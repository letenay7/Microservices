package org.example.auth.repository;

import org.example.auth.entity.RefreshToken;
import org.example.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository  extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    int deleteByUser(User user);
    void deleteByToken(String token);
    void deleteByExpiryDateBefore(Instant now);
}
