package org.example.auth.service;

import org.example.auth.entity.RefreshToken;
import org.example.auth.entity.User;
import org.example.auth.exception.TokenExpiredException;
import org.example.auth.exception.TokenNotFoundException;
import org.example.auth.repository.RefreshTokenRepository;
import org.example.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${spring.application.token-expiration-duration}")
    private long tokenExpirationDuration;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    public RefreshToken createRefreshToken(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setExpiryDate(Instant.now().plusMillis(tokenExpirationDuration));
        return refreshTokenRepository.save(refreshToken);
    }

    public RefreshToken getRefreshToken(String token) {
       return  refreshTokenRepository.findByToken(token)
               .orElseThrow(() -> new TokenNotFoundException("Token not found: " + token));
    }

    public boolean isTokenExpired(RefreshToken token) {
        return token.getExpiryDate().isBefore(Instant.now());
    }

    @Transactional(noRollbackFor = TokenExpiredException.class)
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (isTokenExpired(token)) {
            refreshTokenRepository.delete(token);
            throw new TokenExpiredException("Refresh token expired");
        }
        return token;
    }

    @Transactional(noRollbackFor = TokenExpiredException.class)
    public RefreshToken refreshIncomingToken(String incomingToken) {
        return verifyExpiration(getRefreshToken(incomingToken));
    }

    @Transactional
    public void logoutRefreshToken(String incomingToken) {
        if (refreshTokenRepository.findByToken(incomingToken).isEmpty()) {
            throw new TokenNotFoundException("Refresh token not found");
        }
        refreshTokenRepository.deleteByToken(incomingToken);       // previously fell through to the throw
    }

    /** Logs the user out everywhere, e.g. after a password change. */
    @Transactional
    public void revokeAllTokens(String username) {
        refreshTokenRepository.deleteByUserUsername(username);
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void clearExpiredTokens() {
        refreshTokenRepository.deleteByExpiryDateBefore(Instant.now());
    }
}
