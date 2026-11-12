package com.foodorder.mapper;

import com.foodorder.dto.LoginMemberRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface LoginMapper {

    // 개인회원만 이메일로도 조회 가능
    // 별칭을 지정해 underscore 매핑 설정 없이 DTO에 연결
    @Select("""
            SELECT
                m.member_id AS memberId,
                m.login_id AS loginId,
                m.password_hash AS passwordHash,
                m.role AS role,
                m.status AS status,
                p.access_number AS accessNumber
            FROM members AS m
            LEFT JOIN member_auth_profiles AS p
                ON p.member_id = m.member_id
            WHERE m.login_id = #{identity}
               OR (
                    #{memberType} = 'USER'
                    AND m.email = #{identity}
               )
            LIMIT 2
            """)
    List<LoginMemberRow> findLoginCandidates(
            @Param("identity") String identity,
            @Param("memberType") String memberType
    );
}
