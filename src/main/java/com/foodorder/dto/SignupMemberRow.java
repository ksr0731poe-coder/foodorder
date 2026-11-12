package com.foodorder.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupMemberRow {

    // INSERT 후 DB가 발급한 회원번호를 받음
    private Long memberId;

    private String loginId;

    // 평문 비밀번호가 아닌 BCrypt 해시
    private String passwordHash;

    private String nickname;

    // 서버가 인증 완료한 이메일
    private String email;

    // 서비스에서 결정한 권한
    private String role;
}