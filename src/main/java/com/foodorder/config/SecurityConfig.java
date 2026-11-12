package com.foodorder.config;

import com.foodorder.security.GoogleAuthorizationRequestRepository;
import com.foodorder.security.GoogleOidcUserService;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            LoginAuthenticationProvider loginProvider,
            GoogleOidcUserService googleUsers,
            GoogleAuthorizationRequestRepository googleRequests
    ) throws Exception {

        http
                .authenticationProvider(loginProvider)

                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(
                                DispatcherType.ERROR
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/",
                                "/login",
                                "/account/recovery",
                                "/join",
                                "/join/form",
                                "/join/complete",
                                "/main.css",
                                "/auth.css",
                                "/auth.js",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico"
                        ).permitAll()

                        // 기존 비회원 검색 허용 유지
                        .requestMatchers(
                                HttpMethod.GET,
                                "/restaurants",
                                "/restaurants/{restaurantId:[0-9]+}"
                        ).permitAll()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/join",
                                "/join/email/send",
                                "/join/email/verify",
                                "/account/recovery/id",
                                "/account/recovery/send",
                                "/account/recovery/verify",
                                "/account/recovery/reset"
                        ).permitAll()

                        // Google 인증 시작·콜백
                        .requestMatchers(
                                HttpMethod.GET,
                                "/oauth2/authorization/google",
                                "/login/oauth2/code/google"
                        ).permitAll()

                        // 연결 요청은 로그인한 일반회원만 허용
                        .requestMatchers(
                                HttpMethod.POST,
                                "/account/social/link/google"
                        ).hasRole("USER")

                        .anyRequest().authenticated()
                )

                // 기존 아이디·비밀번호 로그인
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")

                        .authenticationDetailsSource(request ->
                                new LoginAuthenticationProvider.LoginDetails(
                                        request.getParameter("memberType"),
                                        request.getParameter("accessNumber")
                                )
                        )

                        .defaultSuccessUrl("/", true)

                        .failureHandler(
                                (request, response, exception) -> {
                                    String type = "OWNER".equals(
                                            request.getParameter(
                                                    "memberType"
                                            )
                                    ) ? "OWNER" : "USER";

                                    response.sendRedirect(
                                            request.getContextPath()
                                                    + "/login?type="
                                                    + type
                                                    + "&error"
                                    );
                                }
                        )

                        .permitAll()
                )

                // Google 로그인
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")

                        .authorizationEndpoint(endpoint -> endpoint
                                .authorizationRequestRepository(
                                        googleRequests
                                )
                        )

                        .userInfoEndpoint(userInfo -> userInfo
                                .oidcUserService(
                                        googleUsers::loadUser
                                )
                        )

                        .defaultSuccessUrl("/", true)

                        .failureHandler(
                                (request, response, exception) -> {
                                    String code =
                                            exception instanceof
                                                    OAuth2AuthenticationException e
                                                    ? e.getError()
                                                    .getErrorCode()
                                                    : "";

                                    String message = switch (code) {
                                        case "google_not_linked" ->
                                                "このGoogleアカウントは未連携です。"
                                                        + "通常のログイン後に連携してください。";

                                        case "google_conflict" ->
                                                "このGoogleアカウント、または"
                                                        + "Food Orderアカウントは"
                                                        + "既に別の連携に使用されています。";

                                        case "google_link_expired" ->
                                                "連携の有効期限が切れたか、"
                                                        + "ログイン状態が変更されました。"
                                                        + "もう一度お試しください。";

                                        case "google_account_unavailable" ->
                                                "このアカウントでは"
                                                        + "Googleログインを利用できません。";

                                        default ->
                                                "Googleログインに失敗しました。"
                                                        + "もう一度お試しください。";
                                    };

                                    request.getSession().setAttribute(
                                            "googleMessage",
                                            message
                                    );

                                    response.sendRedirect(
                                            request.getContextPath()
                                                    + "/login?type=USER"
                                    );
                                }
                        )
                )

                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .deleteCookies("foodorder-remember-me")
                        .permitAll()
                );

        // CSRF・セッション保護は基本設定を維持
        return http.build();
    }
}
