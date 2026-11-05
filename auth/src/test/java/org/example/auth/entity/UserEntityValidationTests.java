package org.example.auth.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.example.auth.security.AccessRole;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain tests of the entity's constraints: no Spring, no database. Needs hibernate-validator on the classpath. */
class UserEntityValidationTests {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private User validUser() {
        User user = new User();
        user.setUsername("martin");
        user.setPassword("$2a$10$abcdefghijklmnopqrstuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu");
        user.setRoles(EnumSet.of(AccessRole.USER));
        return user;
    }

    private Set<String> violatedFields(User user) {
        return validator.validate(user).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validUserShouldHaveNoViolations() {
        assertTrue(violatedFields(validUser()).isEmpty());
    }

    @Test
    void blankUsernameShouldBeRejected() {
        User user = validUser();
        user.setUsername("   ");

        assertEquals(Set.of("username"), violatedFields(user));
    }

    @Test
    void blankStoredPasswordShouldBeRejected() {
        User user = validUser();
        user.setPassword("");

        assertEquals(Set.of("password"), violatedFields(user));
    }

    @Test
    void newUserShouldStartWithEmptyRolesInsteadOfNull() {
        User user = new User();

        assertTrue(user.addRole(AccessRole.USER));
        assertEquals(EnumSet.of(AccessRole.USER), user.getRoles());
    }
}
