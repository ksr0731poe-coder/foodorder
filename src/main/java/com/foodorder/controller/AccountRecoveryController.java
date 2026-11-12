package com.foodorder.controller;

import com.foodorder.service.AccountRecoveryService;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataAccessException;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AccountRecoveryController {
    private final AccountRecoveryService recovery;

    public AccountRecoveryController(AccountRecoveryService recovery) { this.recovery = recovery; }

    @GetMapping("/account/recovery")
    public String page(@RequestParam(defaultValue = "id") String mode,
                       @RequestParam(defaultValue = "USER") String type, HttpSession session, Model model) {
        model.addAttribute("mode", "password".equals(mode) ? "password" : "id");
        model.addAttribute("type", recovery.normalizeType(type));
        model.addAttribute("codeSent", recovery.codeSent(session));
        model.addAttribute("canReset", recovery.canReset(session));
        return "account-recovery";
    }

    @PostMapping("/account/recovery/id")
    public String findId(@RequestParam String email, @RequestParam(defaultValue = "USER") String type,
                         RedirectAttributes flash) {
        try { flash.addFlashAttribute("foundId", recovery.findId(email, type)); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        catch (DataAccessException e) { flash.addFlashAttribute("error", "現在検索できません。しばらくしてからお試しください。"); }
        return redirect("id", type, flash);
    }

    @PostMapping("/account/recovery/send")
    public String send(@RequestParam String email, @RequestParam(defaultValue = "USER") String type,
                       HttpSession session, RedirectAttributes flash) {
        try {
            recovery.sendCode(session, email, type);
            flash.addFlashAttribute("message", "認証コードを送信しました。5分以内に入力してください。");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        catch (MailException | DataAccessException e) { flash.addFlashAttribute("error", "認証メールを送信できませんでした。しばらくしてからお試しください。"); }
        return redirect("password", type, flash);
    }

    @PostMapping("/account/recovery/verify")
    public String verify(@RequestParam String code, @RequestParam(defaultValue = "USER") String type,
                         HttpSession session, RedirectAttributes flash) {
        try { recovery.verifyCode(session, code); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return redirect("password", type, flash);
    }

    @PostMapping("/account/recovery/reset")
    public String reset(@RequestParam String password, @RequestParam String passwordConfirm,
                        @RequestParam(defaultValue = "USER") String type,
                        HttpSession session, RedirectAttributes flash) {
        try {
            String targetType = recovery.resetPassword(session, password, passwordConfirm);
            return "redirect:/login?reset&type=" + targetType;
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("error", e.getMessage()); }
        catch (DataAccessException e) { flash.addFlashAttribute("error", "パスワードを変更できませんでした。もう一度お試しください。"); }
        return redirect("password", type, flash);
    }

    private String redirect(String mode, String type, RedirectAttributes flash) {
        flash.addAttribute("mode", mode);
        flash.addAttribute("type", recovery.normalizeType(type));
        return "redirect:/account/recovery";
    }
}
