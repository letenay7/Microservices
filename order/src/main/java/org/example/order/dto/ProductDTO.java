package org.example.order.dto;

import java.math.BigDecimal;

public record ProductDTO(Long productId, String name, BigDecimal price) {}
