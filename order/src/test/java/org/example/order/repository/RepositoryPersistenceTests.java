package org.example.order.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolationException;
import org.example.order.entity.Order;
import org.example.order.entity.OrderItem;
import org.example.order.entity.OrderStatus;
import org.example.order.support.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real PostgreSQL, real Hibernate mapping. Each test runs in a transaction that is rolled back.
 */
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfig.class)
class RepositoryPersistenceTests {

    private static final Instant CREATED_AT = Instant.parse("2026-11-25T12:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-11-26T12:00:00Z");

    @Autowired
    private OrderRepository orderRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------- helpers ----------

    private OrderItem item(Long productId, int quantity, BigDecimal price) {
        return OrderItem.builder()
                .productId(productId)
                .productName("product-" + productId)
                .quantity(quantity)
                .price(price)
                .build();
    }

    private Order saveOrder(String username, List<OrderItem> items) {
        Order order = Order.builder()
                .username(username)
                .items(items)
                .totalPrice(BigDecimal.ZERO)
                .status(OrderStatus.PENDING)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();

        order.setTotalPrice(
                items.stream()
                        .map(i -> i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        Order saved = orderRepository.saveAndFlush(order);
        entityManager.clear();
        return saved;
    }

    // ---------- order round-trip ----------

    @Test
    void orderFieldsShouldRoundTripThroughTheDatabase() {
        Order saved = saveOrder("alice", List.of(item(1L, 2, new BigDecimal("1200.00"))));

        Optional<Order> found = orderRepository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("alice");
        assertThat(found.get().getTotalPrice()).isEqualByComparingTo("2400.00");
        assertThat(found.get().getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(found.get().getUpdatedAt()).isEqualTo(UPDATED_AT);
    }

    // ---------- cascade persistence ----------

    @Test
    void itemsShouldBeCascadePersistedWithOrder() {
        Order saved = saveOrder("alice", List.of(
                item(1L, 2, new BigDecimal("10.50")),
                item(2L, 1, new BigDecimal("3.25"))
        ));

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getItems()).hasSize(2);
        assertThat(reloaded.getItems())
                .extracting(OrderItem::getProductId)
                .containsExactlyInAnyOrder(1L, 2L);
        assertThat(reloaded.getItems())
                .extracting(OrderItem::getProductName)
                .containsExactlyInAnyOrder("product-1", "product-2");
    }

    // ---------- orphan removal ----------

    @Test
    void removingItemFromListShouldRemoveItFromDatabase() {
        Order saved = saveOrder("alice", List.of(
                item(1L, 1, new BigDecimal("100.00")),
                item(2L, 1, new BigDecimal("200.00"))
        ));

        // Load, remove one item, save again
        Order managed = orderRepository.findById(saved.getId()).orElseThrow();
        managed.getItems().clear();
        orderRepository.save(managed);
        entityManager.flush();
        entityManager.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getItems()).isEmpty();
    }

    // ---------- findByIdWithItems (JOIN FETCH) ----------

    @Test
    void findByIdWithItemsShouldReturnOrderWithItemsEagerly() {
        Order saved = saveOrder("bob", List.of(
                item(5L, 3, new BigDecimal("7.99"))
        ));

        Optional<Order> found = orderRepository.findByIdWithItems(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getItems()).hasSize(1);
        assertThat(found.get().getItems().getFirst().getProductId()).isEqualTo(5L);
        assertThat(found.get().getItems().getFirst().getQuantity()).isEqualTo(3);
    }

    // ---------- findByUsername (single result) ----------

    @Test
    void findByUsernameShouldReturnTheMatchingOrder() {
        saveOrder("charlie", List.of(item(1L, 1, new BigDecimal("50.00"))));

        Optional<Order> found = orderRepository.findByUsername("charlie");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("charlie");
    }

    @Test
    void findByUsernameShouldReturnEmptyForUnknownUser() {
        saveOrder("charlie", List.of(item(1L, 1, new BigDecimal("50.00"))));

        Optional<Order> found = orderRepository.findByUsername("unknown");

        assertThat(found).isEmpty();
    }

    // ---------- findAllOrdersByUsername ----------

    @Test
    void findAllOrdersByUsernameShouldReturnAllOrdersForThatUser() {
        saveOrder("dave", List.of(item(1L, 1, new BigDecimal("10.00"))));
        saveOrder("dave", List.of(item(2L, 1, new BigDecimal("20.00"))));
        saveOrder("eve", List.of(item(3L, 1, new BigDecimal("30.00"))));

        List<Order> daveOrders = orderRepository.findAllOrdersByUsername("dave");

        assertThat(daveOrders).hasSize(2);
        assertThat(daveOrders).extracting(Order::getUsername).containsOnly("dave");
    }

    @Test
    void findAllOrdersByUsernameShouldReturnEmptyListForUnknownUser() {
        saveOrder("dave", List.of(item(1L, 1, new BigDecimal("10.00"))));

        List<Order> found = orderRepository.findAllOrdersByUsername("nobody");

        assertThat(found).isEmpty();
    }

    // ---------- Bean Validation and DB-level nullable constraints ----------

    @Test
    void nullUsernameShouldBeRejectedByBeanValidation() {
        Order order = Order.builder()
                .username(null)
                .items(List.of())
                .totalPrice(new BigDecimal("100.00"))
                .status(OrderStatus.PENDING)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();

        assertThrows(ConstraintViolationException.class,
                () -> orderRepository.saveAndFlush(order));
    }

    @Test
    void nullTotalPriceShouldBeRejectedByBeanValidation() {
        Order order = Order.builder()
                .username("alice")
                .items(List.of())
                .totalPrice(null)
                .status(OrderStatus.PENDING)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();

        assertThrows(ConstraintViolationException.class,
                () -> orderRepository.saveAndFlush(order));
    }

    @Test
    void orderItemWithNullProductIdShouldBeRejectedByBeanValidation() {
        Order order = Order.builder()
                .username("alice")
                .items(List.of(OrderItem.builder().productId(null).productName("product")
                        .quantity(1).price(new BigDecimal("10.00")).build()))
                .totalPrice(new BigDecimal("10.00"))
                .status(OrderStatus.PENDING)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();

        assertThrows(ConstraintViolationException.class,
                () -> orderRepository.saveAndFlush(order));
    }

    @Test
    void orderItemWithNullProductNameShouldBeRejectedByBeanValidation() {
        Order order = Order.builder()
                .username("alice")
                .items(List.of(OrderItem.builder().productId(1L).productName(null)
                        .quantity(1).price(new BigDecimal("10.00")).build()))
                .totalPrice(new BigDecimal("10.00"))
                .status(OrderStatus.PENDING)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();

        assertThrows(ConstraintViolationException.class,
                () -> orderRepository.saveAndFlush(order));
    }

    @Test
    void orderItemWithNullQuantityShouldBeRejectedByBeanValidation() {
        Order order = Order.builder()
                .username("alice")
                .items(List.of(OrderItem.builder().productId(1L).productName("product")
                        .quantity(null).price(new BigDecimal("10.00")).build()))
                .totalPrice(new BigDecimal("10.00"))
                .status(OrderStatus.PENDING)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();

        assertThrows(ConstraintViolationException.class,
                () -> orderRepository.saveAndFlush(order));
    }

    @Test
    void orderItemWithNullPriceShouldBeRejectedByBeanValidation() {
        Order order = Order.builder()
                .username("alice")
                .items(List.of(OrderItem.builder().productId(1L).productName("product")
                        .quantity(1).price(null).build()))
                .totalPrice(new BigDecimal("10.00"))
                .status(OrderStatus.PENDING)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();

        assertThrows(ConstraintViolationException.class,
                () -> orderRepository.saveAndFlush(order));
    }

    // ---------- status field persistence ----------

    @Test
    void statusFieldShouldBeStoredAndRetrieved() {
        Order saved = saveOrder("alice", List.of(item(1L, 1, new BigDecimal("10.00"))));
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);

        Order found = orderRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void nullStatusShouldBeRejectedByBeanValidation() {
        Order order = Order.builder()
                .username("alice")
                .items(List.of())
                .totalPrice(new BigDecimal("100.00"))
                .status(null)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();

        assertThrows(ConstraintViolationException.class,
                () -> orderRepository.saveAndFlush(order));
    }

    // ---------- findAllOrdersByUsernameAndStatus ----------

    @Test
    void findAllOrdersByUsernameAndStatusShouldReturnMatchingOrders() {
        Order order1 = Order.builder()
                .username("alice")
                .items(List.of(item(1L, 1, new BigDecimal("10.00"))))
                .totalPrice(new BigDecimal("10.00"))
                .status(OrderStatus.CONFIRMED)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();
        Order order2 = Order.builder()
                .username("alice")
                .items(List.of(item(2L, 1, new BigDecimal("20.00"))))
                .totalPrice(new BigDecimal("20.00"))
                .status(OrderStatus.CONFIRMED)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();
        Order order3 = Order.builder()
                .username("alice")
                .items(List.of(item(3L, 1, new BigDecimal("30.00"))))
                .totalPrice(new BigDecimal("30.00"))
                .status(OrderStatus.PENDING)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();

        orderRepository.saveAndFlush(order1);
        orderRepository.saveAndFlush(order2);
        orderRepository.saveAndFlush(order3);
        entityManager.clear();

        List<Order> confirmed = orderRepository.findAllOrdersByUsernameAndStatus("alice", OrderStatus.CONFIRMED);

        assertThat(confirmed).hasSize(2);
        assertThat(confirmed).extracting(Order::getStatus).containsOnly(OrderStatus.CONFIRMED);
    }

    @Test
    void findAllOrdersByUsernameAndStatusShouldReturnEmptyForNoMatch() {
        Order order = Order.builder()
                .username("alice")
                .items(List.of(item(1L, 1, new BigDecimal("10.00"))))
                .totalPrice(new BigDecimal("10.00"))
                .status(OrderStatus.PENDING)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();
        orderRepository.saveAndFlush(order);
        entityManager.clear();

        List<Order> found = orderRepository.findAllOrdersByUsernameAndStatus("alice", OrderStatus.SHIPPED);

        assertThat(found).isEmpty();
    }

    @Test
    void findAllOrdersByUsernameAndStatusShouldIgnoreOtherUsers() {
        Order order1 = Order.builder()
                .username("alice")
                .items(List.of(item(1L, 1, new BigDecimal("10.00"))))
                .totalPrice(new BigDecimal("10.00"))
                .status(OrderStatus.CONFIRMED)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();
        Order order2 = Order.builder()
                .username("bob")
                .items(List.of(item(2L, 1, new BigDecimal("20.00"))))
                .totalPrice(new BigDecimal("20.00"))
                .status(OrderStatus.CONFIRMED)
                .createdAt(CREATED_AT)
                .updatedAt(UPDATED_AT)
                .build();

        orderRepository.saveAndFlush(order1);
        orderRepository.saveAndFlush(order2);
        entityManager.clear();

        List<Order> found = orderRepository.findAllOrdersByUsernameAndStatus("alice", OrderStatus.CONFIRMED);

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getUsername()).isEqualTo("alice");
    }
}
