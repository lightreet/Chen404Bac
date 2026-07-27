package com.chen404.domain.enums;

import com.chen404.domain.entity.User;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 用户可执行的稳定业务能力。
 *
 * <p>角色和信任级别负责描述身份，能力编码负责提供给前后端做功能授权。
 * 首期管理员和知友拥有全部创作能力，后续可以在这里接入用户级覆盖规则。</p>
 */
public enum UserCapabilityEnum {

    FRIEND_CONTENT_VIEW("friend-content:view"),
    ARTICLE_CREATE("article:create"),
    TRAVEL_CREATE("travel:create"),
    MUSIC_CREATE("music:create");

    private final String code;

    UserCapabilityEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 根据用户当前身份解析能力。调用方应传入已经补齐角色信息的用户对象。
     */
    public static List<String> resolveCodes(User user) {
        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            return List.of();
        }
        boolean admin = UserRoleEnum.ADMIN.matchesRoleCode(user.getRoleCode());
        boolean friend = Objects.equals(user.getTrustLevel(), UserTrustLevelEnum.FRIEND.getLevel());
        if (!admin && !friend) {
            return List.of();
        }
        return Arrays.stream(values()).map(UserCapabilityEnum::getCode).toList();
    }

    public static boolean containsCode(List<String> capabilities, String capabilityCode) {
        return capabilityCode != null
                && capabilities != null
                && capabilities.contains(capabilityCode);
    }
}
