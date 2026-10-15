package org.example.auth.dto;

public record ErrorResponseDTO(int status, String message, long timestamp) {}
