package org.example.auth.service;

import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;


@Service
public class JwtService {
    private final Key secretKey = Jwts.SIG.HS256.key().build();
    private final long jwtExpirationMs = 86400000; // 24 hours

    public String generateToken(String username) {
        return String.valueOf(Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(secretKey).compact());

    }
}
