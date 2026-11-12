package com.foodorder.service;

import com.foodorder.dto.SignupMemberRow;
import com.foodorder.dto.SignupRequest;
import com.foodorder.mapper.SignupMapper;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class SignupService {

    private static final ZoneId JAPAN =
            ZoneId.of("Asia/Tokyo");

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private final SignupMapper signupMapper;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public SignupService(
            SignupMapper signupMapper,
            PasswordEncoder passwordEncoder,
            Validator validator
    ) {
        this.signupMapper = signupMapper;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    /*
     * 이메일 인증 성공 후 서버에서만 생성하는 정보.
     * 회원가입 폼의 입력값으로 직접 만들면 안 됩니다.
     */
    public record VerifiedEmail(
            @NotBlank(message = "メールアドレスを確認してください。")
            @Email(message = "メールアドレスを確認してください。")
            @Size(
                    max = 254,
                    message = "メールアドレスは254文字以内で入力してください。"
            )
            String email,

            @NotNull(message = "メール認証が必要です。")
            Instant verifiedAt,

            @NotNull(message = "メール認証が必要です。")
            Instant expiresAt
    ) {
    }

    /*
     * 가입 성공 후 컨트롤러에 돌려줄 결과.
     * accessNumber는 개인회원이면 NULL입니다.
     */
    public record SignupResult(
            Long memberId,
            String type,
            String accessNumber
    ) {
    }

    @Transactional
    public SignupResult signup(
            SignupRequest request,
            VerifiedEmail verifiedEmail
    ) {
        // 컨트롤러 이외의 경로로 호출되어도 검증 수행
        validate(request);
        validateEmailVerification(verifiedEmail);

        String type = request.getType();

        // 화면을 우회해 요청해도 관리자 가입은 허용하지 않음
        if (!"USER".equals(type) && !"OWNER".equals(type)) {
            throw new IllegalArgumentException(
                    "正しい会員種別を選択してください。"
            );
        }

        String loginId = request.getLoginId();
        String nickname = request.getNickname().strip();

        if (nickname.isBlank()) {
            throw new IllegalArgumentException(
                    "ニックネームを入力してください。"
            );
        }

        // 인증 시에도 같은 정규화 규칙을 사용해야 함
        String email = verifiedEmail.email()
                .strip()
                .toLowerCase(Locale.ROOT);

        validatePassword(request);

        // 중복 확인
        if (signupMapper.countByLoginId(loginId) > 0) {
            throw new IllegalArgumentException(
                    "このログインIDは既に使用されています。"
            );
        }

        if (signupMapper.countByNickname(nickname) > 0) {
            throw new IllegalArgumentException(
                    "このニックネームは既に使用されています。"
            );
        }

        if (signupMapper.countByEmail(email) > 0) {
            throw new IllegalArgumentException(
                    "このメールアドレスは既に登録されています。"
            );
        }

        String role;
        String accessNumber = null;
        String businessNumber = null;
        String restaurantName = null;
        String companyNameJa = null;
        String companyNameEn = null;
        String representative = null;
        LocalDate openedOn = null;

        switch (type) {
            case "USER" -> {
                role = "ROLE_USER";
            }

            case "OWNER" -> {
                role = "ROLE_OWNER";

                businessNumber = required(
                        request.getBusinessNumber(),
                        "事業者登録番号を入力してください。"
                );

                restaurantName = required(
                        request.getRestaurantName(),
                        "店舗名を入力してください。"
                );

                companyNameJa = required(
                        request.getCompanyNameJa(),
                        "会社名（日本語）を入力してください。"
                );

                companyNameEn = required(
                        request.getCompanyNameEn(),
                        "会社名（英語）を入力してください。"
                );

                representative = required(
                        request.getRepresentative(),
                        "代表者名を入力してください。"
                );

                openedOn = parseOpenedOn(
                        request.getOpenedOn()
                );

                if (signupMapper.countByBusinessNumber(
                        businessNumber
                ) > 0) {
                    throw new IllegalArgumentException(
                            "この事業者登録番号は既に登録されています。"
                    );
                }

                accessNumber = generateAccessNumber();
            }

            default -> throw new IllegalArgumentException(
                    "正しい会員種別を選択してください。"
            );
        }

        // 平문 비밀번호는 DB 저장 객체에 넣지 않음
        SignupMemberRow member = new SignupMemberRow();
        member.setLoginId(loginId);
        member.setNickname(nickname);
        member.setEmail(email);
        member.setRole(role);
        member.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        try {
            int inserted = signupMapper.insertMember(member);

            if (inserted != 1 || member.getMemberId() == null) {
                throw new IllegalStateException(
                        "会員登録に失敗しました。"
                );
            }

            LocalDateTime emailVerifiedAt =
                    LocalDateTime.ofInstant(
                            verifiedEmail.verifiedAt(),
                            JAPAN
                    );

            int profileInserted = signupMapper.insertProfile(
                    member.getMemberId(),
                    accessNumber,
                    businessNumber,
                    restaurantName,
                    companyNameJa,
                    companyNameEn,
                    representative,
                    openedOn,
                    emailVerifiedAt
            );

            if (profileInserted != 1) {
                throw new IllegalStateException(
                        "会員情報の登録に失敗しました。"
                );
            }

        } catch (DuplicateKeyException e) {
            /*
             * 중복 조회 후 다른 요청이 먼저 저장했거나
             * 발급 번호가 충돌한 경우도 DB에서 차단.
             * 런타임 예외를 다시 던져 전체 가입을 롤백함.
             */
            throw new IllegalArgumentException(
                    "入力された情報が既に登録されているか、"
                            + "発行番号が重複しました。"
                            + "入力内容を確認して、もう一度お試しください。",
                    e
            );
        }

        return new SignupResult(
                member.getMemberId(),
                type,
                accessNumber
        );
    }

    // DTO에 선언한 검증 규칙 실행
    private void validate(Object value) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "入力内容を確認してください。"
            );
        }

        var violations = validator.validate(value);

        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(v -> v.getMessage())
                    .sorted()
                    .findFirst()
                    .orElse("入力内容を確認してください。");

            throw new IllegalArgumentException(message);
        }
    }

    // 이메일 인증 상태와 만료 확인
    private void validateEmailVerification(
            VerifiedEmail verifiedEmail
    ) {
        if (verifiedEmail == null) {
            throw new IllegalArgumentException(
                    "メール認証を完了してください。"
            );
        }

        validate(verifiedEmail);

        Instant now = Instant.now();

        if (verifiedEmail.verifiedAt().isAfter(now)
                || !verifiedEmail.expiresAt()
                .isAfter(verifiedEmail.verifiedAt())
                || !verifiedEmail.expiresAt().isAfter(now)) {

            throw new IllegalArgumentException(
                    "メール認証の有効期限が切れています。"
                            + "もう一度認証してください。"
            );
        }
    }

    private void validatePassword(SignupRequest request) {
        String password = request.getPassword();

        if (!password.equals(request.getPasswordConfirm())) {
            throw new IllegalArgumentException(
                    "確認用パスワードが一致しません。"
            );
        }

        boolean hasLetter =
                password.matches("(?s).*[A-Za-z].*");

        boolean hasNumber =
                password.matches("(?s).*[0-9].*");

        if (!hasLetter || !hasNumber) {
            throw new IllegalArgumentException(
                    "パスワードには半角英字と数字を含めてください。"
            );
        }

        // BCrypt의 입력 바이트 제한 확인
        int byteLength = password
                .getBytes(StandardCharsets.UTF_8)
                .length;

        if (byteLength > 72) {
            throw new IllegalArgumentException(
                    "パスワードはUTF-8で72バイト以内にしてください。"
            );
        }
    }

    private String required(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.strip();
    }

    private LocalDate parseOpenedOn(String value) {
        String text = required(
                value,
                "開業日を入力してください。"
        );

        LocalDate date;

        try {
            date = LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "開業日を正しい日付で入力してください。"
            );
        }

        if (date.isAfter(LocalDate.now(JAPAN))) {
            throw new IllegalArgumentException(
                    "開業日には今日以前の日付を入力してください。"
            );
        }

        return date;
    }

    // 선행 0을 보존하는 12자리 로그인용 번호
    private String generateAccessNumber() {
        StringBuilder number = new StringBuilder(12);

        for (int i = 0; i < 12; i++) {
            number.append(RANDOM.nextInt(10));
        }

        return number.toString();
    }

    // DB에 저장된 관리자 초대번호 해시와 비교할 값 생성
    private String sha256(String value) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "登録処理を実行できませんでした。",
                    e
            );
        }
    }
}