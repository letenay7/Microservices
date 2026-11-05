package org.example.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain tests of the request DTO constraints: no Spring context. */
class RequestDtoValidationTests {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Set<String> violatedFields(Object dto) {
        return validator.validate(dto).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    // ---------- RegisterRequestDTO ----------

    @Test
    void register_validRequestShouldHaveNoViolations() {
        assertTrue(violatedFields(new RegisterRequestDTO("martin", "mySecurePassword123")).isEmpty());
    }

    @Test
    void register_blankUsernameShouldBeRejected() {
        assertEquals(Set.of("username"), violatedFields(new RegisterRequestDTO("   ", "mySecurePassword123")));
    }

    @Test
    void register_tooShortUsernameShouldBeRejected() {
        assertEquals(Set.of("username"), violatedFields(new RegisterRequestDTO("ab", "mySecurePassword123")));
    }

    @Test
    void register_tooShortPasswordShouldBeRejected() {
        assertEquals(Set.of("password"), violatedFields(new RegisterRequestDTO("martin", "short12")));
    }

    @Test
    void register_blankPasswordShouldBeRejected() {
        assertEquals(Set.of("password"), violatedFields(new RegisterRequestDTO("martin", "")));
    }

    @Test
    void register_passwordBeyondBcryptLimitShouldBeRejected() {
        assertTrue(violatedFields(new RegisterRequestDTO("martin", "a".repeat(72))).isEmpty());
        assertEquals(Set.of("password"), violatedFields(new RegisterRequestDTO("martin", "a".repeat(73))));
    }

    // ---------- LoginRequestDTO ----------

    @Test
    void login_blankFieldsShouldBeRejected() {
        assertEquals(Set.of("username", "password"), violatedFields(new LoginRequestDTO("", " ")));
    }

    @Test
    void login_shortPasswordShouldStillBeAccepted() {
        // the password policy applies when setting a password, not when checking one
        assertTrue(violatedFields(new LoginRequestDTO("martin", "pw")).isEmpty());
    }

    // ---------- ResetPasswordDTO ----------

    @Test
    void changePassword_validRequestShouldHaveNoViolations() {
        assertTrue(violatedFields(new ResetPasswordDTO("oldPassword123", "newPassword456")).isEmpty());
    }

    @Test
    void changePassword_weakNewPasswordShouldBeRejected() {
        assertEquals(Set.of("newPassword"), violatedFields(new ResetPasswordDTO("oldPassword123", "short")));
    }

    @Test
    void changePassword_blankCurrentPasswordShouldBeRejected() {
        assertEquals(Set.of("currentPassword"), violatedFields(new ResetPasswordDTO("", "newPassword456")));
    }

    // ---------- TokenRefreshRequestDTO ----------

    @Test
    void tokenRefresh_validTokenShouldHaveNoViolations() {
        assertTrue(violatedFields(new TokenRefreshRequestDTO("mock-refresh-token-uuid")).isEmpty());
    }

    @Test
    void tokenRefresh_blankOrMissingTokenShouldBeRejected() {
        assertEquals(Set.of("refreshToken"), violatedFields(new TokenRefreshRequestDTO("  ")));
        assertEquals(Set.of("refreshToken"), violatedFields(new TokenRefreshRequestDTO(null)));
    }
}