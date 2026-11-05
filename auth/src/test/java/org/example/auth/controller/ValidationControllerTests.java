package org.example.auth.controller;

import org.example.auth.dto.LoginRequestDTO;
import org.example.auth.dto.RegisterRequestDTO;
import org.example.auth.dto.ResetPasswordDTO;
import org.example.auth.repository.UserRepository;
import org.example.auth.service.JwtService;
import org.example.auth.service.RefreshTokenService;
import org.example.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Invalid input must be rejected with 400 BEFORE the service is called.
 * These fail if @Valid is missing on a @RequestBody parameter.
 */
@WebMvcTest({AuthController.class, UserController.class})
@AutoConfigureMockMvc(addFilters = false)
class ValidationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void registerShouldRejectShortPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"martin\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).createUser(any(RegisterRequestDTO.class));
    }

    @Test
    void registerShouldRejectBlankUsername() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"  \",\"password\":\"mySecurePassword123\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).createUser(any(RegisterRequestDTO.class));
    }

    @Test
    void loginShouldRejectBlankPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"martin\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).login(any(LoginRequestDTO.class));
    }

    @Test
    void changePasswordShouldRejectWeakNewPassword() throws Exception {
        mockMvc.perform(patch("/api/user/password")
                        .principal(() -> "martin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"oldPassword123\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(anyString(), any(ResetPasswordDTO.class));
    }

    @Test
    void refreshTokenShouldRejectBlankToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void refreshTokenShouldRejectMissingToken() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void logoutShouldRejectBlankToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\" \"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(refreshTokenService);
    }
}