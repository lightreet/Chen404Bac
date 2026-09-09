package com.chen404.domain.enums;

/** 账号启停状态，供权限判断共享。 */
public enum UserStatusEnum implements IntegerValueEnum {
    DISABLED(0), ENABLED(1);

    private final int value;

    UserStatusEnum(int value) {
        this.value = value;
    }

    @Override
    public int getValue() {
        return value;
    }

    public static boolean isEnabled(Integer value) {
        return value != null && value == ENABLED.value;
    }
}
