package org.example.auth.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain tests of the integrity constraints on RefreshToken: no Spring, no database. */
class RefreshTokenEntityValidationTests {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private RefreshToken validToken() {
        User user = new User();
        user.setUsername("martin");

        RefreshToken token = new RefreshToken();
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(Instant.now().plusSeconds(3600));
        token.setUser(user);
        return token;
    }

    private Set<String> violatedFields(RefreshToken token) {
        return validator.validate(token).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validTokenShouldHaveNoViolations() {
        assertTrue(violatedFields(validToken()).isEmpty());
    }

    @Test
    void blankTokenStringShouldBeRejected() {
        RefreshToken token = validToken();
        token.setToken(" ");

        assertEquals(Set.of("token"), violatedFields(token));
    }

    @Test
    void missingExpiryDateShouldBeRejected() {
        RefreshToken token = validToken();
        token.setExpiryDate(null);

        assertEquals(Set.of("expiryDate"), violatedFields(token));
    }

    @Test
    void missingUserShouldBeRejected() {
        RefreshToken token = validToken();
        token.setUser(null);

        assertEquals(Set.of("user"), violatedFields(token));
    }
}
