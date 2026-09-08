package com.chen404.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chen404.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 用户Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /** 同一用户的头像和公开资料变更在事务内串行，防止引用关系与资料不一致。 */
    @Select("SELECT * FROM sys_user WHERE id=#{id} AND deleted=0 FOR UPDATE")
    User selectByIdForUpdate(@Param("id") Long id);

    /** 登录只更新审计字段；期间改密或禁用时拒绝继续签发令牌。 */
    @Update("UPDATE sys_user SET last_login_time=#{time}, last_login_ip=#{ip}, update_time=CURRENT_TIMESTAMP "
            + "WHERE id=#{id} AND password=#{expectedPassword} AND status=1 AND deleted=0")
    int updateLoginAudit(@Param("id") Long id, @Param("expectedPassword") String expectedPassword,
            @Param("time") LocalDateTime time, @Param("ip") String ip);

    /** 资料更新采用字段白名单，并允许清空简介、头像。 */
    @Update("UPDATE sys_user SET nickname=#{nickname}, avatar=#{avatar}, avatar_file_id=#{avatarFileId}, "
            + "bio=#{bio}, profile_visibility=#{profileVisibility}, email_public=#{emailPublic}, "
            + "update_time=CURRENT_TIMESTAMP WHERE id=#{id} AND deleted=0")
    int updateProfileFields(User profile);

    /** 信任级别调整不写回用户凭据、状态或资料。 */
    @Update("UPDATE sys_user SET trust_level=#{trustLevel}, update_time=CURRENT_TIMESTAMP "
            + "WHERE id=#{id} AND deleted=0")
    int updateTrustLevel(@Param("id") Long id, @Param("trustLevel") Integer trustLevel);

    /** 密码比较并更新，阻止两次并发改密使用同一个旧凭据相互覆盖。 */
    @Update("UPDATE sys_user SET password=#{password}, update_time=CURRENT_TIMESTAMP "
            + "WHERE id=#{id} AND password=#{expectedPassword} AND status=1 AND deleted=0")
    int updatePasswordIfUnchanged(@Param("id") Long id, @Param("expectedPassword") String expectedPassword,
            @Param("password") String password);

    /**
     * 根据用户名查询用户
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND deleted = 0")
    User selectByUsername(@Param("username") String username);

    /**
     * 根据邮箱查询用户
     */
    @Select("SELECT * FROM sys_user WHERE email = #{email} AND deleted = 0")
    User selectByEmail(@Param("email") String email);

    /**
     * 根据手机号查询用户
     */
    @Select("SELECT * FROM sys_user WHERE phone = #{phone} AND deleted = 0")
    User selectByPhone(@Param("phone") String phone);
}
