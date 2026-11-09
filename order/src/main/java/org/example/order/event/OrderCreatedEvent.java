package org.example.order.event;

import org.example.order.entity.OrderItem;

import java.math.BigDecimal;
import java.util.List;

public record OrderCreatedEvent(long orderId, String username,
                                BigDecimal totalPrice, List<OrderItem> orderItems) {}
