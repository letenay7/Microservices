package org.example.auth.controller;


import jakarta.validation.Valid;
import org.example.auth.dto.ResetPasswordDTO;
import org.example.auth.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/user")   // protected by the /api/user/** rule in SecurityConfig
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // PATCH: partial update of the user. The username comes from the authenticated principal.
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ResetPasswordDTO resetPasswordDTO,
                                               Principal principal) {
        userService.changePassword(principal.getName(), resetPasswordDTO);
        return ResponseEntity.noContent().build();
    }
}
