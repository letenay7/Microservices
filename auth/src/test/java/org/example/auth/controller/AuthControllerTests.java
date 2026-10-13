package org.example.auth.controller;

import org.example.auth.dto.LoginRequestDTO;
import org.example.auth.dto.RegisterRequestDTO;
import org.example.auth.entity.User;
import org.example.auth.repository.UserRepository;
import org.example.auth.service.JwtService;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void shouldRegisterUserSuccessfully() throws Exception {
        // 1. Arrange: Create our mock input data using our record DTO
        RegisterRequestDTO request = new RegisterRequestDTO("martin", "mySecurePassword123");

        // Tell our mock password encoder what to return when asked to hash the password
        when(passwordEncoder.encode("mySecurePassword123")).thenReturn("hashed_password_123");

        // 2. Act & Assert: Simulate sending a POST request to /auth/register
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string(
                        "User " + request.username() + " has been successfully registered"));
    }

    @Test
    void shouldLoginUserSuccessfully() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("martin", "mySecurePassword123");

        User mockUser = new User();
        mockUser.setUsername("martin");
        mockUser.setPassword("hashed_password_123");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("mySecurePassword123", "hashed_password_123")).thenReturn(true);
        when(jwtService.generateToken("martin")).thenReturn("mock-jwt-token-xyz");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("mock-jwt-token-xyz"));
    }
}