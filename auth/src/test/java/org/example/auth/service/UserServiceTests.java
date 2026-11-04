package org.example.auth.service;

import org.example.auth.dto.LoginRequestDTO;
import org.example.auth.dto.RegisterRequestDTO;
import org.example.auth.dto.ResetPasswordDTO;
import org.example.auth.entity.User;
import org.example.auth.exception.PasswordModificationException;
import org.example.auth.exception.UserAlreadyExistsException;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Plain unit tests: no Spring context. UserRepository and PasswordEncoder are mocked,
 * and the real UserService logic runs.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User regularUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        regularUser = new User();
        regularUser.setId(1L);
        regularUser.setUsername("martin");
        regularUser.setPassword("hashed_password_123");
        regularUser.setRoles(EnumSet.of(AccessRole.USER));

        adminUser = new User();
        adminUser.setId(2L);
        adminUser.setUsername("admin");
        adminUser.setPassword("hashed_admin_password");
        adminUser.setRoles(EnumSet.of(AccessRole.ADMIN, AccessRole.USER));
    }

    // ---------- createUser ----------

    @Test
    void createUser_shouldHashPasswordAssignUserRoleAndSave() {
        RegisterRequestDTO request = new RegisterRequestDTO("martin", "mySecurePassword123");

        when(userRepository.existsByUsername("martin")).thenReturn(false);
        when(passwordEncoder.encode("mySecurePassword123"))
                .thenReturn("hashed_password_123");
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.createUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertEquals("martin", saved.getUsername());
        assertEquals("hashed_password_123", saved.getPassword(), "password must be stored hashed");
        assertEquals(EnumSet.of(AccessRole.USER), saved.getRoles(), "new users get only the USER role");
        assertSame(saved, result);
    }

    @Test
    void createUser_shouldThrowWhenUsernameAlreadyExists() {
        RegisterRequestDTO request = new RegisterRequestDTO("martin", "mySecurePassword123");

        when(userRepository.existsByUsername("martin")).thenReturn(true);

        assertThrows(UserAlreadyExistsException.class, () -> userService.createUser(request));

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    // ---------- login ----------

    @Test
    void login_shouldReturnUserWhenCredentialsAreValid() {
        LoginRequestDTO request = new LoginRequestDTO("martin", "mySecurePassword123");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(regularUser));
        when(passwordEncoder.matches("mySecurePassword123", "hashed_password_123"))
                .thenReturn(true);

        assertSame(regularUser, userService.login(request));
    }

    @Test
    void login_shouldThrowBadCredentialsWhenPasswordIsWrong() {
        LoginRequestDTO request = new LoginRequestDTO("martin", "wrong-password");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(regularUser));
        when(passwordEncoder.matches("wrong-password", "hashed_password_123"))
                .thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> userService.login(request));
    }

    @Test
    void login_shouldThrowBadCredentialsWhenUserDoesNotExist() {
        LoginRequestDTO request = new LoginRequestDTO("ghost", "whatever");

        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userService.login(request));
        verifyNoInteractions(passwordEncoder);
    }

    // ---------- loginAdmin ----------

    @Test
    void loginAdmin_shouldReturnUserWhenAdminWithValidPassword() {
        LoginRequestDTO request = new LoginRequestDTO("admin", "StrongPassword123");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("StrongPassword123", "hashed_admin_password"))
                .thenReturn(true);

        assertSame(adminUser, userService.loginAdmin(request));
    }

    @Test
    void loginAdmin_shouldThrowAccessDeniedWhenUserIsNotAdmin() {
        LoginRequestDTO request = new LoginRequestDTO("martin", "mySecurePassword123");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(regularUser));
        when(passwordEncoder.matches("mySecurePassword123", "hashed_password_123")).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> userService.loginAdmin(request));
    }

    @Test
    void loginAdmin_shouldThrowBadCredentialsWhenPasswordIsWrong() {
        LoginRequestDTO request = new LoginRequestDTO("admin", "wrong-password");

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(passwordEncoder.matches("wrong-password", "hashed_admin_password")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> userService.loginAdmin(request));
    }

    @Test
    void loginAdmin_shouldNotRevealRoleWhenNonAdminHasWrongPassword() {
        // Password is checked before the role, so a wrong password must yield
        // BadCredentials, never AccessDenied.
        LoginRequestDTO request = new LoginRequestDTO("martin", "wrong-password");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(regularUser));
        when(passwordEncoder.matches("wrong-password", "hashed_password_123")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> userService.loginAdmin(request));
    }

    @Test
    void loginAdmin_shouldThrowUsernameNotFoundWhenUserDoesNotExist() {
        LoginRequestDTO request = new LoginRequestDTO("ghost", "whatever");

        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userService.loginAdmin(request));
        verifyNoInteractions(passwordEncoder);
    }

    // ---------- password update ----------

    @Test
    void resetUserPassword_shouldHashAndSaveNewPassword() {
        ResetPasswordDTO request = new ResetPasswordDTO("martin", "newPassword456");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(regularUser));
        when(passwordEncoder.matches("newPassword456", "hashed_password_123")).thenReturn(false);
        when(passwordEncoder.encode("newPassword456")).thenReturn("hashed_new_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.resetUserPassword(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed_new_password", captor.getValue().getPassword(),
                "the new password must be stored hashed, not raw");
        assertEquals("martin", captor.getValue().getUsername());
        assertSame(captor.getValue(), result);
    }

    @Test
    void resetUserPassword_shouldThrowWhenNewPasswordMatchesOldOne() {
        ResetPasswordDTO request = new ResetPasswordDTO("martin", "mySecurePassword123");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(regularUser));
        // raw new password first, stored hash second
        when(passwordEncoder.matches("mySecurePassword123", "hashed_password_123")).thenReturn(true);

        assertThrows(PasswordModificationException.class, () -> userService.resetUserPassword(request));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetUserPassword_shouldThrowWhenUserDoesNotExist() {
        ResetPasswordDTO request = new ResetPasswordDTO("ghost", "newPassword456");

        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // assumes getUserByUsername throws UsernameNotFoundException; adjust if it throws something else
        assertThrows(UsernameNotFoundException.class, () -> userService.resetUserPassword(request));

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any(User.class));
    }

    // ---------- promote to admin ----------
    @Test
    void promoteToAdmin_shouldAddAdminRoleAndSave() {
        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(regularUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.promoteToAdmin("martin");

        assertEquals(EnumSet.of(AccessRole.USER, AccessRole.ADMIN), result.getRoles(),
                "ADMIN is added, USER is kept");
        verify(userRepository).save(regularUser);
    }

    @Test
    void promoteToAdmin_shouldNotSaveWhenAlreadyAdmin() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        User result = userService.promoteToAdmin("admin");

        assertSame(adminUser, result);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void promoteToAdmin_shouldThrowWhenUserDoesNotExist() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // assumes getUserByUsername throws UsernameNotFoundException, as in the resetUserPassword test
        assertThrows(UsernameNotFoundException.class, () -> userService.promoteToAdmin("ghost"));

        verify(userRepository, never()).save(any(User.class));
    }
}
