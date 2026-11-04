package org.example.auth.controller;

import org.example.auth.entity.User;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.example.auth.service.JwtService;
import org.example.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumSet;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests only (filters off). Who may call the endpoint is tested in AdminEndpointSecurityTests.
 */
@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTests {

    private static final String PROMOTE_URL = "/api/admin/users/{username}/roles/admin";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // @WebMvcTest picks up JwtAuthenticationFilter, which needs these two
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void shouldPromoteUserAndReturnSafeView() throws Exception {
        User promoted = new User();
        promoted.setUsername("martin");
        promoted.setPassword("hashed_password_123");
        promoted.setRoles(EnumSet.of(AccessRole.USER, AccessRole.ADMIN));

        when(userService.promoteToAdmin("martin")).thenReturn(promoted);

        mockMvc.perform(put(PROMOTE_URL, "martin").principal(() -> "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("martin"))
                .andExpect(jsonPath("$.roles", hasItem("ADMIN")))
                .andExpect(jsonPath("$.password").doesNotExist());

        verify(userService).promoteToAdmin("martin");
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        when(userService.promoteToAdmin("ghost"))
                .thenThrow(new UsernameNotFoundException("User with username ghost not found"));

        mockMvc.perform(put(PROMOTE_URL, "ghost").principal(() -> "admin"))
                .andExpect(status().isNotFound());
    }
}
