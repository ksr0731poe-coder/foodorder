package com.foodorder.config;

import com.foodorder.dto.LoginMemberRow;
import com.foodorder.mapper.LoginMapper;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

@Component
public class LoginAuthenticationProvider
        implements AuthenticationProvider {

    private static final Set<String> MEMBER_TYPES =
            Set.of("USER", "OWNER");

    private final LoginMapper loginMapper;
    private final PasswordEncoder passwordEncoder;

    // 존재하지 않는 회원도 비밀번호 해시 비교를 수행하기 위한 값
    private final String dummyPasswordHash;

    public LoginAuthenticationProvider(
            LoginMapper loginMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.loginMapper = loginMapper;
        this.passwordEncoder = passwordEncoder;

        this.dummyPasswordHash = passwordEncoder.encode(
                "FoodOrder-Dummy-Password-For-Timing"
        );
    }

    // Spring Security가 요청에서 읽어 전달할 추가 로그인 정보
    public record LoginDetails(
            String memberType,
            String accessNumber
    ) implements Serializable {
    }

    @Override
    public Authentication authenticate(
            Authentication authentication
    ) throws AuthenticationException {

        if (!(authentication.getDetails()
                instanceof LoginDetails details)) {
            throw invalidCredentials();
        }

        String memberType = clean(details.memberType());
        String accessNumber = clean(details.accessNumber());
        String identity = clean(authentication.getName());

        Object credentials = authentication.getCredentials();

        // 비밀번호 앞뒤 공백은 제거하지 않음
        String password = credentials == null
                ? ""
                : credentials.toString();

        if (!MEMBER_TYPES.contains(memberType)) {
            throw invalidCredentials();
        }

        if (identity.isBlank() || identity.length() > 254) {
            throw invalidCredentials();
        }

        if (password.isEmpty()
                || password.length() > 64
                || password.getBytes(
                StandardCharsets.UTF_8
        ).length > 72) {
            throw invalidCredentials();
        }

        // 가게관리자·관리자는 12자리 로그인 번호 필수
        if (!"USER".equals(memberType)
                && !accessNumber.matches("[0-9]{12}")) {
            throw invalidCredentials();
        }

        List<LoginMemberRow> candidates;

        try {
            candidates = loginMapper.findLoginCandidates(
                    identity,
                    memberType
            );
        } catch (DataAccessException e) {
            throw new InternalAuthenticationServiceException(
                    "現在ログインできません。",
                    e
            );
        }

        // 조회 결과가 정확히 한 명이어야 로그인 가능
        LoginMemberRow member = candidates.size() == 1
                ? candidates.getFirst()
                : null;

        String storedHash =
                member != null && member.getPasswordHash() != null
                        ? member.getPasswordHash()
                        : dummyPasswordHash;

        boolean passwordMatches =
                passwordEncoder.matches(
                        password,
                        storedHash
                );

        if (member == null || !passwordMatches) {
            throw invalidCredentials();
        }

        // 선택한 탭을 권한으로 사용하지 않고 DB 권한과 비교
        String expectedRole = "ROLE_" + memberType;

        if (!expectedRole.equals(member.getRole())) {
            throw invalidCredentials();
        }

        // 이용 중지·탈퇴 계정 로그인 차단
        if (!"ACTIVE".equals(member.getStatus())) {
            throw invalidCredentials();
        }

        if (!"USER".equals(memberType)
                && !accessNumber.equals(
                member.getAccessNumber()
        )) {
            throw invalidCredentials();
        }

        // 이메일로 로그인해도 인증된 사용자 이름은 login_id로 통일
        // 인증 결과에는 비밀번호 해시를 보관하지 않음
        var principal = User
                .withUsername(member.getLoginId())
                .password("")
                .authorities(member.getRole())
                .build();

        return UsernamePasswordAuthenticationToken
                .authenticated(
                        principal,
                        null,
                        principal.getAuthorities()
                );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class
                .isAssignableFrom(authentication);
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private BadCredentialsException invalidCredentials() {
        // 어떤 항목이 틀렸는지 개별적으로 노출하지 않음
        return new BadCredentialsException(
                "ログイン情報を確認してください。"
        );
    }
}