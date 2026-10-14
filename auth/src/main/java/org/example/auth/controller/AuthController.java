package org.example.auth.controller;

import org.example.auth.dto.*;
import org.example.auth.entity.RefreshToken;
import org.example.auth.entity.User;
import org.example.auth.exception.GlobalExceptionHandler;
import org.example.auth.exception.UserAlreadyExistsException;
import org.example.auth.repository.RefreshTokenRepository;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.example.auth.service.JwtService;
import org.example.auth.service.RefreshTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final GlobalExceptionHandler exceptionHandler;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtService jwtService, RefreshTokenService refreshTokenService,
                          RefreshTokenRepository refreshTokenRepository, GlobalExceptionHandler exceptionHandler) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.exceptionHandler = exceptionHandler;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequestDTO registerRequestDTO) {
        String username = registerRequestDTO.username();
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("User with username "  + username + " already exists" );
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(registerRequestDTO.password()));

        // set default role AccessRole.USER
        user.setRoles(EnumSet.of(AccessRole.USER));
        userRepository.save(user);
        String accessToken = jwtService.generateToken(username);
        String refreshToken = refreshTokenService.createRefreshToken(username).getToken();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponseDTO(accessToken, refreshToken));
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequestDTO loginRequestDTO) {
        String username = loginRequestDTO.username();
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (!passwordEncoder.matches(loginRequestDTO.password(), user.getPassword())) {
                throw new BadCredentialsException("Invalid username or password");
            }
            String accessToken = jwtService.generateToken(user.getUsername());
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
            return ResponseEntity.ok(new AuthResponseDTO(accessToken, refreshToken.getToken()));
        }
        throw new UsernameNotFoundException("User with username " + username + " not found");
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

    @PostMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> loginAdmin(@RequestBody LoginRequestDTO loginRequestDTO) {
        Optional<User> userOptional = userRepository.findByUsername(loginRequestDTO.username());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (!passwordEncoder.matches(loginRequestDTO.password(), user.getPassword())) {
                throw new BadCredentialsException("Invalid username or password");
            }

            boolean isAdmin = user.getRoles().stream()
                    .anyMatch(role -> role.getValue().equals("ADMIN"));
            if (!isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponseDTO(HttpStatus.FORBIDDEN.value(),
                                "Access denied: Admin role required", System.currentTimeMillis()));
            }

            String accessToken = jwtService.generateToken(user.getUsername());
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
            return ResponseEntity.ok(new AuthResponseDTO(accessToken, refreshToken.getToken()));
        }
        throw new UsernameNotFoundException(
                "User with username " + loginRequestDTO.username() + " not found");
    }
}