package org.example.order.dto;

import java.math.BigDecimal;

public record ProductDTO(long productId, String name, BigDecimal price) {}
