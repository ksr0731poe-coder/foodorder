package com.foodorder.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {

        http
                .authorizeHttpRequests(auth -> auth

                        // 오류 화면을 처리할 때 로그인 화면으로 바뀌는 것을 방지
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()

                        // 비회원도 사용할 수 있는 화면·정적 파일
                        .requestMatchers(
                                HttpMethod.GET,
                                "/",
                                "/login",
                                "/join",
                                "/main.css",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/favicon.ico"
                        ).permitAll()

                        // 비회원도 가게 검색·목록·상세 조회 가능
                        .requestMatchers(
                                HttpMethod.GET,
                                "/restaurants",
                                "/restaurants/{restaurantId:[0-9]+}"
                        ).permitAll()

                        // 회원가입 요청은 비회원도 가능
                        // 실제 회원가입 처리는 이후 별도로 구현
                        .requestMatchers(HttpMethod.POST, "/join").permitAll()

                        // 그 밖의 요청은 로그인 필요
                        .anyRequest().authenticated()
                )

                // 지금은 기본 로그인 화면 유지
                // 직접 만든 login.html 연결은 다음 단계에서 변경
                .formLogin(Customizer.withDefaults());

        return http.build();
    }
}