package org.example.order.dto;

public record ProductErrorResponseDTO(int status, String message, long timestamp) {
}
