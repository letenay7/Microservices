package org.example.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequestDTO(
        @NotBlank @Size(min = 3, max = 50) String username,
        // max 72: BCrypt only uses the first 72 bytes of a password
        @NotBlank @Size(min = 8, max = 72) String password) {
}