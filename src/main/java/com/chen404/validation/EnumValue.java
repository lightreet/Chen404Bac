package com.chen404.validation;

import com.chen404.domain.enums.IntegerValueEnum;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 校验整数是否为指定业务枚举值；允许 null，必填字段另用 NotNull。 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = EnumValueValidator.class)
public @interface EnumValue {
    Class<? extends IntegerValueEnum> value();
    String message() default "枚举值无效";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
