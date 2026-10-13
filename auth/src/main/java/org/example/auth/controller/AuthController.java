package org.example.auth.controller;

import org.example.auth.dto.*;
import org.example.auth.entity.RefreshToken;
import org.example.auth.entity.User;
import org.example.auth.repository.RefreshTokenRepository;
import org.example.auth.repository.UserRepository;
import org.example.auth.service.JwtService;
import org.example.auth.service.RefreshTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtService jwtService, RefreshTokenService refreshTokenService,
                          RefreshTokenRepository refreshTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> registerUser(@RequestBody RegisterRequestDTO registerRequestDTO) {
        if (userRepository.existsByUsername(registerRequestDTO.username())) {
            throw new RuntimeException("Username is already in use");
        }
        User user = new User();
        user.setUsername(registerRequestDTO.username());
        user.setPassword(passwordEncoder.encode(registerRequestDTO.password()));
        userRepository.save(user);
        String username = user.getUsername();
        String accessToken = jwtService.generateToken(username);
        String refreshToken = refreshTokenService.createRefreshToken(username).getToken();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponseDTO(accessToken, refreshToken));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> loginUser(@RequestBody LoginRequestDTO loginRequestDTO) {
        Optional<User> userOptional = userRepository.findByUsername(loginRequestDTO.username());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (!passwordEncoder.matches(loginRequestDTO.password(), user.getPassword())) {
                throw new BadCredentialsException("Invalid username or password");
            }
            String accessToken = jwtService.generateToken(user.getUsername());
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
            return ResponseEntity.ok(new AuthResponseDTO(accessToken, refreshToken.getToken()));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @PostMapping("/refresh_token")
    public ResponseEntity<AuthResponseDTO> refreshCurrentToken(
            @RequestBody TokenRefreshRequestDTO tokenRefreshRequestDTO) {
        String incomingToken = tokenRefreshRequestDTO.refreshToken();
        Optional<RefreshToken> token = refreshTokenRepository.findByToken(incomingToken);
        if (token.isPresent()) {
            RefreshToken tok = token.get();
            RefreshToken verifiedToken = refreshTokenService.verifyExpiration(tok);
            User tokUser = verifiedToken.getUser();
            String newToken = jwtService.generateToken(tokUser.getUsername());
            return ResponseEntity.ok(new AuthResponseDTO(newToken, incomingToken));
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponseDTO> logoutCurrentToken(
            @RequestBody TokenRefreshRequestDTO tokenRefreshRequestDTO) {
        String incomingToken = tokenRefreshRequestDTO.refreshToken();
        Optional<RefreshToken> token = refreshTokenRepository.findByToken(incomingToken);
        if (token.isPresent()) {
            refreshTokenRepository.deleteByToken(incomingToken);
            return ResponseEntity.ok(new MessageResponseDTO(incomingToken));
        }
        return ResponseEntity.notFound().build();
    }
}