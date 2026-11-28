package org.example.order.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderRequestDTO(@NotNull List<OrderItemDTO> items) {}
