package com.chen404.validation;

import com.chen404.domain.enums.IntegerValueEnum;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

/** 初始化时从枚举读取合法值，避免请求校验重复维护业务编码。 */
public final class EnumValueValidator implements ConstraintValidator<EnumValue, Integer> {
    private Set<Integer> supportedValues;

    @Override
    public void initialize(EnumValue constraint) {
        supportedValues = IntegerValueEnum.supportedValues(constraint.value());
    }

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || supportedValues.contains(value);
    }
}
