package org.example.order.dto;

import org.example.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.List;

public record OrderProcessDTO(List<OrderItem> orderItems, BigDecimal totalPrice) {
}
