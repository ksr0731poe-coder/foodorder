package com.foodorder.mapper;

import com.foodorder.dto.LoginMemberRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.util.List;

@Mapper
public interface AccountRecoveryMapper {
    @Select("""
            SELECT member_id AS memberId, login_id AS loginId, role, status
            FROM members
            WHERE email = #{email} AND role = #{role} AND status = 'ACTIVE'
            LIMIT 2
            """)
    List<LoginMemberRow> findByEmail(@Param("email") String email, @Param("role") String role);

    @Update("""
            UPDATE members SET password_hash = #{hash}
            WHERE member_id = #{memberId} AND email = #{email}
              AND role = #{role} AND status = 'ACTIVE'
            """)
    int updatePassword(@Param("memberId") Long memberId, @Param("email") String email,
                       @Param("role") String role, @Param("hash") String hash);
}
