package org.example.auth.service;

import org.example.auth.dto.LoginRequestDTO;
import org.example.auth.dto.RegisterRequestDTO;
import org.example.auth.dto.ResetPasswordDTO;
import org.example.auth.entity.User;
import org.example.auth.exception.LastAdminException;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.EnumSet;
import java.util.List;
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

    @Mock
    private RefreshTokenService refreshTokenService;

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
        when(passwordEncoder.encode("mySecurePassword123")).thenReturn("hashed_password_123");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.createUser(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
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

        verify(userRepository, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void createUser_shouldThrowUserAlreadyExistsWhenInsertHitsUniqueConstraint() {
        RegisterRequestDTO request = new RegisterRequestDTO("martin", "mySecurePassword123");

        // another request registered "martin" between the existence check and the insert
        when(userRepository.existsByUsername("martin")).thenReturn(false);
        when(passwordEncoder.encode("mySecurePassword123"))
                .thenReturn("hashed_password_123");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThrows(UserAlreadyExistsException.class, () -> userService.createUser(request));
    }

    // ---------- login ----------

    @Test
    void login_shouldReturnUserWhenCredentialsAreValid() {
        LoginRequestDTO request = new LoginRequestDTO("martin", "mySecurePassword123");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(regularUser));
        when(passwordEncoder.matches("mySecurePassword123", "hashed_password_123")).thenReturn(true);

        assertSame(regularUser, userService.login(request));
    }

    @Test
    void login_shouldThrowBadCredentialsWhenPasswordIsWrong() {
        LoginRequestDTO request = new LoginRequestDTO("martin", "wrong-password");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(regularUser));
        when(passwordEncoder.matches("wrong-password", "hashed_password_123")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> userService.login(request));
    }

    @Test
    void login_shouldThrowBadCredentialsWhenUserDoesNotExist() {
        LoginRequestDTO request = new LoginRequestDTO("ghost", "whatever");

        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userService.login(request));
        verifyNoInteractions(passwordEncoder);
    }

    // ---------- changePassword ----------

    @Test
    void changePassword_shouldHashAndSaveNewPassword() {
        ResetPasswordDTO request = new ResetPasswordDTO("oldPassword123", "newPassword456");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(regularUser));
        when(passwordEncoder.matches("oldPassword123", "hashed_password_123")).thenReturn(true);
        when(passwordEncoder.matches("newPassword456", "hashed_password_123")).thenReturn(false);
        when(passwordEncoder.encode("newPassword456")).thenReturn("hashed_new_password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.changePassword("martin", request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed_new_password", captor.getValue().getPassword(),
                "the new password must be stored hashed, not raw");
        assertSame(captor.getValue(), result);
        verify(refreshTokenService).revokeAllTokens("martin");
    }

    @Test
    void changePassword_shouldThrowWhenCurrentPasswordIsWrong() {
        ResetPasswordDTO request = new ResetPasswordDTO("wrongPassword", "newPassword456");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(regularUser));
        when(passwordEncoder.matches("wrongPassword", "hashed_password_123")).thenReturn(false);

        assertThrows(PasswordModificationException.class, () -> userService.changePassword("martin", request));
        verifyNoInteractions(refreshTokenService);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_shouldThrowWhenNewPasswordSameAsCurrent() {
        ResetPasswordDTO request = new ResetPasswordDTO("samePassword1", "samePassword1");

        when(userRepository.findByUsername("martin")).thenReturn(Optional.of(regularUser));
        // raw password first, stored hash second
        when(passwordEncoder.matches("samePassword1", "hashed_password_123")).thenReturn(true);

        assertThrows(PasswordModificationException.class, () -> userService.changePassword("martin", request));
        verifyNoInteractions(refreshTokenService);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void changePassword_shouldThrowWhenUserDoesNotExist() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        // assumes getUserByUsername throws UsernameNotFoundException
        assertThrows(UsernameNotFoundException.class,
                () -> userService.changePassword("ghost", new ResetPasswordDTO("old", "new")));

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(refreshTokenService);
        verify(userRepository, never()).save(any(User.class));
    }

    // ---------- promoteToAdmin ----------

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

    // ---------- demoteFromAdmin ----------

    @Test
    void demoteFromAdmin_shouldRemoveAdminRoleWhenAnotherAdminExists() {
        User secondAdmin = new User();
        secondAdmin.setUsername("second-admin");
        secondAdmin.setPassword("hashed_admin_password");
        secondAdmin.setRoles(EnumSet.of(AccessRole.ADMIN, AccessRole.USER));

        when(userRepository.findAllForUpdate()).thenReturn(List.of(adminUser, secondAdmin, regularUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.demoteFromAdmin("admin");

        assertEquals(EnumSet.of(AccessRole.USER), result.getRoles(), "only ADMIN is removed, USER stays");
        verify(userRepository).save(adminUser);
    }

    @Test
    void demoteFromAdmin_shouldRefuseToRemoveTheLastAdmin() {
        when(userRepository.findAllForUpdate()).thenReturn(List.of(adminUser, regularUser));

        assertThrows(LastAdminException.class, () -> userService.demoteFromAdmin("admin"));

        assertEquals(EnumSet.of(AccessRole.ADMIN, AccessRole.USER), adminUser.getRoles());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void demoteFromAdmin_shouldBeANoOpForAUserWhoIsNotAnAdmin() {
        when(userRepository.findAllForUpdate()).thenReturn(List.of(adminUser, regularUser));

        User result = userService.demoteFromAdmin("martin");

        assertSame(regularUser, result);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void demoteFromAdmin_shouldThrowWhenUserDoesNotExist() {
        when(userRepository.findAllForUpdate()).thenReturn(List.of(adminUser));

        assertThrows(UsernameNotFoundException.class, () -> userService.demoteFromAdmin("ghost"));

        verify(userRepository, never()).save(any(User.class));
    }
}