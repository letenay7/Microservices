package org.example.auth.controller;

import org.example.auth.config.SecurityConfig;
import org.example.auth.dto.ResetPasswordDTO;
import org.example.auth.entity.User;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.example.auth.service.JwtService;
import org.example.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumSet;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** SecurityConfig and JwtAuthenticationFilter are REAL here. */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserEndpointSecurityTests {

    private static final String PASSWORD_URL = "/api/user/password";
    private static final String BODY = "{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"newPassword456\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

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

    @Test
    void userShouldChangeOwnPasswordUsingIdentityFromToken() throws Exception {
        mockMvc.perform(patch(PASSWORD_URL)
                        .header("Authorization", "Bearer user-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(org.mockito.ArgumentMatchers.eq("martin"), any(ResetPasswordDTO.class));
    }

    @Test
    void adminShouldAlsoBeAbleToChangeOwnPassword() throws Exception {
        mockMvc.perform(patch(PASSWORD_URL)
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isNoContent());

        verify(userService).changePassword(org.mockito.ArgumentMatchers.eq("admin"), any(ResetPasswordDTO.class));
    }

    @Test
    void anonymousShouldGet401AndServiceNeverCalled() throws Exception {
        mockMvc.perform(patch(PASSWORD_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).changePassword(anyString(), any(ResetPasswordDTO.class));
    }
}
