package org.example.order.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.example.order.entity.Order;
import org.example.order.entity.OrderItem;
import org.example.order.support.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
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

    @Autowired
    private OrderRepository orderRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ---------- helpers ----------

    private OrderItem item(Long productId, int quantity, BigDecimal price) {
        return OrderItem.builder()
                .productId(productId)
                .quantity(quantity)
                .price(price)
                .build();
    }

    private Order saveOrder(String username, List<OrderItem> items) {
        Order order = Order.builder()
                .username(username)
                .items(items)
                .totalPrice(BigDecimal.ZERO)
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

    // ---------- DB-level nullable constraints ----------

    @Test
    void nullUsernameShouldBeRejectedByDatabase() {
        Order order = Order.builder()
                .username(null)
                .items(List.of())
                .totalPrice(new BigDecimal("100.00"))
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> orderRepository.saveAndFlush(order));
    }

    @Test
    void nullTotalPriceShouldBeRejectedByDatabase() {
        Order order = Order.builder()
                .username("alice")
                .items(List.of())
                .totalPrice(null)
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> orderRepository.saveAndFlush(order));
    }

    @Test
    void orderItemWithNullProductIdShouldBeRejectedByDatabase() {
        Order order = Order.builder()
                .username("alice")
                .items(List.of(OrderItem.builder().productId(null).quantity(1).price(new BigDecimal("10.00")).build()))
                .totalPrice(new BigDecimal("10.00"))
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> orderRepository.saveAndFlush(order));
    }

    @Test
    void orderItemWithNullQuantityShouldBeRejectedByDatabase() {
        Order order = Order.builder()
                .username("alice")
                .items(List.of(OrderItem.builder().productId(1L).quantity(null).price(new BigDecimal("10.00")).build()))
                .totalPrice(new BigDecimal("10.00"))
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> orderRepository.saveAndFlush(order));
    }

    @Test
    void orderItemWithNullPriceShouldBeRejectedByDatabase() {
        Order order = Order.builder()
                .username("alice")
                .items(List.of(OrderItem.builder().productId(1L).quantity(1).price(null).build()))
                .totalPrice(new BigDecimal("10.00"))
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> orderRepository.saveAndFlush(order));
    }
}
