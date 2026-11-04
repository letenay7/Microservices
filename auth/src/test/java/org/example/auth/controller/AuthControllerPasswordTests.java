package org.example.auth.controller;

import org.example.auth.dto.ResetPasswordDTO;
import org.example.auth.entity.User;
import org.example.auth.exception.PasswordModificationException;
import org.example.auth.repository.UserRepository;
import org.example.auth.service.JwtService;
import org.example.auth.service.RefreshTokenService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the password update endpoint.
 * controller calls userService.resetUserPassword(dto) and returns 200 on success.
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerPasswordTests {

    private static final String PASSWORD_URL = "/api/auth/password";

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

    @Test
    void shouldUpdatePasswordSuccessfully() throws Exception {
        ResetPasswordDTO request = new ResetPasswordDTO("martin", "newPassword456");

        User updated = new User();
        updated.setUsername("martin");
        when(userService.resetUserPassword(any(ResetPasswordDTO.class))).thenReturn(updated);

        mockMvc.perform(patch(PASSWORD_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // the JSON body must be bound correctly and passed on to the service
        ArgumentCaptor<ResetPasswordDTO> captor = ArgumentCaptor.forClass(ResetPasswordDTO.class);
        verify(userService).resetUserPassword(captor.capture());
        assertEquals("martin", captor.getValue().username());
        assertEquals("newPassword456", captor.getValue().newPassword());
    }

    @Test
    void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        ResetPasswordDTO request = new ResetPasswordDTO("ghost", "newPassword456");

        when(userService.resetUserPassword(any(ResetPasswordDTO.class)))
                .thenThrow(new UsernameNotFoundException("User with username ghost not found"));

        mockMvc.perform(patch(PASSWORD_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldRejectWhenNewPasswordSameAsOld() throws Exception {
        ResetPasswordDTO request = new ResetPasswordDTO("martin", "mySecurePassword123");

        when(userService.resetUserPassword(any(ResetPasswordDTO.class)))
                .thenThrow(new PasswordModificationException(
                        "New password cannot be the same as the old password"));

        mockMvc.perform(patch(PASSWORD_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
