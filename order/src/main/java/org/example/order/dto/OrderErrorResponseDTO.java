package org.example.order.dto;

public record OrderErrorResponseDTO(int status, String message, long timestamp) {
}
