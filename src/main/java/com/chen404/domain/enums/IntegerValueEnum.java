package com.chen404.domain.enums;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 使用整数持久化的业务枚举；请求校验和内部校验共享同一组合法值。 */
public interface IntegerValueEnum {

    int getValue();

    /** 返回枚举声明的实际值，支持不连续的编码，禁止把数值范围当成枚举。 */
    static Set<Integer> supportedValues(Class<? extends IntegerValueEnum> enumType) {
        IntegerValueEnum[] values = enumType.getEnumConstants();
        if (values == null) {
            throw new IllegalArgumentException("整数业务值必须由枚举声明");
        }
        return Arrays.stream(values).map(IntegerValueEnum::getValue).collect(Collectors.toUnmodifiableSet());
    }

    /** null 不代表合法枚举；可选字段是否允许缺省由调用边界决定。 */
    static boolean contains(Class<? extends IntegerValueEnum> enumType, Integer value) {
        return value != null && supportedValues(enumType).contains(value);
    }
}
