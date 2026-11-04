package org.example.auth.controller;

import org.example.auth.dto.ResetPasswordDTO;
import org.example.auth.exception.PasswordModificationException;
import org.example.auth.repository.UserRepository;
import org.example.auth.service.JwtService;
import org.example.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web-layer tests only (filters off). Who may call the endpoint is tested in UserEndpointSecurityTests. */
@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTests {

    private static final String PASSWORD_URL = "/api/user/password";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    // @WebMvcTest picks up JwtAuthenticationFilter, which needs these two
    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void shouldChangePasswordForTheAuthenticatedUser() throws Exception {
        ResetPasswordDTO request = new ResetPasswordDTO("oldPassword123", "newPassword456");

        mockMvc.perform(patch(PASSWORD_URL)
                        .principal(() -> "martin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNoContent());

        // the username must come from the principal, and the body must be bound correctly
        ArgumentCaptor<ResetPasswordDTO> captor = ArgumentCaptor.forClass(ResetPasswordDTO.class);
        verify(userService).changePassword(eq("martin"), captor.capture());
        assertEquals("oldPassword123", captor.getValue().currentPassword());
        assertEquals("newPassword456", captor.getValue().newPassword());
    }

    @Test
    void shouldReturnBadRequestWhenPasswordRejected() throws Exception {
        when(userService.changePassword(eq("martin"), any(ResetPasswordDTO.class)))
                .thenThrow(new PasswordModificationException("Current password is incorrect"));

        // needs a PasswordModificationException -> 400 mapping in GlobalExceptionHandler
        mockMvc.perform(patch(PASSWORD_URL)
                        .principal(() -> "martin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordDTO("wrong", "newPassword456"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundWhenUserNoLongerExists() throws Exception {
        when(userService.changePassword(eq("ghost"), any(ResetPasswordDTO.class)))
                .thenThrow(new UsernameNotFoundException("User with username ghost not found"));

        mockMvc.perform(patch(PASSWORD_URL)
                        .principal(() -> "ghost")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ResetPasswordDTO("old", "new"))))
                .andExpect(status().isNotFound());
    }
}
