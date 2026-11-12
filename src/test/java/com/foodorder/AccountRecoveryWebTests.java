package com.foodorder;

import com.foodorder.service.AccountRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountRecoveryWebTests {
    @Autowired MockMvc mvc;
    @MockitoBean AccountRecoveryService recovery;

    @BeforeEach
    void setUp() {
        when(recovery.normalizeType(anyString())).thenAnswer(call -> "OWNER".equals(call.getArgument(0)) ? "OWNER" : "USER");
    }

    @Test
    void anonymousHomeShowsLoginAndRegistration() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(containsString("href=\"/join\"")))
                .andExpect(content().string(not(containsString("action=\"/logout\""))));
    }

    @Test
    void signedInHomeShowsOnlyAuthenticatedAccountActions() throws Exception {
        mvc.perform(get("/").with(user("food_user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("action=\"/account/social/link/google\"")))
                .andExpect(content().string(not(containsString("href=\"/login\""))))
                .andExpect(content().string(not(containsString("href=\"/join\""))));
    }

    @Test
    void signedInUsersAreRedirectedAwayFromLoginAndSignup() throws Exception {
        for (String path : new String[]{"/login", "/join", "/join/form"}) {
            mvc.perform(get(path).with(user("food_user").roles("USER")))
                    .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/"));
        }
    }

    @Test
    void recoveryAndLoginPagesRenderForAnonymousUsers() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("/account/recovery?mode=id")));
        mvc.perform(get("/account/recovery"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/account/recovery/id\"")))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test
    void verifiedEmailImmediatelyShowsResetFormWithoutEmailOrCodeForms() throws Exception {
        when(recovery.canReset(any())).thenReturn(true);
        mvc.perform(get("/account/recovery").param("mode", "password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/account/recovery/reset\"")))
                .andExpect(content().string(containsString("autocomplete=\"new-password\"")))
                .andExpect(content().string(not(containsString("action=\"/account/recovery/send\""))))
                .andExpect(content().string(not(containsString("action=\"/account/recovery/verify\""))));
    }

    @Test
    void unverifiedEmailShowsCodeFormWithoutPasswordFields() throws Exception {
        when(recovery.codeSent(any())).thenReturn(true);
        mvc.perform(get("/account/recovery").param("mode", "password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/account/recovery/verify\"")))
                .andExpect(content().string(not(containsString("name=\"password\""))));
    }

    @Test
    void idLookupWorksAnonymouslyWithCsrfAndDisplaysId() throws Exception {
        when(recovery.findId("user@example.com", "USER")).thenReturn("food_user");
        var result = mvc.perform(post("/account/recovery/id").with(csrf()).param("email", "user@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("foundId", "food_user")).andReturn();
        mvc.perform(get("/account/recovery").flashAttrs(result.getFlashMap()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("food_user")));
    }

    @Test
    void recoveryPostsRequireCsrf() throws Exception {
        mvc.perform(post("/account/recovery/reset").param("password", "Newpass123").param("passwordConfirm", "Newpass123"))
                .andExpect(status().isForbidden());
        verify(recovery, never()).resetPassword(any(), any(), any());
    }
}
