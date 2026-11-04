package org.example.auth.dto;

import java.util.Set;

public record UserResponseDTO(String username, Set<String> roles) {
}
