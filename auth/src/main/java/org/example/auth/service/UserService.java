package org.example.auth.service;

import org.example.auth.dto.LoginRequestDTO;
import org.example.auth.dto.RegisterRequestDTO;
import org.example.auth.entity.User;
import org.example.auth.exception.UserAlreadyExistsException;
import org.example.auth.repository.UserRepository;
import org.example.auth.security.AccessRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Optional;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
         return userRepository.save(user);
    }

    public User login(LoginRequestDTO loginRequestDTO) {
        String username = loginRequestDTO.username();
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (!passwordEncoder.matches(loginRequestDTO.password(), user.getPassword())) {
                throw new BadCredentialsException("Invalid username or password");
            }
            return user;
        }
        throw new BadCredentialsException("Invalid username or password");
    }

    public User loginAdmin(LoginRequestDTO loginRequestDTO) {
        Optional<User> userOptional = userRepository.findByUsername(loginRequestDTO.username());
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (!passwordEncoder.matches(loginRequestDTO.password(), user.getPassword())) {
                throw new BadCredentialsException("Invalid username or password");
            }
            boolean isAdmin = user.getRoles().stream()
                    .anyMatch(role -> role.getValue().equals("ADMIN"));
            if (!isAdmin) {
                throw new AccessDeniedException("Access Denied");
            }
            return user;
        }
        throw new UsernameNotFoundException(
                "User with username " + loginRequestDTO.username() + " not found");
    }
}
