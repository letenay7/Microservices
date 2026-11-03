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
import java.util.Optional;
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
        Optional<RefreshToken> optionalRefreshToken = refreshTokenRepository.findByToken(token);
        if (optionalRefreshToken.isPresent()) {
            return optionalRefreshToken.get();
        }
        throw new TokenNotFoundException("Token not found: " + token);
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().isAfter(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new TokenExpiredException("Refresh token expired");
        }
        return token;
    }
    public RefreshToken refreshIncomingToken(String incomingToken) {
        Optional<RefreshToken> token = refreshTokenRepository.findByToken(incomingToken);
        if (token.isPresent()) {
            return verifyExpiration(token.get());
        }
        throw new TokenExpiredException("Refresh token expired");
    }

    public void logoutRefreshToken(String incomingToken) {
        Optional<RefreshToken> token = refreshTokenRepository.findByToken(incomingToken);
        if (token.isPresent()) {
            refreshTokenRepository.deleteByToken(incomingToken);
        }
        throw new TokenNotFoundException("Refresh token not found");

    }

    @Transactional
    public void deleteByToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void clearExpiredTokens() {
        refreshTokenRepository.deleteByExpiryDateBefore(Instant.now());
    }
}
