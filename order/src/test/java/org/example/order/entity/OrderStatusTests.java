package org.example.order.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OrderStatus} enum transition logic.
 * Covers all valid transitions, invalid transitions, and terminal states.
 */
class OrderStatusTests {

    // ---------- valid transitions ----------

    @Test
    void pendingShouldTransitionToConfirmed() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
    }

    @Test
    void pendingShouldTransitionToCancelled() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void confirmedShouldTransitionToShipped() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
    }

    @Test
    void confirmedShouldTransitionToCancelled() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
    }

    @Test
    void shippedShouldTransitionToDelivered() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    // ---------- invalid transitions ----------

    @Test
    void pendingShouldNotTransitionToShipped() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
    }

    @Test
    void pendingShouldNotTransitionToDelivered() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void pendingShouldNotTransitionToPending() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PENDING)).isFalse();
    }

    @Test
    void confirmedShouldNotTransitionToPending() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PENDING)).isFalse();
    }

    @Test
    void confirmedShouldNotTransitionToDelivered() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.DELIVERED)).isFalse();
    }

    @Test
    void confirmedShouldNotTransitionToConfirmed() {
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void shippedShouldNotTransitionToPending() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.PENDING)).isFalse();
    }

    @Test
    void shippedShouldNotTransitionToConfirmed() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
    }

    @Test
    void shippedShouldNotTransitionToCancelled() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void shippedShouldNotTransitionToShipped() {
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
    }

    // ---------- terminal states ----------

    @Test
    void deliveredShouldNotTransitionToAnyStatus() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(target))
                    .as("DELIVERED should not transition to %s", target)
                    .isFalse();
        }
    }

    @Test
    void cancelledShouldNotTransitionToAnyStatus() {
        for (OrderStatus target : OrderStatus.values()) {
            assertThat(OrderStatus.CANCELLED.canTransitionTo(target))
                    .as("CANCELLED should not transition to %s", target)
                    .isFalse();
        }
    }

    // ---------- enum completeness ----------

    @Test
    void shouldHaveExactlyFiveStatuses() {
        assertThat(OrderStatus.values()).hasSize(5);
    }
}
