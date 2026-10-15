package org.example.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Date;


@Service
public class JwtService {
    private final SecretKey secretKey = Jwts.SIG.HS256.key().build();
    private final long jwtExpirationMs = 86400000; // 24 hours

    public String generateToken(String username) {
        return String.valueOf(Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                .signWith(secretKey).compact());

    }

    public String extractUsername(String token) {
        Jws<Claims> claimsJws = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
        return claimsJws.getPayload().getSubject();
    }
    public boolean isTokenValid(String token, String username) {
        Jws<Claims> claimsJws = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
        if (!claimsJws.getPayload().getSubject().equals(username)) {
            return false;
        }
        return isTokenExpired(token);

    }
    public boolean isTokenExpired(String token) {
        Jws<Claims> claimsJws = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token);
        return claimsJws.getPayload().getExpiration().before(new Date());

    }
}
