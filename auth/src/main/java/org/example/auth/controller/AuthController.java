package org.example.auth.controller;

import org.example.auth.dto.RegisterRequestDTO;
import org.example.auth.entity.User;
import org.example.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepository userRepository;
    private  final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/status")
    public String getStatus() {
        return "Auth Service is up and running!";
    }

    @PostMapping("/register")
    public String registerUser(@RequestBody RegisterRequestDTO registerRequestDTO) {
        User user = new User();
        user.setUsername(registerRequestDTO.username());
        user.setPassword(passwordEncoder.encode(registerRequestDTO.password()));
        userRepository.save(user);
        return "User " +
                user.getUsername() +
                " has been successfully registered";
    }
}