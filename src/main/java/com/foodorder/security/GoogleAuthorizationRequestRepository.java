package com.foodorder.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.time.Instant;

@Component
public class GoogleAuthorizationRequestRepository
        implements AuthorizationRequestRepository
        <OAuth2AuthorizationRequest> {

    public static final String PENDING_LINK =
            "foodorder.google.pendingLink";

    public static final String LINK_INTENT =
            "foodorder.google.linkIntent";

    public record LinkIntent(
            String loginId,
            Instant expiresAt
    ) implements Serializable {
    }

    private final HttpSessionOAuth2AuthorizationRequestRepository
            delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(
            HttpServletRequest request
    ) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (authorizationRequest != null) {
            var session = request.getSession(false);

            if (session != null) {
                Object pending =
                        session.getAttribute(PENDING_LINK);

                session.removeAttribute(PENDING_LINK);

                if (pending instanceof LinkIntent intent
                        && intent.expiresAt()
                        .isAfter(Instant.now())
                        && "google".equals(
                        authorizationRequest.getAttribute(
                                "registration_id"
                        )
                )) {

                    // 연결 정보는 서버에 저장되는 OAuth 요청에만 추가
                    authorizationRequest =
                            OAuth2AuthorizationRequest
                                    .from(authorizationRequest)
                                    .attributes(attributes ->
                                            attributes.put(
                                                    LINK_INTENT,
                                                    intent
                                            )
                                    )
                                    .build();
                }
            }
        }

        delegate.saveAuthorizationRequest(
                authorizationRequest,
                request,
                response
        );
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        OAuth2AuthorizationRequest saved =
                delegate.removeAuthorizationRequest(
                        request,
                        response
                );

        if (saved != null) {
            Object intent = saved.getAttribute(LINK_INTENT);

            if (intent instanceof LinkIntent) {
                request.setAttribute(
                        LINK_INTENT,
                        intent
                );
            }
        }

        return saved;
    }
}