package org.example.order.dto;

import java.util.List;

public record OrderRequestDTO(List<OrderItemDTO> items) {}
