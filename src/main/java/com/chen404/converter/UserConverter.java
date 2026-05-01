package com.chen404.converter;

import com.chen404.domain.dto.UserProfileVO;
import com.chen404.domain.entity.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 用户视图转换器。
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    @Mapping(target = "username", source = "username")
    @Mapping(target = "nickname", source = "nickname")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "avatar", source = "avatar")
    @Mapping(target = "bio", source = "bio")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "role", source = "role")
    @Mapping(target = "roleCode", source = "roleCode")
    @Mapping(target = "roleName", source = "roleName")
    @Mapping(target = "trustLevel", source = "trustLevel")
    @Mapping(target = "trustLevelName", source = "trustLevelName")
    @Mapping(target = "memberLabel", source = "memberLabel")
    @Mapping(target = "createTime", source = "createTime")
    @Mapping(target = "lastLoginTime", source = "lastLoginTime")
    @Mapping(target = "lastLoginIp", source = "lastLoginIp")
    UserProfileVO toVO(User user);
}
