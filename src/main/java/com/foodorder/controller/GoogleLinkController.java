package com.foodorder.controller;

import com.foodorder.security.GoogleAuthorizationRequestRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Controller
public class GoogleLinkController {

    @PostMapping("/account/social/link/google")
    public String link(
            Authentication authentication,
            HttpSession session
    ) {
        boolean allowed =
                authentication != null
                        && authentication.isAuthenticated()
                        && authentication.getAuthorities().stream()
                        .anyMatch(authority ->
                                "ROLE_USER".equals(
                                        authority.getAuthority()
                                )
                        );

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN
            );
        }

        // 연결 대상은 사용자 입력이 아닌 현재 로그인 회원
        session.setAttribute(
                GoogleAuthorizationRequestRepository.PENDING_LINK,
                new GoogleAuthorizationRequestRepository.LinkIntent(
                        authentication.getName(),
                        Instant.now().plusSeconds(300)
                )
        );

        return "redirect:/oauth2/authorization/google";
    }
}