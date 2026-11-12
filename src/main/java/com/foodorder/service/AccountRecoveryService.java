package com.foodorder.service;

import com.foodorder.dto.LoginMemberRow;
import com.foodorder.mapper.AccountRecoveryMapper;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import static com.foodorder.service.EmailVerificationService.Purpose.PASSWORD_RESET;

@Service
public class AccountRecoveryService {
    private static final String TARGET = AccountRecoveryService.class.getName() + ".TARGET";
    private final AccountRecoveryMapper mapper;
    private final EmailVerificationService verification;
    private final PasswordEncoder encoder;
    private final Validator validator;

    private record EmailInput(@NotBlank @Email @Size(max = 254) String email) {}
    private record ResetTarget(Long memberId, String email, String type) {}

    public AccountRecoveryService(AccountRecoveryMapper mapper, EmailVerificationService verification,
                                  PasswordEncoder encoder, Validator validator) {
        this.mapper = mapper;
        this.verification = verification;
        this.encoder = encoder;
        this.validator = validator;
    }

    public String normalizeType(String type) {
        return "OWNER".equals(type) ? "OWNER" : "USER";
    }

    private String email(String input) {
        String email = input == null ? "" : input.strip().toLowerCase(Locale.ROOT);
        if (!validator.validate(new EmailInput(email)).isEmpty()) {
            throw new IllegalArgumentException("正しいメールアドレスを入力してください。");
        }
        return email;
    }

    private LoginMemberRow member(String email, String type) {
        var members = mapper.findByEmail(email, "ROLE_" + normalizeType(type));
        if (members.size() != 1) {
            throw new IllegalArgumentException("選択した会員種別に、このメールアドレスの登録が見つかりません。");
        }
        return members.getFirst();
    }

    public String findId(String input, String type) {
        return member(email(input), type).getLoginId();
    }

    public void sendCode(HttpSession session, String input, String type) {
        synchronized (session) {
            session.removeAttribute(TARGET);
            verification.clearAfterSignup(session, PASSWORD_RESET);
            String email = email(input);
            String safeType = normalizeType(type);
            var member = member(email, safeType);
            verification.sendCode(session, email, PASSWORD_RESET);
            session.setAttribute(TARGET, new ResetTarget(member.getMemberId(), email, safeType));
        }
    }

    public boolean codeSent(HttpSession session) {
        return session.getAttribute(TARGET) instanceof ResetTarget;
    }

    public void verifyCode(HttpSession session, String code) {
        synchronized (session) {
            if (!codeSent(session)) {
                throw new IllegalArgumentException("認証コードを送信してください。");
            }
            verification.verifyCode(session, code, PASSWORD_RESET);
        }
    }

    public boolean canReset(HttpSession session) {
        synchronized (session) {
            var verified = verification.getVerifiedEmail(session, PASSWORD_RESET);
            return session.getAttribute(TARGET) instanceof ResetTarget target
                    && verified != null && target.email().equals(verified.email());
        }
    }

    public String resetPassword(HttpSession session, String password, String confirm) {
        synchronized (session) {
            if (!canReset(session)) {
                throw new IllegalArgumentException("メール認証を完了してください。期限切れの場合は再送信してください。");
            }
            if (password == null || password.length() < 8 || password.length() > 64
                    || password.getBytes(StandardCharsets.UTF_8).length > 72
                    || !password.matches("(?s).*[A-Za-z].*") || !password.matches("(?s).*[0-9].*")) {
                throw new IllegalArgumentException("パスワードは英字と数字を含む8〜64文字、UTF-8で72バイト以内にしてください。");
            }
            if (!password.equals(confirm)) {
                throw new IllegalArgumentException("確認用パスワードが一致しません。");
            }
            ResetTarget target = (ResetTarget) session.getAttribute(TARGET);
            if (mapper.updatePassword(target.memberId(), target.email(), "ROLE_" + target.type(), encoder.encode(password)) != 1) {
                throw new IllegalArgumentException("アカウントの状態が変更されました。もう一度お試しください。");
            }
            verification.clearAfterSignup(session, PASSWORD_RESET);
            session.removeAttribute(TARGET);
            return target.type();
        }
    }
}
