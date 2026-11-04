package org.example.auth.config;

import io.jsonwebtoken.JwtException;
import org.example.auth.controller.AuthController;
import org.example.auth.dto.LoginRequestDTO;
import org.example.auth.entity.RefreshToken;
import org.example.auth.entity.User;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.example.auth.service.JwtService;
import org.example.auth.service.RefreshTokenService;
import org.example.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumSet;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security tests: SecurityConfig and JwtAuthenticationFilter are REAL here
 * (no addFilters = false). JwtService and UserRepository are mocked, so a token
 * "works" when we stub them to accept it.
 *
 * The /api/admin/** and /api/user/** paths below have no handler in AuthController,
 * so a request that passes security ends in 404. That is how these tests tell
 * "security let it through" (404) from "security blocked it" (401 / 403).
 * Once the promote endpoint exists, point the admin tests at it and expect 200.
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class SecurityConfigTests {

    private static final String ADMIN_PATH = "/api/admin/users/martin/roles/admin";
    private static final String USER_PATH = "/api/user/anything";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        acceptToken("user-token", "martin", EnumSet.of(AccessRole.USER));
        acceptToken("admin-token", "admin", EnumSet.of(AccessRole.ADMIN, AccessRole.USER));
    }


    private void acceptToken(String token, String username, EnumSet<AccessRole> roles) {
        User account = new User();
        account.setUsername(username);
        account.setRoles(roles);

        when(jwtService.extractUsername(token)).thenReturn(username);
        when(jwtService.isTokenValid(token, username)).thenReturn(true);
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(account));
    }

    // ---------- public endpoints ----------

    @Test
    void loginShouldBeReachableWithoutToken() throws Exception {
        User account = new User();
        account.setUsername("martin");
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");

        when(userService.login(any(LoginRequestDTO.class))).thenReturn(account);
        when(jwtService.generateToken("martin")).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken("martin")).thenReturn(refreshToken);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO("martin", "pw"))))
                .andExpect(status().isOk());
    }

    @Test
    void adminLoginShouldBeReachableWithoutToken() throws Exception {
        User admin = new User();
        admin.setUsername("admin");
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");

        when(userService.loginAdmin(any(LoginRequestDTO.class))).thenReturn(admin);
        when(jwtService.generateToken("admin")).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken("admin")).thenReturn(refreshToken);

        mockMvc.perform(post("/api/auth/admin-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequestDTO("admin", "pw"))))
                .andExpect(status().isOk());
    }

    // ---------- authentication ----------

    @Test
    void protectedPathShouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get(USER_PATH))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPathShouldReturn401WhenTokenIsInvalid() throws Exception {
        when(jwtService.extractUsername("bad-token")).thenThrow(new JwtException("invalid token"));

        mockMvc.perform(get(USER_PATH).header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPathShouldReturn401WhenTokenValidButUserNoLongerExists() throws Exception {
        when(jwtService.extractUsername("ghost-token")).thenReturn("ghost");
        when(jwtService.isTokenValid("ghost-token", "ghost")).thenReturn(true);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get(USER_PATH).header("Authorization", "Bearer ghost-token"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- authorization ----------

    @Test
    void userRoleShouldPassUserPath() throws Exception {
        // 404 = passed security, no handler behind it
        mockMvc.perform(get(USER_PATH).header("Authorization", "Bearer user-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void userRoleShouldBeForbiddenOnAdminPath() throws Exception {
        mockMvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoleShouldPassAdminPath() throws Exception {
        // 404 = passed security (and the filter continued the chain), no handler behind it
        mockMvc.perform(get(ADMIN_PATH).header("Authorization", "Bearer admin-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminPathShouldReturn401WithoutToken() throws Exception {
        mockMvc.perform(get(ADMIN_PATH))
                .andExpect(status().isUnauthorized());
    }
}
