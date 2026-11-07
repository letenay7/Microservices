package org.example.auth.controller;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.example.auth.dto.LoginRequestDTO;
import org.example.auth.entity.RefreshToken;
import org.example.auth.entity.User;
import org.example.auth.repository.UserRepository;
import org.example.auth.service.JwtService;
import org.example.auth.service.RefreshTokenService;
import org.example.auth.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Login attempts must leave an audit trail, with the client IP and without the password. */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoginAuditControllerTests {

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

    private final Logger auditLogger = (Logger) LoggerFactory.getLogger("auth.audit");
    private ListAppender<ILoggingEvent> appender;
    
    private final String IP = "203.0.113.9" ;

    @BeforeEach
    void attachAppender() {
        appender = new ListAppender<>();
        appender.start();
        auditLogger.setLevel(Level.INFO);
        auditLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        auditLogger.detachAppender(appender);
    }

    private RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    @Test
    void failedLoginShouldBeLoggedWithUsernameAndClientIpButNotThePassword() throws Exception {
        when(userService.login(any(LoginRequestDTO.class)))
                .thenThrow(new BadCredentialsException("Invalid username or password"));

        mockMvc.perform(post("/api/auth/login")
                        .with(fromIp(IP))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"martin\",\"password\":\"supersecret-password\"}"))
                .andExpect(status().isUnauthorized());          // the response is unchanged

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.getFirst();
        String message = event.getFormattedMessage();
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(message.contains("event=login_failed"));
        assertTrue(message.contains("username=\"martin\""));
        assertTrue(message.contains(IP));
        assertFalse(message.contains("supersecret-password"), "the password must never be logged");
    }

    @Test
    void successfulLoginShouldBeLoggedAtInfo() throws Exception {
        User user = new User();
        user.setUsername("martin");
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken("refresh-token");

        when(userService.login(any(LoginRequestDTO.class))).thenReturn(user);
        when(jwtService.generateToken("martin")).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken("martin")).thenReturn(refreshToken);

        mockMvc.perform(post("/api/auth/login")
                        .with(fromIp(IP))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"martin\",\"password\":\"mySecurePassword123\"}"))
                .andExpect(status().isOk());

        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.getFirst();
        assertEquals(Level.INFO, event.getLevel());
        assertTrue(event.getFormattedMessage().contains("event=login_success"));
        assertTrue(event.getFormattedMessage().contains(IP));
    }

    @Test
    void invalidRequestsShouldNotBeLoggedAsFailedLogins() throws Exception {
        // a blank password is rejected with 400 before any authentication takes place
        mockMvc.perform(post("/api/auth/login")
                        .with(fromIp(IP))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"martin\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(appender.list.isEmpty());
    }
}