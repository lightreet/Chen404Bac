package com.chen404.domain.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterDTOValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRequireVerifiedEmailRegistrationChannel() {
        RegisterDTO missingEmail = validBase();
        missingEmail.setRegisterType("email");

        RegisterDTO phoneRegistration = validBase();
        phoneRegistration.setPhone("13800138000");
        phoneRegistration.setRegisterType("phone");

        RegisterDTO emailRegistration = validBase();
        emailRegistration.setEmail("user@example.com");
        emailRegistration.setRegisterType("email");

        assertFalse(validator.validate(missingEmail).isEmpty());
        assertFalse(validator.validate(phoneRegistration).isEmpty());
        assertTrue(validator.validate(emailRegistration).isEmpty());
    }

    private RegisterDTO validBase() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("user_404");
        dto.setPassword("secure-password");
        dto.setCode("123456");
        return dto;
    }
}
