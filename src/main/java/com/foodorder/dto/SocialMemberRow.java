package com.foodorder.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SocialMemberRow {

    private Long memberId;
    private String loginId;
    private String role;
    private String status;
}