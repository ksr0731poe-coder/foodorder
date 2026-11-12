package com.foodorder.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRequest {

    // 회원 유형
    @NotBlank(message = "会員種別を選択してください。")
    @Pattern(
            regexp = "USER|OWNER",
            message = "正しい会員種別を選択してください。"
    )
    private String type;

    // 공통 회원 정보
    @NotBlank(message = "ログインIDを入力してください。")
    @Pattern(
            regexp = "[A-Za-z0-9_]{4,50}",
            message = "ログインIDは半角英数字とアンダースコアで4〜50文字にしてください。"
    )
    private String loginId;

    @NotBlank(message = "ニックネームを入力してください。")
    @Size(
            max = 50,
            message = "ニックネームは50文字以内で入力してください。"
    )
    private String nickname;

    @NotBlank(message = "パスワードを入力してください。")
    @Size(
            min = 12,
            max = 64,
            message = "パスワードは12〜64文字で入力してください。"
    )
    private String password;

    @NotBlank(message = "確認用パスワードを入力してください。")
    @Size(
            min = 12,
            max = 64,
            message = "確認用パスワードは12〜64文字で入力してください。"
    )
    private String passwordConfirm;

    // 가게관리자 가입 정보
    @Size(
            max = 30,
            message = "事業者登録番号は30文字以内で入力してください。"
    )
    private String businessNumber;

    @Size(
            max = 150,
            message = "店舗名は150文字以内で入力してください。"
    )
    private String restaurantName;

    @Size(
            max = 150,
            message = "会社名（日本語）は150文字以内で入力してください。"
    )
    private String companyNameJa;

    @Size(
            max = 150,
            message = "会社名（英語）は150文字以内で入力してください。"
    )
    private String companyNameEn;

    // 가게 대표자 또는 관리자 이름
    @Size(
            max = 100,
            message = "氏名は100文字以内で入力してください。"
    )
    private String representative;

    // 개업일의 실제 날짜 검증은 서비스에서 처리
    @Size(
            max = 10,
            message = "開業日を正しい形式で入力してください。"
    )
    private String openedOn;

}