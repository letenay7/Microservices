package org.example.auth.bootstrap;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.example.auth.entity.User;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plain unit tests: repository and encoder mocked, real validator, real AdminBootstrap logic. */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private void runWith(String username, String password) {
        new AdminBootstrap(userRepository, passwordEncoder, validator, username, password)
                .run(new DefaultApplicationArguments());
    }

    private User existingUser(EnumSet<AccessRole> roles) {
        User user = new User();
        user.setUsername("admin");
        user.setPassword("existing-hash");
        user.setRoles(roles);
        return user;
    }

    // ---------- configuration ----------

    @Test
    void shouldDoNothingWhenNotConfigured() {
        assertDoesNotThrow(() -> runWith("", ""));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void shouldFailStartupWhenOnlyOneValueIsSet() {
        assertThrows(IllegalStateException.class, () -> runWith("admin", ""));
        assertThrows(IllegalStateException.class, () -> runWith("", "StrongPassword123"));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void shouldFailStartupWithWeakPasswordWithoutLeakingIt() {
        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> runWith("admin", "x1y2z3"));

        assertFalse(exception.getMessage().contains("x1y2z3"), "the password must not appear in the message");
        verifyNoInteractions(userRepository, passwordEncoder);
    }

    // ---------- creating the admin ----------

    @Test
    void shouldCreateAdminWithBothRolesAndHashedPassword() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("StrongPassword123")).thenReturn("hashed_admin_password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        runWith("admin", "StrongPassword123");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertEquals("admin", saved.getUsername());
        assertEquals("hashed_admin_password", saved.getPassword(), "password must be stored hashed");
        assertEquals(Set.of(AccessRole.ADMIN, AccessRole.USER), saved.getRoles());
    }

    @Test
    void shouldNotFailStartupWhenUsernameIsTakenConcurrently() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("StrongPassword123")).thenReturn("hashed_admin_password");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertDoesNotThrow(() -> runWith("admin", "StrongPassword123"));
    }

    // ---------- existing accounts are never touched ----------

    @Test
    void shouldNotPromoteOrModifyAnExistingRegularUser() {
        // someone registered the configured username first: they must NOT become an admin
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(existingUser(EnumSet.of(AccessRole.USER))));

        runWith("admin", "StrongPassword123");

        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void shouldDoNothingWhenTheAdminAlreadyExists() {
        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(existingUser(EnumSet.of(AccessRole.ADMIN, AccessRole.USER))));

        runWith("admin", "StrongPassword123");

        verify(userRepository, never()).save(any(User.class));
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }
}