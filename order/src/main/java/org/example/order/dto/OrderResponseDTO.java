package org.example.order.dto;

import org.example.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponseDTO(Long orderId, String username, List<OrderItem> items,
                               BigDecimal totalPrice, long timestamp) {}
