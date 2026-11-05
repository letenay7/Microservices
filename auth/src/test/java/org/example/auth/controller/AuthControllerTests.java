package org.example.auth.controller;

import org.example.auth.dto.LoginRequestDTO;
import org.example.auth.dto.RegisterRequestDTO;
import org.example.auth.dto.TokenRefreshRequestDTO;
import org.example.auth.entity.RefreshToken;
import org.example.auth.entity.User;
import org.example.auth.exception.TokenNotFoundException;
import org.example.auth.exception.UserAlreadyExistsException;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.example.auth.service.JwtService;
import org.example.auth.service.RefreshTokenService;
import org.example.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.EnumSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTests {

    private static final long REFRESH_TOKEN_DURATION_MS = 604_800_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    private User user;
    private RefreshToken userRefreshToken;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("martin");
        user.setPassword("hashed_password_123");
        user.setRoles(EnumSet.of(AccessRole.USER));

        userRefreshToken = new RefreshToken();
        userRefreshToken.setId(1L);
        userRefreshToken.setToken("mock-refresh-token-uuid");
        userRefreshToken.setExpiryDate(Instant.now().plusMillis(REFRESH_TOKEN_DURATION_MS));
        userRefreshToken.setUser(user);
    }

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        RegisterRequestDTO request = new RegisterRequestDTO("martin", "mySecurePassword123");

        when(userService.createUser(any(RegisterRequestDTO.class))).thenReturn(user);
        when(jwtService.generateToken("martin")).thenReturn("mock-access-token");
        when(refreshTokenService.createRefreshToken("martin")).thenReturn(userRefreshToken);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("mock-refresh-token-uuid"));
    }

    @Test
    void shouldLoginUserSuccessfully() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("martin", "mySecurePassword123");

        when(userService.login(any(LoginRequestDTO.class))).thenReturn(user);
        when(jwtService.generateToken("martin")).thenReturn("mock-access-token");
        when(refreshTokenService.createRefreshToken("martin")).thenReturn(userRefreshToken);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("mock-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("mock-refresh-token-uuid"));
    }

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        // No need to log in first: a refresh request only needs a token the controller can look up.
        when(refreshTokenService.getRefreshToken("mock-refresh-token-uuid"))
                .thenReturn(userRefreshToken);
        when(refreshTokenService.refreshIncomingToken("mock-refresh-token-uuid"))
                .thenReturn(userRefreshToken);
        when(jwtService.generateToken("martin")).thenReturn("new-access-token");

        TokenRefreshRequestDTO refreshRequest = new TokenRefreshRequestDTO("mock-refresh-token-uuid");

        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("mock-refresh-token-uuid"));
    }

    @Test
    void shouldReturnNotFoundWhenRefreshTokenUnknown() throws Exception {
        doThrow(new TokenNotFoundException("Token not found"))
                .when(refreshTokenService).refreshIncomingToken("unknown-token");

        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TokenRefreshRequestDTO("unknown-token"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldLogoutUserSuccessfully() throws Exception {
        when(refreshTokenService.getRefreshToken("mock-refresh-token-uuid"))
                .thenReturn(userRefreshToken);

        TokenRefreshRequestDTO logoutRequest = new TokenRefreshRequestDTO(
                "mock-refresh-token-uuid");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(logoutRequest)))
                .andExpect(status().isOk());

        verify(refreshTokenService).logoutRefreshToken("mock-refresh-token-uuid");
    }


    @Test
    void shouldReturnConflictWhenUserAlreadyExists() throws Exception {
        RegisterRequestDTO request = new RegisterRequestDTO("martin", "mySecurePassword123");

        when(userService.createUser(any(RegisterRequestDTO.class)))
                .thenThrow(new UserAlreadyExistsException("User with username martin already exists"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnUnauthorizedOnBadCredentials() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("martin", "wrong-password");

        when(userService.login(any(LoginRequestDTO.class)))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnNotFoundWhenLoggingOutWithUnknownToken() throws Exception {
        doThrow(new TokenNotFoundException("Token not found"))
                .when(refreshTokenService).logoutRefreshToken("unknown-token");

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new TokenRefreshRequestDTO("unknown-token"))))
                .andExpect(status().isNotFound());
    }
}