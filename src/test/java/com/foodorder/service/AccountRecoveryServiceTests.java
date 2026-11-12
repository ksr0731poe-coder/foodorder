package com.foodorder.service;

import com.foodorder.dto.LoginMemberRow;
import com.foodorder.mapper.AccountRecoveryMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.List;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AccountRecoveryServiceTests {
    private AccountRecoveryMapper mapper;
    private JavaMailSender mail;
    private EmailVerificationService verification;
    private AccountRecoveryService recovery;
    private MockHttpSession session;
    private ValidatorFactory validation;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @BeforeEach
    void setUp() {
        mapper = mock(AccountRecoveryMapper.class);
        mail = mock(JavaMailSender.class);
        validation = Validation.buildDefaultValidatorFactory();
        verification = new EmailVerificationService(mail, validation.getValidator(), "test@foodorder.example");
        recovery = new AccountRecoveryService(mapper, verification, encoder, validation.getValidator());
        session = new MockHttpSession();
        LoginMemberRow member = new LoginMemberRow();
        member.setMemberId(42L);
        member.setLoginId("food_user");
        when(mapper.findByEmail("user@example.com", "ROLE_USER")).thenReturn(List.of(member));
        when(mapper.updatePassword(eq(42L), eq("user@example.com"), eq("ROLE_USER"), anyString())).thenReturn(1);
    }

    @AfterEach
    void close() { validation.close(); }

    private String lastCode() {
        var capture = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail, atLeastOnce()).send(capture.capture());
        var messages = capture.getAllValues();
        var matcher = Pattern.compile("[0-9]{6}").matcher(messages.getLast().getText());
        assertTrue(matcher.find());
        return matcher.group();
    }

    @Test
    void idLookupReturnsIdImmediatelyWithoutEmailVerification() {
        assertEquals("food_user", recovery.findId(" USER@example.com ", "USER"));
        verifyNoInteractions(mail);
    }

    @Test
    void cannotResetWithoutVerification() {
        recovery.sendCode(session, "user@example.com", "USER");
        assertFalse(recovery.canReset(session));
        assertThrows(IllegalArgumentException.class, () -> recovery.resetPassword(session, "Newpass123", "Newpass123"));
        verify(mapper, never()).updatePassword(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void successfulVerificationAllowsOneResetAndStoresOnlyHash() {
        recovery.sendCode(session, "user@example.com", "USER");
        recovery.verifyCode(session, lastCode());
        assertTrue(recovery.canReset(session));
        assertEquals("USER", recovery.resetPassword(session, "Newpass123", "Newpass123"));
        var hash = ArgumentCaptor.forClass(String.class);
        verify(mapper).updatePassword(eq(42L), eq("user@example.com"), eq("ROLE_USER"), hash.capture());
        assertNotEquals("Newpass123", hash.getValue());
        assertTrue(encoder.matches("Newpass123", hash.getValue()));
        assertFalse(recovery.canReset(session));
        assertThrows(IllegalArgumentException.class, () -> recovery.resetPassword(session, "Newpass456", "Newpass456"));
    }

    @Test
    void signupVerificationCannotAuthorizePasswordReset() {
        recovery.sendCode(session, "user@example.com", "USER");
        verification.sendCode(session, "user@example.com");
        verification.verifyCode(session, lastCode());
        assertNotNull(verification.getVerifiedEmail(session));
        assertFalse(recovery.canReset(session));
    }

    @Test
    void passwordResetVerificationCannotAuthorizeSignupOrAnotherSession() {
        recovery.sendCode(session, "user@example.com", "USER");
        String code = lastCode();
        assertThrows(IllegalArgumentException.class, () -> recovery.verifyCode(new MockHttpSession(), code));
        recovery.verifyCode(session, code);
        assertNull(verification.getVerifiedEmail(session));
        assertThrows(IllegalArgumentException.class, () -> recovery.verifyCode(session, code));
    }

    @Test
    void fiveIncorrectAttemptsInvalidateCode() {
        recovery.sendCode(session, "user@example.com", "USER");
        String code = lastCode();
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(IllegalArgumentException.class, () -> recovery.verifyCode(session, wrong));
        }
        assertThrows(IllegalArgumentException.class, () -> recovery.verifyCode(session, code));
        assertFalse(recovery.canReset(session));
    }

    @Test
    void invalidPasswordDoesNotConsumeVerification() {
        recovery.sendCode(session, "user@example.com", "USER");
        recovery.verifyCode(session, lastCode());
        assertThrows(IllegalArgumentException.class, () -> recovery.resetPassword(session, "short1", "short1"));
        assertThrows(IllegalArgumentException.class, () -> recovery.resetPassword(session, "Newpass123", "Newpass456"));
        assertTrue(recovery.canReset(session));
        verify(mapper, never()).updatePassword(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    void resendRevokesVerifiedResetEvenWhenThrottled() {
        recovery.sendCode(session, "user@example.com", "USER");
        recovery.verifyCode(session, lastCode());
        assertThrows(IllegalArgumentException.class, () -> recovery.sendCode(session, "user@example.com", "USER"));
        assertFalse(recovery.canReset(session));
    }

    @Test
    void ownerRecoveryUsesOwnerRole() {
        assertThrows(IllegalArgumentException.class, () -> recovery.findId("user@example.com", "OWNER"));
        verify(mapper).findByEmail("user@example.com", "ROLE_OWNER");
        verifyNoInteractions(mail);
    }
}
