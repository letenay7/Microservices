package org.example.order.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OrderEntityValidationTests {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Order validOrder() {
        Instant now = Instant.now();
        return Order.builder()
                .username("alice")
                .items(List.of())
                .totalPrice(BigDecimal.ZERO)
                .status(OrderStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private Set<String> violatedFields(Order order) {
        return validator.validate(order).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validOrderShouldHaveNoViolations() {
        assertThat(violatedFields(validOrder())).isEmpty();
    }

    @Test
    void nullUsernameShouldBeRejected() {
        Order order = validOrder();
        order.setUsername(null);

        assertThat(violatedFields(order)).containsExactly("username");
    }

    @Test
    void blankUsernameShouldBeRejected() {
        Order order = validOrder();
        order.setUsername("   ");

        assertThat(violatedFields(order)).containsExactly("username");
    }

    @Test
    void nullItemsShouldBeRejected() {
        Order order = validOrder();
        order.setItems(null);

        assertThat(violatedFields(order)).containsExactly("items");
    }

    @Test
    void nullTotalPriceShouldBeRejected() {
        Order order = validOrder();
        order.setTotalPrice(null);

        assertThat(violatedFields(order)).containsExactly("totalPrice");
    }

    @Test
    void nullStatusShouldBeRejected() {
        Order order = validOrder();
        order.setStatus(null);

        assertThat(violatedFields(order)).containsExactly("status");
    }

    @Test
    void nullCreatedAtShouldBeRejected() {
        Order order = validOrder();
        order.setCreatedAt(null);

        assertThat(violatedFields(order)).containsExactly("createdAt");
    }

    @Test
    void nullUpdatedAtShouldBeRejected() {
        Order order = validOrder();
        order.setUpdatedAt(null);

        assertThat(violatedFields(order)).containsExactly("updatedAt");
    }
}