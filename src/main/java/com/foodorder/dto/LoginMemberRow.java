package com.foodorder.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LoginMemberRow {

    private Long memberId;

    private String loginId;

    // DB에 저장된 BCrypt 해시
    private String passwordHash;

    private String role;

    private String status;

    // 가게관리자 또는 관리자 로그인용 번호
    private String accessNumber;
}