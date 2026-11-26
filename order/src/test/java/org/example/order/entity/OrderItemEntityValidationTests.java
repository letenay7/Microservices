package org.example.order.entity;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemEntityValidationTests {

    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private OrderItem validOrderItem() {
        return OrderItem.builder()
                .productId(1L)
                .productName("product")
                .quantity(1)
                .price(new BigDecimal("10.00"))
                .build();
    }

    private Set<String> violatedFields(OrderItem orderItem) {
        return validator.validate(orderItem).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validOrderItemShouldHaveNoViolations() {
        assertThat(violatedFields(validOrderItem())).isEmpty();
    }

    @Test
    void nullProductIdShouldBeRejected() {
        OrderItem orderItem = validOrderItem();
        orderItem.setProductId(null);

        assertThat(violatedFields(orderItem)).containsExactly("productId");
    }

    @Test
    void nullProductNameShouldBeRejected() {
        OrderItem orderItem = validOrderItem();
        orderItem.setProductName(null);

        assertThat(violatedFields(orderItem)).containsExactly("productName");
    }

    @Test
    void nullQuantityShouldBeRejected() {
        OrderItem orderItem = validOrderItem();
        orderItem.setQuantity(null);

        assertThat(violatedFields(orderItem)).containsExactly("quantity");
    }

    @Test
    void nullPriceShouldBeRejected() {
        OrderItem orderItem = validOrderItem();
        orderItem.setPrice(null);

        assertThat(violatedFields(orderItem)).containsExactly("price");
    }
}