package org.example.order.dto;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponseDTO(Long orderId, String username,
                               List<OrderItemDTO> items, BigDecimal totalPrice,
                               long timestamp) {}
