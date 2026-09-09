package com.chen404.validation;

import com.chen404.domain.enums.IntegerValueEnum;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnumValueValidatorTest {
    enum SparseStatus implements IntegerValueEnum {
        FIRST(1), LAST(7);
        private final int value;
        SparseStatus(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    static class Request {
        @EnumValue(SparseStatus.class)
        Integer status;
    }

    @Test
    void validatesActualEnumMembersInsteadOfNumericRangeAndAllowsOptionalNull() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var request = new Request();
            assertTrue(validator.validate(request).isEmpty());
            for (SparseStatus status : SparseStatus.values()) {
                request.status = status.getValue();
                assertTrue(validator.validate(request).isEmpty());
                assertTrue(IntegerValueEnum.contains(SparseStatus.class, request.status));
            }
            request.status = 2;
            assertFalse(validator.validate(request).isEmpty());
            assertFalse(IntegerValueEnum.contains(SparseStatus.class, 2));
            assertFalse(IntegerValueEnum.contains(SparseStatus.class, null));
        }
    }
}
