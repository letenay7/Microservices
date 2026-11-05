package org.example.auth.service;

import org.example.auth.entity.RefreshToken;
import org.example.auth.entity.User;
import org.example.auth.exception.TokenExpiredException;
import org.example.auth.exception.TokenNotFoundException;
import org.example.auth.repository.RefreshTokenRepository;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.example.auth.support.PostgresTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The service runs against real PostgreSQL with its OWN transactions: the test itself opens none
 * (NOT_SUPPORTED), so this shows whether the @Transactional settings on RefreshTokenService are
 * right. Data is really committed, so every test cleans up.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.application.token-expiration-duration=604800000"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, RefreshTokenService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class RefreshTokenServiceIntegrationTests {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        refreshTokenRepository.deleteAll();   // tokens first: they reference users
        userRepository.deleteAll();
    }

    private User saveUser(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("$2a$10$hashedPasswordValue");
        user.setRoles(EnumSet.of(AccessRole.USER));
        return userRepository.save(user);
    }

    private RefreshToken saveToken(User user, String value, Instant expiry) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setToken(value);
        token.setExpiryDate(expiry);
        return refreshTokenRepository.save(token);
    }

    @Test
    void createRefreshTokenShouldPersistTokenForUser() {
        saveUser("martin");

        RefreshToken created = refreshTokenService.createRefreshToken("martin");

        assertTrue(refreshTokenRepository.findByToken(created.getToken()).isPresent());
    }

    @Test
    void refreshedTokenShouldExposeItsUserWithoutAnOpenSession() {
        // The controller calls token.getUser().getUsername() after the service transaction has ended.
        // With fetch = LAZY this needs @EntityGraph(attributePaths = "user") on findByToken
        // (or Open Session In View). Outside a web request there is no OSIV, so this test shows the truth.
        User user = saveUser("martin");
        saveToken(user, "valid-token", Instant.now().plusSeconds(3600));

        RefreshToken refreshed = refreshTokenService.refreshIncomingToken("valid-token");

        assertDoesNotThrow(() -> refreshed.getUser().getUsername(),
                "add @EntityGraph(attributePaths = \"user\") to RefreshTokenRepository.findByToken");
    }

    @Test
    void expiredTokenShouldBeDeletedEvenThoughAnExceptionIsThrown() {
        // Proves noRollbackFor = TokenExpiredException works: without it the delete is rolled back
        User user = saveUser("martin");
        saveToken(user, "expired-token", Instant.now().minusSeconds(60));

        assertThrows(TokenExpiredException.class,
                () -> refreshTokenService.refreshIncomingToken("expired-token"));

        assertTrue(refreshTokenRepository.findByToken("expired-token").isEmpty(),
                "expired token must be gone after the failed refresh");
    }

    @Test
    void validTokenShouldSurviveRefresh() {
        User user = saveUser("martin");
        saveToken(user, "valid-token", Instant.now().plusSeconds(3600));

        refreshTokenService.refreshIncomingToken("valid-token");

        assertTrue(refreshTokenRepository.findByToken("valid-token").isPresent());
    }

    @Test
    void logoutShouldDeleteTheToken() {
        // Derived delete queries need a read-write transaction; the service must provide one
        User user = saveUser("martin");
        saveToken(user, "valid-token", Instant.now().plusSeconds(3600));

        refreshTokenService.logoutRefreshToken("valid-token");

        assertTrue(refreshTokenRepository.findByToken("valid-token").isEmpty());
    }

    @Test
    void logoutWithUnknownTokenShouldThrow() {
        assertThrows(TokenNotFoundException.class, () -> refreshTokenService.logoutRefreshToken("unknown"));
    }

    @Test
    void revokeAllTokensShouldOnlyRemoveThatUsersTokens() {
        User martin = saveUser("martin");
        User other = saveUser("other");
        saveToken(martin, "martin-1", Instant.now().plusSeconds(3600));
        saveToken(martin, "martin-2", Instant.now().plusSeconds(3600));
        saveToken(other, "other-1", Instant.now().plusSeconds(3600));

        refreshTokenService.revokeAllTokens("martin");

        assertTrue(refreshTokenRepository.findByToken("martin-1").isEmpty());
        assertTrue(refreshTokenRepository.findByToken("martin-2").isEmpty());
        assertTrue(refreshTokenRepository.findByToken("other-1").isPresent());
    }

    @Test
    void clearExpiredTokensShouldOnlyRemoveExpiredOnes() {
        User user = saveUser("martin");
        saveToken(user, "expired", Instant.now().minusSeconds(60));
        saveToken(user, "valid", Instant.now().plusSeconds(3600));

        refreshTokenService.clearExpiredTokens();

        assertTrue(refreshTokenRepository.findByToken("expired").isEmpty());
        assertEquals(1, refreshTokenRepository.count());
    }
}