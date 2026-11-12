package com.foodorder.service;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

@Service
public class EmailVerificationService {

    private static final String SESSION_KEY =
            EmailVerificationService.class.getName() + ".STATE";

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private final JavaMailSender mailSender;
    private final Validator validator;
    private final String from;

    public EmailVerificationService(
            JavaMailSender mailSender,
            Validator validator,
            @Value("${foodorder.mail-from:}") String from
    ) {
        this.mailSender = mailSender;
        this.validator = validator;
        this.from = from;
    }

    public enum Purpose { SIGNUP, PASSWORD_RESET }

    // 사용자 요청으로 직접 생성하지 않고 서버 세션에서만 사용
    private static class VerificationState {

        private Instant lastRequestedAt;

        private String pendingEmail;
        private byte[] codeHash;
        private Instant codeExpiresAt;
        private int failedAttempts;

        private SignupService.VerifiedEmail verifiedEmail;
    }

    // 이메일 형식 검증용
    private record EmailInput(
            @NotBlank(
                    message = "メールアドレスを入力してください。"
            )
            @Email(
                    message = "正しいメールアドレスを入力してください。"
            )
            @Size(
                    max = 254,
                    message = "メールアドレスは254文字以内で入力してください。"
            )
            String email
    ) {
    }

    public void sendCode(HttpSession session, String inputEmail) {
        sendCode(session, inputEmail, Purpose.SIGNUP);
    }

    public void sendCode(HttpSession session, String inputEmail, Purpose purpose) {
        String email = inputEmail == null
                ? ""
                : inputEmail.strip().toLowerCase(Locale.ROOT);

        var violations = validator.validate(
                new EmailInput(email)
        );

        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getMessage())
                    .sorted()
                    .findFirst()
                    .orElse(
                            "メールアドレスを確認してください。"
                    );

            throw new IllegalArgumentException(message);
        }

        if (from.isBlank()) {
            throw new IllegalArgumentException(
                    "現在、認証メールを送信できません。"
                            + "しばらくしてからお試しください。"
            );
        }

        synchronized (session) {
            VerificationState state = state(session, purpose);
            Instant now = Instant.now();

            if (state.lastRequestedAt != null
                    && now.isBefore(
                    state.lastRequestedAt.plusSeconds(60)
            )) {
                throw new IllegalArgumentException(
                        "認証コードの再送信は60秒後にお試しください。"
                );
            }

            // 発送失敗時にも連続要求を制限
            state.lastRequestedAt = now;

            // 새 발송 요청이 들어오면 이전 코드와 인증 완료 상태 폐기
            state.pendingEmail = null;
            state.codeHash = null;
            state.codeExpiresAt = null;
            state.failedAttempts = 0;
            state.verifiedEmail = null;

            String code = generateCode();

            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(email);
            mail.setSubject("【Food Order】メール認証コード");
            mail.setText(
                    "Food Orderをご利用いただきありがとうございます。\n\n"
                            + "認証コード：" + code + "\n\n"
                            + "5分以内に入力してください。\n"
                            + "このメールに心当たりがない場合は、"
                            + "破棄してください。"
            );

            // 실패 시 MailException이 발생하며 유효한 코드는 남기지 않음
            mailSender.send(mail);

            state.pendingEmail = email;
            state.codeHash = hash(code);
            state.codeExpiresAt =
                    Instant.now().plusSeconds(300);
        }
    }

    public void verifyCode(HttpSession session, String inputCode) {
        verifyCode(session, inputCode, Purpose.SIGNUP);
    }

    public void verifyCode(HttpSession session, String inputCode, Purpose purpose) {
        synchronized (session) {
            VerificationState state = state(session, purpose);
            Instant now = Instant.now();

            if (state.codeHash == null
                    || state.codeExpiresAt == null) {
                throw new IllegalArgumentException(
                        "認証コードを送信してから入力してください。"
                );
            }

            if (!state.codeExpiresAt.isAfter(now)) {
                clearPendingCode(state);

                throw new IllegalArgumentException(
                        "認証コードの有効期限が切れています。"
                                + "もう一度送信してください。"
                );
            }

            if (state.failedAttempts >= 5) {
                clearPendingCode(state);

                throw new IllegalArgumentException(
                        "入力回数の上限に達しました。"
                                + "認証コードを再送信してください。"
                );
            }

            String code = inputCode == null
                    ? ""
                    : inputCode.strip();

            boolean matches =
                    code.matches("[0-9]{6}")
                            && MessageDigest.isEqual(
                            state.codeHash,
                            hash(code)
                    );

            if (!matches) {
                state.failedAttempts++;

                if (state.failedAttempts >= 5) {
                    clearPendingCode(state);

                    throw new IllegalArgumentException(
                            "入力回数の上限に達しました。"
                                    + "認証コードを再送信してください。"
                    );
                }

                throw new IllegalArgumentException(
                        "認証コードが一致しません。"
                );
            }

            // 인증 완료 후 가입 가능한 시간은 30분
            state.verifiedEmail =
                    new SignupService.VerifiedEmail(
                            state.pendingEmail,
                            now,
                            now.plusSeconds(1800)
                    );

            // 성공한 코드는 재사용할 수 없도록 폐기
            clearPendingCode(state);
        }
    }

    public SignupService.VerifiedEmail getVerifiedEmail(HttpSession session) {
        return getVerifiedEmail(session, Purpose.SIGNUP);
    }

    public SignupService.VerifiedEmail getVerifiedEmail(HttpSession session, Purpose purpose) {
        synchronized (session) {
            VerificationState state = state(session, purpose);

            if (state.verifiedEmail == null) {
                return null;
            }

            if (!state.verifiedEmail.expiresAt()
                    .isAfter(Instant.now())) {

                state.verifiedEmail = null;
                return null;
            }

            return state.verifiedEmail;
        }
    }

    // 가입 화면에서 이메일 입력값 표시용
    public String getDisplayEmail(HttpSession session) {
        return getDisplayEmail(session, Purpose.SIGNUP);
    }

    public String getDisplayEmail(HttpSession session, Purpose purpose) {
        synchronized (session) {
            var verified = getVerifiedEmail(session, purpose);

            if (verified != null) {
                return verified.email();
            }

            return state(session, purpose).pendingEmail;
        }
    }

    // 가입 성공 후 인증 정보 폐기
    // 재발송 제한 시각은 유지
    public void clearAfterSignup(HttpSession session) {
        clearAfterSignup(session, Purpose.SIGNUP);
    }

    public void clearAfterSignup(HttpSession session, Purpose purpose) {
        synchronized (session) {
            VerificationState state = state(session, purpose);
            state.verifiedEmail = null;
            clearPendingCode(state);
        }
    }

    private VerificationState state(HttpSession session, Purpose purpose) {
        Object value = session.getAttribute(SESSION_KEY + purpose.name());

        if (value instanceof VerificationState existing) {
            return existing;
        }

        VerificationState created =
                new VerificationState();

        session.setAttribute(SESSION_KEY + purpose.name(), created);
        return created;
    }

    private void clearPendingCode(
            VerificationState state
    ) {
        state.pendingEmail = null;
        state.codeHash = null;
        state.codeExpiresAt = null;
        state.failedAttempts = 0;
    }

    private String generateCode() {
        return String.format(
                Locale.ROOT,
                "%06d",
                RANDOM.nextInt(1_000_000)
        );
    }

    private byte[] hash(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(
                            value.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "認証処理を実行できませんでした。",
                    e
            );
        }
    }
}