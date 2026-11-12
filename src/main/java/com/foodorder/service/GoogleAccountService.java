package com.foodorder.service;

import com.foodorder.dto.SocialMemberRow;
import com.foodorder.mapper.SocialAccountMapper;
import com.foodorder.security.GoogleAuthorizationRequestRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class GoogleAccountService {

    private final SocialAccountMapper mapper;

    public GoogleAccountService(
            SocialAccountMapper mapper
    ) {
        this.mapper = mapper;
    }

    @Transactional
    public String resolve(
            String googleSubject,
            HttpServletRequest request
    ) {
        if (googleSubject == null
                || googleSubject.isBlank()
                || googleSubject.length() > 255) {
            throw failure("google_invalid");
        }

        try {
            SocialMemberRow linked =
                    mapper.findGoogleMember(googleSubject);

            Object value = request.getAttribute(
                    GoogleAuthorizationRequestRepository.LINK_INTENT
            );

            // 로그인 상태에서 시작한 계정 연결
            if (value instanceof
                    GoogleAuthorizationRequestRepository.LinkIntent
                            intent) {

                if (!intent.expiresAt().isAfter(Instant.now())) {
                    throw failure("google_link_expired");
                }

                Authentication current =
                        SecurityContextHolder.getContext()
                                .getAuthentication();

                boolean sameMember =
                        current != null
                                && current.isAuthenticated()
                                && intent.loginId().equals(
                                current.getName()
                        )
                                && current.getAuthorities().stream()
                                .anyMatch(authority ->
                                        "ROLE_USER".equals(
                                                authority.getAuthority()
                                        )
                                );

                if (!sameMember) {
                    throw failure("google_link_expired");
                }

                SocialMemberRow target =
                        mapper.findMember(intent.loginId());

                requireActiveUser(target);

                if (linked != null) {
                    if (!target.getMemberId().equals(
                            linked.getMemberId()
                    )) {
                        throw failure("google_conflict");
                    }

                    // 이미 같은 회원에게 연결된 경우
                    requireActiveUser(linked);
                    return linked.getLoginId();
                }

                mapper.insertGoogleLink(
                        target.getMemberId(),
                        googleSubject
                );

                return target.getLoginId();
            }

            // 일반 Google 로그인
            if (linked == null) {
                throw failure("google_not_linked");
            }

            requireActiveUser(linked);
            return linked.getLoginId();

        } catch (DuplicateKeyException e) {
            throw failure("google_conflict");

        } catch (DataAccessException e) {
            throw failure("google_unavailable");
        }
    }

    private void requireActiveUser(
            SocialMemberRow member
    ) {
        if (member == null
                || !"ACTIVE".equals(member.getStatus())
                || !"ROLE_USER".equals(member.getRole())) {
            throw failure("google_account_unavailable");
        }
    }

    private OAuth2AuthenticationException failure(
            String code
    ) {
        return new OAuth2AuthenticationException(
                new OAuth2Error(code)
        );
    }
}