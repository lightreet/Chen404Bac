package com.chen404.domain.enums;

import lombok.Getter;

@Getter
public enum UserRoleEnum {

    ADMIN(1, "admin", "管理员"),
    USER(0, "user", "读者"),
    GUEST(-1, "guest", "来客");

    private final int roleValue;
    private final String roleCode;
    private final String displayName;

    UserRoleEnum(int roleValue, String roleCode, String displayName) {
        this.roleValue = roleValue;
        this.roleCode = roleCode;
        this.displayName = displayName;
    }

    public static UserRoleEnum fromRoleCode(String roleCode) {
        if (roleCode != null) {
            for (UserRoleEnum value : values()) {
                if (value.roleCode.equalsIgnoreCase(roleCode.trim())) {
                    return value;
                }
            }
        }
        return USER;
    }

    public static UserRoleEnum fromRoleValue(Integer roleValue) {
        if (roleValue != null) {
            for (UserRoleEnum value : values()) {
                if (value.roleValue == roleValue) {
                    return value;
                }
            }
        }
        return USER;
    }

    public boolean matchesRoleCode(String roleCode) {
        return roleCode != null && this.roleCode.equalsIgnoreCase(roleCode.trim());
    }
}
