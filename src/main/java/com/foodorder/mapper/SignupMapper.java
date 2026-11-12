package com.foodorder.mapper;

import com.foodorder.dto.SignupMemberRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface SignupMapper {

    // 아이디 중복 확인
    @Select("""
            SELECT COUNT(*)
            FROM members
            WHERE login_id = #{loginId}
            """)
    int countByLoginId(@Param("loginId") String loginId);

    // 닉네임 중복 확인
    @Select("""
            SELECT COUNT(*)
            FROM members
            WHERE nickname = #{nickname}
            """)
    int countByNickname(@Param("nickname") String nickname);

    // 이메일 중복 확인
    @Select("""
            SELECT COUNT(*)
            FROM members
            WHERE email = #{email}
            """)
    int countByEmail(@Param("email") String email);

    // 사업자번호 중복 확인
    @Select("""
            SELECT COUNT(*)
            FROM member_auth_profiles
            WHERE business_number = #{businessNumber}
            """)
    int countByBusinessNumber(
            @Param("businessNumber") String businessNumber
    );

    // 공통 회원 정보 저장
    // 생성된 member_id를 SignupMemberRow.memberId에 넣음
    @Insert("""
            INSERT INTO members (
                login_id,
                password_hash,
                nickname,
                email,
                role,
                status
            )
            VALUES (
                #{loginId},
                #{passwordHash},
                #{nickname},
                #{email},
                #{role},
                'ACTIVE'
            )
            """)
    @Options(
            useGeneratedKeys = true,
            keyProperty = "memberId",
            keyColumn = "member_id"
    )
    int insertMember(SignupMemberRow member);

    // 개인회원은 사업자 관련 값과 로그인 번호가 NULL
    @Insert("""
            INSERT INTO member_auth_profiles (
                member_id,
                access_number,
                business_number,
                restaurant_name,
                company_name_ja,
                company_name_en,
                representative,
                opened_on,
                email_verified_at
            )
            VALUES (
                #{memberId},
                #{accessNumber},
                #{businessNumber},
                #{restaurantName},
                #{companyNameJa},
                #{companyNameEn},
                #{representative},
                #{openedOn},
                #{emailVerifiedAt}
            )
            """)
    int insertProfile(
            @Param("memberId") Long memberId,
            @Param("accessNumber") String accessNumber,
            @Param("businessNumber") String businessNumber,
            @Param("restaurantName") String restaurantName,
            @Param("companyNameJa") String companyNameJa,
            @Param("companyNameEn") String companyNameEn,
            @Param("representative") String representative,
            @Param("openedOn") LocalDate openedOn,
            @Param("emailVerifiedAt") LocalDateTime emailVerifiedAt
    );

}