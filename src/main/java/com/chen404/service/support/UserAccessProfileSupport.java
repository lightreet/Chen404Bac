package com.chen404.service.support;

import com.chen404.domain.entity.Role;
import com.chen404.domain.entity.SysFile;
import com.chen404.domain.entity.User;
import com.chen404.mapper.RoleMapper;
import com.chen404.mapper.SysFileMapper;
import com.chen404.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 统一装配用户的权限侧资料，避免角色/信任级别逻辑散落在多个业务类中。
 */
@Component
public class UserAccessProfileSupport {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private SysFileMapper sysFileMapper;

    /**
     * 根据用户 ID 加载用户并补齐权限相关信息。
     */
    public User loadUserProfile(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userMapper.selectById(userId);
        return enrichUserProfile(user);
    }

    /**
     * 对已有用户对象补齐角色编码、角色值与信任级别，并清除敏感信息。
     */
    public User enrichUserProfile(User user) {
        if (user == null) {
            return null;
        }
        user.setPassword(null);
        applyDisplayAvatar(user);
        applyRoleInfo(user);
        if (user.getTrustLevel() == null) {
            user.setTrustLevel(User.TrustLevel.NORMAL);
        }
        return user;
    }

    /**
     * 若有 avatar_file_id，用 sys_file.file_url 覆盖展示用 avatar；查不到或文件无 URL 则保留库内 avatar。
     */
    public void applyDisplayAvatar(User user) {
        if (user == null || user.getAvatarFileId() == null) {
            return;
        }
        SysFile f = sysFileMapper.selectById(user.getAvatarFileId());
        if (f != null && StringUtils.hasText(f.getFileUrl())) {
            user.setAvatar(f.getFileUrl());
        }
    }

    private void applyRoleInfo(User user) {
        List<Role> roles = roleMapper.selectRolesByUserId(user.getId());
        Role primaryRole = roles.stream()
                .filter(role -> User.RoleCode.ADMIN.equals(role.getRoleCode()))
                .findFirst()
                .orElseGet(() -> roles.isEmpty() ? null : roles.get(0));

        if (primaryRole == null) {
            user.setRole(User.RoleValue.USER);
            user.setRoleCode(User.RoleCode.USER);
            return;
        }

        user.setRoleCode(primaryRole.getRoleCode());
        user.setRole(User.RoleCode.ADMIN.equals(primaryRole.getRoleCode())
                ? User.RoleValue.ADMIN
                : User.RoleValue.USER);
    }
}
