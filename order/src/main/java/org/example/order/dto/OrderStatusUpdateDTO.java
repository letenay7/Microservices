package org.example.order.dto;

import jakarta.validation.constraints.NotNull;
import org.example.order.entity.OrderStatus;

public record OrderStatusUpdateDTO(@NotNull OrderStatus status) {}
