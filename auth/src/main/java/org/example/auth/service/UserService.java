package org.example.auth.service;

import org.example.auth.dto.LoginRequestDTO;
import org.example.auth.dto.RegisterRequestDTO;
import org.example.auth.dto.ResetPasswordDTO;
import org.example.auth.entity.User;
import org.example.auth.exception.PasswordModificationException;
import org.example.auth.exception.UserAlreadyExistsException;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    public User createUser(RegisterRequestDTO registerRequestDTO) {
        String username = registerRequestDTO.username();
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("User with username " + username + " already exists");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(registerRequestDTO.password()));

        // set default role AccessRole.USER
        user.setRoles(EnumSet.of(AccessRole.USER));
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyExistsException("User with username " + username + " already exists");
        }
    }

    private User getUserByUsername(String username) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isPresent()) {
            return userOptional.get();
        }
        throw new UsernameNotFoundException("User with username " + username + " not found");
    }

    public User login(LoginRequestDTO loginRequestDTO) {
        String username = loginRequestDTO.username();
        User user = getUserByUsername(username);
        if (!passwordEncoder.matches(loginRequestDTO.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        return user;
    }

    private boolean isAdmin(User user) {
        return user.getRoles().stream()
                .anyMatch(role -> role.getValue().equals("ADMIN"));
    }

    public User loginAdmin(LoginRequestDTO loginRequestDTO) {
        User user = getUserByUsername(loginRequestDTO.username());
        if (!passwordEncoder.matches(loginRequestDTO.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid username or password");
        }
        if (!isAdmin(user)) {
            throw new AccessDeniedException("Access Denied");
        }
        return user;
    }


    @Transactional
    public User changePassword(String username, ResetPasswordDTO dto) {
        User user = getUserByUsername(username);

        // matches(rawPassword, encodedPassword): raw first, stored hash second
        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new PasswordModificationException("Current password is incorrect");
        }
        if (passwordEncoder.matches(dto.newPassword(), user.getPassword())) {
            throw new PasswordModificationException(
                    "New password cannot be the same as the old password");
        }
        user.setPassword(passwordEncoder.encode(dto.newPassword()));
         User saved = userRepository.save(user);
         refreshTokenService.revokeAllTokens(username);
         return saved;
    }

    @Transactional
    public User promoteToAdmin(String username) {
        User user = getUserByUsername(username);
        if (!user.addRole(AccessRole.ADMIN)) {
            return user;
        }
        return userRepository.save(user);
    }
}
