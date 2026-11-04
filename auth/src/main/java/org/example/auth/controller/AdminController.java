package org.example.auth.controller;

import org.example.auth.dto.UserResponseDTO;
import org.example.auth.entity.User;
import org.example.auth.security.AccessRole;
import org.example.auth.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    // Two layers: the /api/admin/** URL rule in SecurityConfig and this annotation.
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/users/{username}/roles/admin")
    public ResponseEntity<UserResponseDTO> promoteToAdmin(@PathVariable String username,
                                                          Principal principal) {
        User user = userService.promoteToAdmin(username);
        Set<String> roles = user.getRoles().stream()
                .map(AccessRole::getValue)
                .collect(Collectors.toSet());
        // Audit trail: who granted admin to whom
        log.info("User '{}' granted ADMIN role to '{}'",
                principal != null ? principal.getName() : "unknown", username);
        return ResponseEntity.ok(new UserResponseDTO(user.getUsername(), roles));
    }
}