package com.foodorder.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(
            @RequestParam(
                    name = "type",
                    defaultValue = "USER"
            ) String type,
            Authentication authentication,
            HttpSession session,
            Model model
    ) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }

        String safeType =
                "OWNER".equals(type) ? "OWNER" : "USER";

        model.addAttribute("type", safeType);

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

        model.addAttribute("signedIn", false);

        Object message =
                session.getAttribute("googleMessage");

        session.removeAttribute("googleMessage");

        model.addAttribute(
                "googleMessage",
                message
        );

        return "login";
    }
}