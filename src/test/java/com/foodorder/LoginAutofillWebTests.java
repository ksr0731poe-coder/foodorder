package com.foodorder;

import com.foodorder.dto.LoginMemberRow;
import com.foodorder.mapper.LoginMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LoginAutofillWebTests {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean LoginMapper mapper;
    private LoginMemberRow member;

    @BeforeEach
    void setUp() {
        member = new LoginMemberRow();
        member.setLoginId("food_user");
        member.setPasswordHash(encoder.encode("Password123"));
        member.setRole("ROLE_USER");
        member.setStatus("ACTIVE");
        member.setAccessNumber("123456789012");
        when(mapper.findLoginCandidates(eq("food_user"), anyString())).thenReturn(List.of(member));
    }

    @Test
    void bothTabsRenderSavedInputOptionAndPasswordAutofill() throws Exception {
        for (String type : List.of("USER", "OWNER")) {
            mvc.perform(get("/login").param("type", type))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"saveLogin\"")))
                    .andExpect(content().string(containsString("autocomplete=\"current-password\"")))
                    .andExpect(content().string(containsString("data-login-form")))
                    .andExpect(content().string(not(containsString("name=\"remember-me\""))));
        }
    }

    @Test
    void savingInputsDoesNotEnableAutomaticLogin() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("username", "food_user")
                        .param("password", "Password123").param("memberType", "USER")
                        .param("saveLogin", "true"))
                .andExpect(authenticated()).andExpect(redirectedUrl("/"))
                .andExpect(cookie().doesNotExist("foodorder-remember-me"));
        mvc.perform(get("/").cookie(new Cookie("foodorder-remember-me", "old-cookie")))
                .andExpect(unauthenticated());
    }

    @Test
    void ownerMustStillSubmitCorrectAccessNumber() throws Exception {
        member.setRole("ROLE_OWNER");
        mvc.perform(post("/login").with(csrf()).param("username", "food_user")
                        .param("password", "Password123").param("memberType", "OWNER")
                        .param("saveLogin", "true"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?type=OWNER&error"));
        mvc.perform(post("/login").with(csrf()).param("username", "food_user")
                        .param("password", "Password123").param("memberType", "OWNER")
                        .param("accessNumber", "123456789012").param("saveLogin", "true"))
                .andExpect(authenticated().withRoles("OWNER")).andExpect(redirectedUrl("/"));
    }

    @Test
    void autofillScriptIsAccessibleWithoutLogin() throws Exception {
        mvc.perform(get("/js/login-autofill.js")).andExpect(status().isOk());
    }
}
