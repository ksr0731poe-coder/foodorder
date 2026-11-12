package com.foodorder.mapper;

import com.foodorder.dto.SocialMemberRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SocialAccountMapper {

    @Select("""
            SELECT
                m.member_id AS memberId,
                m.login_id AS loginId,
                m.role AS role,
                m.status AS status
            FROM member_social_accounts AS s
            JOIN members AS m
                ON m.member_id = s.member_id
            WHERE s.provider = 'google'
              AND s.provider_user_id = #{googleSubject}
            """)
    SocialMemberRow findGoogleMember(
            @Param("googleSubject") String googleSubject
    );

    @Select("""
            SELECT
                member_id AS memberId,
                login_id AS loginId,
                role AS role,
                status AS status
            FROM members
            WHERE login_id = #{loginId}
            """)
    SocialMemberRow findMember(
            @Param("loginId") String loginId
    );

    @Insert("""
            INSERT INTO member_social_accounts (
                member_id,
                provider,
                provider_user_id
            )
            VALUES (
                #{memberId},
                'google',
                #{googleSubject}
            )
            """)
    int insertGoogleLink(
            @Param("memberId") Long memberId,
            @Param("googleSubject") String googleSubject
    );
}