package org.example.order.event;

import org.example.order.entity.OrderStatus;

public record OrderStatusChangedEvent(long orderId, String username,
                                      OrderStatus prevStatus, OrderStatus newStatus) {
}
