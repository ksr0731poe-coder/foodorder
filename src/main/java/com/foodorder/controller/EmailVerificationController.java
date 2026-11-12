package com.foodorder.controller;

import com.foodorder.service.EmailVerificationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class EmailVerificationController {

    private static final Logger log =
            LoggerFactory.getLogger(
                    EmailVerificationController.class
            );

    // 아래 기존 코드는 유지



    private final EmailVerificationService
            emailVerificationService;

    public EmailVerificationController(
            EmailVerificationService emailVerificationService
    ) {
        this.emailVerificationService =
                emailVerificationService;
    }

    @PostMapping("/join/email/send")
    public String send(
            @RequestParam(
                    name = "email",
                    required = false
            ) String email,

            @RequestParam(
                    name = "type",
                    defaultValue = "USER"
            ) String type,

            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            emailVerificationService.sendCode(
                    session,
                    email
            );

            redirectAttributes.addFlashAttribute(
                    "message",
                    "認証コードを送信しました。"
                            + "5分以内に入力してください。"
            );

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "message",
                    e.getMessage()
            );

        } catch (MailException e) {
            // 오류 원인은 서버 콘솔에만 기록
            Throwable cause = e.getMostSpecificCause();

            log.warn("SMTP 메일 발송 실패", e);

            // 화면에는 일본어 안내만 표시
            redirectAttributes.addFlashAttribute(
                    "message",
                    "認証メールを送信できませんでした。"
                            + "しばらくしてからお試しください。"
            );
        }

        return redirectToForm(type);
    }

    @PostMapping("/join/email/verify")
    public String verify(
            @RequestParam(
                    name = "code",
                    required = false
            ) String code,

            @RequestParam(
                    name = "type",
                    defaultValue = "USER"
            ) String type,

            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            emailVerificationService.verifyCode(
                    session,
                    code
            );

            redirectAttributes.addFlashAttribute(
                    "message",
                    "メール認証が完了しました。"
                            + "30分以内に会員登録を完了してください。"
            );

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute(
                    "message",
                    e.getMessage()
            );
        }

        return redirectToForm(type);
    }

    private String redirectToForm(String type) {
        String safeType =
                "OWNER".equals(type) ? "OWNER" : "USER";

        return "redirect:/join/form?type=" + safeType;
    }
}