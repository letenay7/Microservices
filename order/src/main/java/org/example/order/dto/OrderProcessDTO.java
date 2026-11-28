package org.example.order.dto;

import jakarta.validation.constraints.NotNull;
import org.example.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.List;

public record OrderProcessDTO(@NotNull List<OrderItem> orderItems,
                              @NotNull BigDecimal totalPrice) {
}
