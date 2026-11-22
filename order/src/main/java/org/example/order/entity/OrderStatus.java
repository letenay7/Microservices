package org.example.order.entity;

import java.util.Set;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    private static final Set<OrderStatus> PENDING_TRANSITIONS = Set.of(CONFIRMED, CANCELLED);
    private static final Set<OrderStatus> CONFIRMED_TRANSITIONS = Set.of(SHIPPED, CANCELLED);
    private static final Set<OrderStatus> SHIPPED_TRANSITIONS = Set.of(DELIVERED);

    public boolean canTransitionTo(OrderStatus target) {
        Set<OrderStatus> allowed = switch (this) {
            case PENDING -> PENDING_TRANSITIONS;
            case CONFIRMED -> CONFIRMED_TRANSITIONS;
            case SHIPPED -> SHIPPED_TRANSITIONS;
            case DELIVERED, CANCELLED -> Set.of();
        };
        return allowed.contains(target);
    }
}
