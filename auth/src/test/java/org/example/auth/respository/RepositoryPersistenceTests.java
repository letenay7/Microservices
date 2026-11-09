package org.example.auth.respository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolationException;
import org.example.auth.entity.RefreshToken;
import org.example.auth.entity.User;
import org.example.auth.repository.RefreshTokenRepository;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.example.auth.support.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real PostgreSQL, real Hibernate mapping. Each test runs in a transaction that is rolled back.
**/
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfig.class)
class RepositoryPersistenceTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private User saveUser(String username, EnumSet<AccessRole> roles) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("$2a$10$hashedPasswordValue");
        user.setRoles(roles);
        return userRepository.saveAndFlush(user);
    }

    private RefreshToken newToken(User user, String value) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(value);
        token.setExpiryDate(Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS));
        return token;
    }

    // ---------- users ----------

    @Test
    void rolesShouldRoundTripThroughTheDatabase() {
        saveUser("martin", EnumSet.of(AccessRole.USER, AccessRole.ADMIN));
        entityManager.clear();

        User reloaded = userRepository.findByUsername("martin").orElseThrow();

        assertEquals(Set.of(AccessRole.USER, AccessRole.ADMIN), reloaded.getRoles());
    }

    @Test
    void roleAddedInPlaceShouldBePersisted() {
        // Same steps as UserService.promoteToAdmin: load, mutate the EnumSet in place, save.
        saveUser("martin", EnumSet.of(AccessRole.USER));
        entityManager.clear();

        User managed = userRepository.findByUsername("martin").orElseThrow();
        managed.addRole(AccessRole.ADMIN);
        userRepository.save(managed);
        entityManager.flush();
        entityManager.clear();

        User reloaded = userRepository.findByUsername("martin").orElseThrow();
        assertEquals(Set.of(AccessRole.USER, AccessRole.ADMIN), reloaded.getRoles(),
                "promotion must survive a reload from the database");
    }

    @Test
    void usernameShouldBeUnique() {
        saveUser("martin", EnumSet.of(AccessRole.USER));

        // This is what a concurrent registration hits after existsByUsername returned false
        assertThrows(DataIntegrityViolationException.class,
                () -> saveUser("martin", EnumSet.of(AccessRole.USER)));
    }

    @Test
    void blankUsernameShouldBeRejectedByBeanValidationOnSave() {
        // Entity validation fires on save, not at the controller. If the exception arrives wrapped
        // in your Hibernate/Spring version, adjust the expected type.
        assertThrows(ConstraintViolationException.class,
                () -> saveUser("  ", EnumSet.of(AccessRole.USER)));
    }

    @Test
    void existsByUsernameShouldReflectStoredUsers() {
        saveUser("martin", EnumSet.of(AccessRole.USER));

        assertTrue(userRepository.existsByUsername("martin"));
        assertFalse(userRepository.existsByUsername("ghost"));
    }

    // ---------- refresh tokens ----------

    @Test
    void tokenShouldRoundTripByTokenValue() {
        User user = saveUser("martin", EnumSet.of(AccessRole.USER));
        RefreshToken token = newToken(user, "token-1");
        refreshTokenRepository.saveAndFlush(token);
        entityManager.clear();

        RefreshToken found = refreshTokenRepository.findByToken("token-1").orElseThrow();

        assertEquals("token-1", found.getToken());
        assertEquals(token.getExpiryDate(), found.getExpiryDate());
    }

    @Test
    void userShouldBeAbleToHaveSeveralRefreshTokens() {
        // logging in from a second device creates a second token for the same user
        User user = saveUser("martin", EnumSet.of(AccessRole.USER));

        refreshTokenRepository.saveAndFlush(newToken(user, "token-1"));
        refreshTokenRepository.saveAndFlush(newToken(user, "token-2"));

        assertEquals(2, refreshTokenRepository.count());
    }

    @Test
    void tokenValueShouldBeUnique() {
        User user = saveUser("martin", EnumSet.of(AccessRole.USER));
        refreshTokenRepository.saveAndFlush(newToken(user, "same-token"));

        assertThrows(DataIntegrityViolationException.class,
                () -> refreshTokenRepository.saveAndFlush(newToken(user, "same-token")));
    }
}