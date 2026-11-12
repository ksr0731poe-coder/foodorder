package com.foodorder.security;

import com.foodorder.service.GoogleAccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GoogleOidcUserService {

    private final GoogleAccountService accounts;

    private final OidcUserService delegate =
            new OidcUserService();

    public GoogleOidcUserService(
            GoogleAccountService accounts
    ) {
        this.accounts = accounts;
    }

    public OidcUser loadUser(OidcUserRequest request) {
        if (!"google".equals(
                request.getClientRegistration()
                        .getRegistrationId()
        )) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("google_invalid")
            );
        }

        // Google 사용자 정보 로딩은 기본 OIDC 서비스 사용
        OidcUser remote = delegate.loadUser(request);

        HttpServletRequest servletRequest =
                ((ServletRequestAttributes)
                        RequestContextHolder.currentRequestAttributes())
                        .getRequest();

        String localLoginId = accounts.resolve(
                remote.getSubject(),
                servletRequest
        );

        Map<String, Object> claims = new HashMap<>();

        if (remote.getUserInfo() != null) {
            claims.putAll(
                    remote.getUserInfo().getClaims()
            );
        }

        claims.put(
                "local_login_id",
                localLoginId
        );

        // Google 로그인 후에도 사용자 이름은 Food Order ID로 통일
        return new DefaultOidcUser(
                List.of(
                        new SimpleGrantedAuthority("ROLE_USER")
                ),
                remote.getIdToken(),
                new OidcUserInfo(claims),
                "local_login_id"
        );
    }
}