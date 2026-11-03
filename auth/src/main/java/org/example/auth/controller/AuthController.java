package org.example.auth.controller;

import org.example.auth.dto.*;
import org.example.auth.entity.RefreshToken;
import org.example.auth.entity.User;
import org.example.auth.service.JwtService;
import org.example.auth.service.RefreshTokenService;
import org.example.auth.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService userService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(UserService userService, JwtService jwtService,
                          RefreshTokenService refreshTokenService) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> registerUser(@RequestBody RegisterRequestDTO registerRequestDTO) {
        User user = userService.createUser(registerRequestDTO);
        String username = user.getUsername();
        String accessToken = jwtService.generateToken(username);
        String refreshToken = refreshTokenService.createRefreshToken(username).getToken();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponseDTO(accessToken, refreshToken));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> loginUser(@RequestBody LoginRequestDTO loginRequestDTO) {
        User user = userService.login(loginRequestDTO);
        String accessToken = jwtService.generateToken(user.getUsername());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
        return ResponseEntity.ok(new AuthResponseDTO(accessToken, refreshToken.getToken()));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponseDTO> refreshCurrentToken(
            @RequestBody TokenRefreshRequestDTO tokenRefreshRequestDTO) {
        String incomingToken = tokenRefreshRequestDTO.refreshToken();
        RefreshToken verifiedToken = refreshTokenService.refreshIncomingToken(incomingToken);
        User tokUser = verifiedToken.getUser();
        String newToken = jwtService.generateToken(tokUser.getUsername());
        return ResponseEntity.ok(new AuthResponseDTO(newToken, incomingToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponseDTO> logoutCurrentToken(
            @RequestBody TokenRefreshRequestDTO tokenRefreshRequestDTO) {
        String incomingToken = tokenRefreshRequestDTO.refreshToken();
        refreshTokenService.logoutRefreshToken(incomingToken);
        return ResponseEntity.ok(new MessageResponseDTO(incomingToken));
    }

    @PostMapping("/admin")
    public ResponseEntity<AuthResponseDTO> loginAdmin(@RequestBody LoginRequestDTO loginRequestDTO) {
        User user = userService.loginAdmin(loginRequestDTO);
        String accessToken = jwtService.generateToken(user.getUsername());
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getUsername());
        return ResponseEntity.ok(new AuthResponseDTO(accessToken, refreshToken.getToken()));
    }
}