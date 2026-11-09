package org.example.auth.security;

import io.jsonwebtoken.JwtException;
import org.example.auth.entity.User;
import org.example.auth.repository.UserRepository;
import org.example.auth.service.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests: the filter is called directly, so
 * MockMvc and filter ordering are out of the picture.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTests {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void shouldAuthenticateWithRoleAuthoritiesWhenTokenIsValid() throws Exception {
        User account = new User();
        account.setUsername("martin");
        account.setRoles(EnumSet.of(AccessRole.USER));

        when(jwtService.extractUsername("valid-token")).thenReturn("martin");
        when(jwtService.isTokenValid("valid-token", "martin")).thenReturn(true);
        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(account));

        filter.doFilter(requestWithToken("valid-token"), response, chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertNotNull(authentication, "authentication should be set");
        assertTrue(authentication.isAuthenticated(), "token must be an authenticated one");
        assertEquals("martin", authentication.getName());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
        assertNotNull(chain.getRequest(), "filter must continue the chain");
    }

    @Test
    void shouldContinueChainWithoutAuthenticationWhenNoHeader() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest(), "filter must continue the chain");
    }

    @Test
    void shouldStayUnauthenticatedWhenTokenIsInvalid() throws Exception {
        when(jwtService.extractUsername("bad-token")).thenThrow(new JwtException("invalid"));

        filter.doFilter(requestWithToken("bad-token"), response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest(), "filter must continue the chain");
    }

    @Test
    void shouldStayUnauthenticatedWhenTokenValidationFails() throws Exception {
        when(jwtService.extractUsername("expired-token")).thenReturn("martin");
        when(jwtService.isTokenValid("expired-token", "martin")).thenReturn(false);

        filter.doFilter(requestWithToken("expired-token"), response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepository, never()).findByUsername("martin");
        assertNotNull(chain.getRequest());
    }

    @Test
    void shouldStayUnauthenticatedWhenUserNoLongerExists() throws Exception {
        when(jwtService.extractUsername("ghost-token")).thenReturn("ghost");
        when(jwtService.isTokenValid("ghost-token", "ghost")).thenReturn(true);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        filter.doFilter(requestWithToken("ghost-token"), response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
    }
}
