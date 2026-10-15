package org.example.auth.controller;

import org.example.auth.dto.*;
import org.example.auth.entity.RefreshToken;
import org.example.auth.entity.User;
import org.example.auth.repository.RefreshTokenRepository;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.example.auth.service.JwtService;
import org.example.auth.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class) // Tests *only* the AuthController web layer
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private RefreshTokenRepository refreshTokenRepository;

    private long tokenExpirationDuration = 604800000L;

    private User mockUser;
    private RefreshToken mockRefreshToken;

    private User mockAdminUser;
    private RefreshToken mockAdminRefreshToken;


    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("martin");
        mockUser.setPassword("hashed_password_123");
        mockUser.setRoles(EnumSet.of(AccessRole.USER));

        mockRefreshToken = new RefreshToken();
        mockRefreshToken.setId(1L);
        mockRefreshToken.setToken("mock-refresh-token-uuid");
        mockRefreshToken.setExpiryDate(Instant.now().plusMillis(tokenExpirationDuration));
        mockRefreshToken.setUser(mockUser);

        mockAdminUser = new User();
        mockAdminUser.setId(2L);
        mockAdminUser.setUsername("admin");
        mockAdminUser.setPassword("StrongPassword123");
        mockAdminUser.setRoles(EnumSet.of(AccessRole.ADMIN, AccessRole.USER));

        mockAdminRefreshToken = new RefreshToken();
        mockAdminRefreshToken.setId(2L);
        mockAdminRefreshToken.setToken("mock-refresh-token-admin-uuid");
        mockAdminRefreshToken.setExpiryDate(Instant.now().plusMillis(tokenExpirationDuration));
        mockAdminRefreshToken.setUser(mockAdminUser);
    }

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        RegisterRequestDTO request = new RegisterRequestDTO("martin", "mySecurePassword123");

        when(passwordEncoder.encode(anyString())).thenReturn("hashed_password_123");
        when(userRepository.save(any(User.class))).thenReturn(mockUser);
        when(jwtService.generateToken("martin")).thenReturn("mock-access-token");

        RefreshToken token = new RefreshToken();
        token.setToken("mock-refresh-token-uuid");
        when(refreshTokenService.createRefreshToken("martin")).thenReturn(token);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void shouldLoginUserSuccessfully() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("martin", "mySecurePassword123");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("mySecurePassword123", "hashed_password_123")).thenReturn(true);
        when(jwtService.generateToken("martin")).thenReturn("mock-access-token");

        RefreshToken token = new RefreshToken();
        token.setToken("mock-refresh-token-uuid");
        when(refreshTokenService.createRefreshToken("martin")).thenReturn(token);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.generateToken("martin")).thenReturn("mock-access-token");
        when(refreshTokenService.createRefreshToken("martin")).thenReturn(mockRefreshToken);

        when(refreshTokenRepository.findByToken("mock-refresh-token-uuid"))
                .thenReturn(Optional.of(mockRefreshToken));
        when(refreshTokenService.verifyExpiration(any(RefreshToken.class))).thenReturn(mockRefreshToken);

        LoginRequestDTO request = new LoginRequestDTO("martin", "mySecurePassword123");

        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        AuthResponseDTO authResponse = objectMapper.readValue(responseBody, AuthResponseDTO.class);

        TokenRefreshRequestDTO refreshRequest = new TokenRefreshRequestDTO(authResponse.refreshToken());

        mockMvc.perform(post("/api/auth/refresh_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void shouldLogoutUserSuccessfully() throws Exception {
        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(jwtService.generateToken("martin")).thenReturn("mock-access-token");
        when(refreshTokenService.createRefreshToken("martin")).thenReturn(mockRefreshToken);

        when(refreshTokenRepository.findByToken("mock-refresh-token-uuid"))
                .thenReturn(Optional.of(mockRefreshToken));
        when(refreshTokenService.verifyExpiration(any(RefreshToken.class))).thenReturn(mockRefreshToken);
        doNothing().when(refreshTokenRepository).deleteByToken("mock-refresh-token-uuid");

        TokenRefreshRequestDTO refreshRequest = new TokenRefreshRequestDTO(
                "mock-refresh-token-uuid");

        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk());

    }

    @Test
    void shouldRejectNonAdminUser() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("martin", "mySecurePassword123");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("mySecurePassword123", "hashed_password_123"))
                .thenReturn(true);
        when(jwtService.generateToken("martin")).thenReturn("mock-access-token");

        RefreshToken token = new RefreshToken();
        token.setToken("mock-refresh-token-uuid");
        when(refreshTokenService.createRefreshToken("martin")).thenReturn(token);

        mockMvc.perform(post("/api/auth/admin")
        .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAcceptAdminUser() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("admin", "SupePassword123");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(mockAdminUser));
        when(passwordEncoder.matches("SupePassword123", "StrongPassword123"))
                .thenReturn(true);
        when(jwtService.generateToken("admin")).thenReturn("mock-access-token");
        RefreshToken token = new RefreshToken();
        token.setToken("mock-refresh-token-uuid");
        when(refreshTokenService.createRefreshToken("admin")).thenReturn(token);
        mockMvc.perform(post("/api/auth/admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk());
    }
}