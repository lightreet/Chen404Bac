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
        dto.setPassword("secure-password");
        dto.setCode("123456");
        return dto;
    }

    @Test
    void shouldAcceptFullEmailUpToDatabaseCapacityWithoutUsername() {
        RegisterDTO request = validBase();
        request.setEmail("a".repeat(60) + "@" + "b".repeat(35) + ".com");
        assertTrue(validator.validate(request).isEmpty());

        request.setEmail("a".repeat(60) + "@" + "b".repeat(36) + ".com");
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void defaultEmailNicknameMustRemainEditableInProfile() {
        UpdateProfileDTO profile = new UpdateProfileDTO();
        profile.setAvatar("/default-member-avatar.svg");
        profile.setNickname("a".repeat(60) + "@" + "b".repeat(35) + ".com");
        assertTrue(validator.validate(profile).isEmpty());
        profile.setNickname(profile.getNickname() + "x");
        assertFalse(validator.validate(profile).isEmpty());
    }
}
