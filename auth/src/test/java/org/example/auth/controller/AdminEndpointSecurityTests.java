package org.example.auth.controller;

import org.example.auth.config.SecurityConfig;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumSet;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may promote? SecurityConfig and JwtAuthenticationFilter are REAL here.
 * The key assertion is that a non-admin never reaches the service (no privilege escalation).
 */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminEndpointSecurityTests {

    private static final String PROMOTE_URL = "/api/admin/users/martin/roles/admin";

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

        User promoted = new User();
        promoted.setUsername("martin");
        promoted.setRoles(EnumSet.of(AccessRole.USER, AccessRole.ADMIN));
        when(userService.promoteToAdmin("martin")).thenReturn(promoted);
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
    void adminShouldBeAbleToPromote() throws Exception {
        mockMvc.perform(put(PROMOTE_URL).header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());

        verify(userService).promoteToAdmin("martin");
    }

    @Test
    void regularUserShouldBeForbiddenAndServiceNeverCalled() throws Exception {
        mockMvc.perform(put(PROMOTE_URL).header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());

        verify(userService, never()).promoteToAdmin(anyString());
    }

    @Test
    void anonymousShouldGet401AndServiceNeverCalled() throws Exception {
        mockMvc.perform(put(PROMOTE_URL))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).promoteToAdmin(anyString());
    }

    // ---------- demotion ----------

    @Test
    void adminShouldBeAbleToDemote() throws Exception {
        User demoted = new User();
        demoted.setUsername("martin");
        demoted.setRoles(EnumSet.of(AccessRole.USER));
        when(userService.demoteFromAdmin("martin")).thenReturn(demoted);

        mockMvc.perform(delete(PROMOTE_URL).header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());

        verify(userService).demoteFromAdmin("martin");
    }

    @Test
    void regularUserShouldBeForbiddenFromDemotingAndServiceNeverCalled() throws Exception {
        mockMvc.perform(delete(PROMOTE_URL).header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden());

        verify(userService, never()).demoteFromAdmin(anyString());
    }

    @Test
    void anonymousShouldGet401OnDemotionAndServiceNeverCalled() throws Exception {
        mockMvc.perform(delete(PROMOTE_URL))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).demoteFromAdmin(anyString());
    }
}
