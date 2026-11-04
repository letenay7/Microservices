package org.example.auth.service;

import org.example.auth.entity.RefreshToken;
import org.example.auth.entity.User;
import org.example.auth.exception.TokenExpiredException;
import org.example.auth.exception.TokenNotFoundException;
import org.example.auth.repository.RefreshTokenRepository;
import org.example.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain unit tests: repositories are mocked, the real RefreshTokenService logic runs. */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTests {

    private static final long DURATION_MS = 604_800_000L;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User user;
    private RefreshToken validToken;
    private RefreshToken expiredToken;

    @BeforeEach
    void setUp() {
        // @Value fields are not populated without a Spring context
        ReflectionTestUtils.setField(refreshTokenService, "tokenExpirationDuration", DURATION_MS);

        user = new User();
        user.setId(1L);
        user.setUsername("martin");

        validToken = new RefreshToken();
        validToken.setToken("valid-token");
        validToken.setUser(user);
        validToken.setExpiryDate(Instant.now().plusSeconds(3600));

        expiredToken = new RefreshToken();
        expiredToken.setToken("expired-token");
        expiredToken.setUser(user);
        expiredToken.setExpiryDate(Instant.now().minusSeconds(60));
    }

    // ---------- createRefreshToken ----------

    @Test
    void createRefreshToken_shouldSaveUuidTokenForUserWithFutureExpiry() {
        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        Instant before = Instant.now();
        RefreshToken result = refreshTokenService.createRefreshToken("martin");
        Instant after = Instant.now();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken saved = captor.getValue();

        assertSame(user, saved.getUser());
        assertDoesNotThrow(() -> UUID.fromString(saved.getToken()), "token should be a UUID");
        assertTrue(!saved.getExpiryDate().isBefore(before.plusMillis(DURATION_MS))
                        && !saved.getExpiryDate().isAfter(after.plusMillis(DURATION_MS)),
                "expiry should be now + configured duration");
        assertSame(saved, result);
    }

    @Test
    void createRefreshToken_shouldGenerateDifferentTokenEachTime() {
        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(user));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        RefreshToken first = refreshTokenService.createRefreshToken("martin");
        RefreshToken second = refreshTokenService.createRefreshToken("martin");

        assertNotEquals(first.getToken(), second.getToken());
    }

    @Test
    void createRefreshToken_shouldThrowWhenUserDoesNotExist() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> refreshTokenService.createRefreshToken("ghost"));

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    // ---------- getRefreshToken ----------

    @Test
    void getRefreshToken_shouldReturnTokenWhenFound() {
        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(validToken));

        assertSame(validToken, refreshTokenService.getRefreshToken("valid-token"));
    }

    @Test
    void getRefreshToken_shouldThrowWhenNotFound() {
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertThrows(TokenNotFoundException.class, () -> refreshTokenService.getRefreshToken("unknown"));
    }

    // ---------- verifyExpiration ----------

    @Test
    void verifyExpiration_shouldReturnTokenAndKeepItWhenNotExpired() {
        // regression: the check used to be inverted and deleted VALID tokens
        assertSame(validToken, refreshTokenService.verifyExpiration(validToken));

        verify(refreshTokenRepository, never()).delete(any(RefreshToken.class));
    }

    @Test
    void verifyExpiration_shouldDeleteAndThrowWhenExpired() {
        assertThrows(TokenExpiredException.class, () -> refreshTokenService.verifyExpiration(expiredToken));

        verify(refreshTokenRepository).delete(expiredToken);
    }

    // ---------- refreshIncomingToken ----------

    @Test
    void refreshIncomingToken_shouldReturnValidToken() {
        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(validToken));

        assertSame(validToken, refreshTokenService.refreshIncomingToken("valid-token"));
    }

    @Test
    void refreshIncomingToken_shouldDeleteAndThrowWhenExpired() {
        when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(expiredToken));

        assertThrows(TokenExpiredException.class, () -> refreshTokenService.refreshIncomingToken("expired-token"));

        verify(refreshTokenRepository).delete(expiredToken);
    }

    @Test
    void refreshIncomingToken_shouldThrowNotFoundForUnknownToken() {
        // regression: used to throw TokenExpiredException("Refresh token expired")
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertThrows(TokenNotFoundException.class, () -> refreshTokenService.refreshIncomingToken("unknown"));
    }

    // ---------- logoutRefreshToken ----------

    @Test
    void logoutRefreshToken_shouldDeleteTokenWithoutThrowing() {
        // regression: used to delete and then throw TokenNotFoundException anyway
        when(refreshTokenRepository.findByToken("valid-token")).thenReturn(Optional.of(validToken));

        assertDoesNotThrow(() -> refreshTokenService.logoutRefreshToken("valid-token"));

        verify(refreshTokenRepository).deleteByToken("valid-token");
    }

    @Test
    void logoutRefreshToken_shouldThrowWhenTokenUnknown() {
        when(refreshTokenRepository.findByToken("unknown")).thenReturn(Optional.empty());

        assertThrows(TokenNotFoundException.class, () -> refreshTokenService.logoutRefreshToken("unknown"));

        verify(refreshTokenRepository, never()).deleteByToken(anyString());
    }

    // ---------- revokeAllTokens ----------

    @Test
    void revokeAllTokens_shouldDeleteEveryTokenOfTheUser() {
        refreshTokenService.revokeAllTokens("martin");

        verify(refreshTokenRepository).deleteByUserUsername("martin");
    }

    // ---------- clearExpiredTokens ----------

    @Test
    void clearExpiredTokens_shouldDeleteTokensExpiredBeforeNow() {
        Instant before = Instant.now();
        refreshTokenService.clearExpiredTokens();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).deleteByExpiryDateBefore(captor.capture());
        assertTrue(!captor.getValue().isBefore(before) && !captor.getValue().isAfter(Instant.now()));
    }
}
