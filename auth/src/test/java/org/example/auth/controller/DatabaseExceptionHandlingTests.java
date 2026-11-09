package org.example.auth.controller;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import org.example.auth.dto.RegisterRequestDTO;
import org.example.auth.entity.User;
import org.example.auth.repository.UserRepository;
import org.example.auth.service.JwtService;
import org.example.auth.service.RefreshTokenService;
import org.example.auth.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks the GlobalExceptionHandler mappings for exceptions that escape the service layer. */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class DatabaseExceptionHandlingTests {

    private static final String REGISTER_BODY = "{\"username\":\"martin\",\"password\":\"mySecurePassword123\"}";

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
    void dataIntegrityViolationShouldBecomeConflictWithoutLeakingDetails() throws Exception {
        when(userService.createUser(any(RegisterRequestDTO.class))).thenThrow(
                new DataIntegrityViolationException(
                        "ERROR: duplicate key value violates unique constraint \"uk_users_username\""));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("uk_users_username"))))
                .andExpect(content().string(not(containsString("duplicate key"))));
    }

    @Test
    void constraintViolationShouldBecomeBadRequestNamingTheField() throws Exception {
        // a real violation set: blank username on the entity
        User invalid = new User();
        invalid.setUsername("  ");
        invalid.setPassword("hash");
        Set<ConstraintViolation<User>> violations =
                Validation.buildDefaultValidatorFactory().getValidator().validate(invalid);

        when(userService.createUser(any(RegisterRequestDTO.class)))
                .thenThrow(new ConstraintViolationException(violations));

        // message text is locale dependent, so only the field name is asserted
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REGISTER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("username")));
    }
}