package com.foodorder.controller;

import com.foodorder.dto.SignupRequest;
import com.foodorder.service.EmailVerificationService;
import com.foodorder.service.SignupService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
public class SignupController {

    private static final String COMPLETION_KEY =
            "foodorder.signup.completion";

    private final SignupService signupService;

    private final EmailVerificationService
            emailVerificationService;

    public SignupController(
            SignupService signupService,
            EmailVerificationService emailVerificationService
    ) {
        this.signupService = signupService;
        this.emailVerificationService =
                emailVerificationService;
    }

    @GetMapping("/join")
    public String selectType(
            @RequestParam(
                    name = "type",
                    defaultValue = "USER"
            ) String type,
            Model model,
            Authentication authentication
    ) {
        if (isSignedIn(authentication)) return "redirect:/";
        addTypeAttributes(
                model,
                normalizeType(type)
        );

        return "join";
    }

    @GetMapping("/join/form")
    public String form(
            @RequestParam(
                    name = "type",
                    defaultValue = "USER"
            ) String type,
            HttpSession session,
            Model model,
            Authentication authentication
    ) {
        if (isSignedIn(authentication)) return "redirect:/";
        String safeType = normalizeType(type);

        addTypeAttributes(model, safeType);

        SignupRequest request = new SignupRequest();
        request.setType(safeType);

        model.addAttribute(
                "signupRequest",
                request
        );

        var verified =
                emailVerificationService.getVerifiedEmail(
                        session
                );

        model.addAttribute(
                "verifiedEmail",
                verified == null ? null : verified.email()
        );

        model.addAttribute(
                "email",
                emailVerificationService.getDisplayEmail(
                        session
                )
        );

        // 일본 시간 기준 오늘 날짜: 개업일의 최댓값
        model.addAttribute(
                "today",
                LocalDate.now(
                        ZoneId.of("Asia/Tokyo")
                )
        );

        return "join-form";
    }

    // 가입 결과만 반환하고 실패 시 페이지를 이동하지 않음
    @PostMapping("/join")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> signup(
            @Valid
            @ModelAttribute("signupRequest")
            SignupRequest request,

            BindingResult bindingResult,

            HttpSession session
    ) {
        if (bindingResult.hasErrors()) {
            String message = bindingResult
                    .getAllErrors()
                    .stream()
                    .map(error -> error.getDefaultMessage())
                    .filter(Objects::nonNull)
                    .sorted()
                    .findFirst()
                    .orElse(
                            "入力内容を確認してください。"
                    );

            return failure(message, session);
        }

        try {
            synchronized (session) {
                var verifiedEmail =
                        emailVerificationService
                                .getVerifiedEmail(session);

                SignupService.SignupResult result =
                        signupService.signup(
                                request,
                                verifiedEmail
                        );

                emailVerificationService.clearAfterSignup(
                        session
                );

                // 비밀번호·입력 폼은 세션에 저장하지 않음
                // 완료 화면에 필요한 결과만 잠시 보관
                session.setAttribute(
                        COMPLETION_KEY,
                        result
                );
            }

            return ResponseEntity.ok()
                    .header("Cache-Control", "no-store")
                    .body(
                            Map.<String, Object>of(
                                    "success", true
                            )
                    );

        } catch (IllegalArgumentException e) {
            return failure(
                    e.getMessage(),
                    session
            );
        }
    }

    @GetMapping("/join/complete")
    public String complete(
            HttpSession session,
            Model model
    ) {
        SignupService.SignupResult result;

        synchronized (session) {
            Object saved =
                    session.getAttribute(COMPLETION_KEY);

            if (!(saved instanceof
                    SignupService.SignupResult completed)) {
                return "redirect:/join";
            }

            result = completed;
            session.removeAttribute(COMPLETION_KEY);
        }

        model.addAttribute("registered", true);
        model.addAttribute("memberType", result.type());
        model.addAttribute(
                "accessNumber",
                result.accessNumber()
        );

        return "join-complete";
    }

    private ResponseEntity<Map<String, Object>> failure(
            String message,
            HttpSession session
    ) {
        boolean emailVerified =
                emailVerificationService
                        .getVerifiedEmail(session) != null;

        return ResponseEntity.badRequest()
                .header("Cache-Control", "no-store")
                .body(
                        Map.<String, Object>of(
                                "success", false,
                                "message",
                                message == null
                                        ? "入力内容を確認してください。"
                                        : message,
                                "emailVerified", emailVerified
                        )
                );
    }

    private void addTypeAttributes(
            Model model,
            String type
    ) {
        model.addAttribute("type", type);

        model.addAttribute(
                "types",
                List.of("USER", "OWNER")
        );

        model.addAttribute(
                "labels",
                Map.of(
                        "USER", "一般会員",
                        "OWNER", "店舗管理者"
                )
        );
    }

    private String normalizeType(String type) {
        return "OWNER".equals(type) ? "OWNER" : "USER";
    }
    private boolean isSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

}