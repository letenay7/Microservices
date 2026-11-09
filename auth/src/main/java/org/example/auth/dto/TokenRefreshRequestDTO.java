package org.example.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequestDTO(@NotBlank String refreshToken) {
}
