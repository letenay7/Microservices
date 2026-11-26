package org.example.order.service;

import org.example.order.client.ProductClient;
import org.example.order.dto.OrderItemDTO;
import org.example.order.dto.OrderProcessDTO;
import org.example.order.dto.OrderRequestDTO;
import org.example.order.dto.OrderResponseDTO;
import org.example.order.dto.ProductDTO;
import org.example.order.entity.Order;
import org.example.order.entity.OrderItem;
import org.example.order.entity.OrderStatus;
import org.example.order.event.OrderCreatedEvent;
import org.example.order.event.OrderStatusChangedEvent;
import org.example.order.exception.CannotModifyOrderException;
import org.example.order.exception.OrderNotFoundException;
import org.example.order.messaging.OrderEventProducer;
import org.example.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTests {

    private static final String USER = "alice";

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProductClient productClient;
    @Mock
    private OrderEventProducer orderEventProducer;

    @InjectMocks
    private OrderService orderService;

    // ---------- helpers ----------

    // NOTE: adjust the ProductDTO constructor if yours differs
    private static ProductDTO product(long id, String price) {
        return new ProductDTO(id, "Product " + id, new BigDecimal(price));
    }

    private static OrderRequestDTO request(OrderItemDTO... items) {
        return new OrderRequestDTO(List.of(items));
    }

    private static OrderItem item(long productId, int quantity, String price) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal(price));
        return item;
    }

    private static Order order(Long id , String username, OrderItem... items) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", id); // avoids depending on a setId()
        order.setUsername(username);
        order.setItems(new ArrayList<>(List.of(items)));
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem i : items) {
            total = total.add(i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
        }
        order.setTotalPrice(total);
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    private static Order orderWithStatus(Long id, String username, OrderStatus status, OrderItem... items) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", id);
        order.setUsername(username);
        order.setItems(new ArrayList<>(List.of(items)));
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem i : items) {
            total = total.add(i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
        }
        order.setTotalPrice(total);
        order.setStatus(status);
        return order;
    }

    // ---------- processOrderRequest ----------

    @Test
    void processOrderRequest_singleItem_calculatesTotal() {
        when(productClient.getProductById(1L, USER)).thenReturn(product(1, "1200.00"));

        OrderProcessDTO result = orderService.processOrderRequest(
                request(new OrderItemDTO(1L, 2)), USER);

        assertThat(result.totalPrice()).isEqualByComparingTo("2400.00");
        assertThat(result.orderItems()).hasSize(1);
        OrderItem created = result.orderItems().getFirst();
        assertThat(created.getProductId()).isEqualTo(1L);
        assertThat(created.getQuantity()).isEqualTo(2);
        assertThat(created.getPrice()).isEqualByComparingTo("1200.00");
    }

    @Test
    void processOrderRequest_multipleItems_sumsAllLines() {
        when(productClient.getProductById(1L, USER)).thenReturn(product(1, "10.50"));
        when(productClient.getProductById(2L, USER)).thenReturn(product(2, "3.25"));

        OrderProcessDTO result = orderService.processOrderRequest(
                request(new OrderItemDTO(1L, 2), new OrderItemDTO(2L, 4)), USER);

        // 10.50*2 + 3.25*4 = 21.00 + 13.00
        assertThat(result.totalPrice()).isEqualByComparingTo("34.00");
        assertThat(result.orderItems()).hasSize(2);
    }

    @Test
    void processOrderRequest_emptyItems_returnsZeroTotal() {
        OrderProcessDTO result = orderService.processOrderRequest(
                new OrderRequestDTO(List.of()), USER);

        assertThat(result.totalPrice()).isEqualByComparingTo("0.00");
        assertThat(result.orderItems()).isEmpty();
        verifyNoInteractions(productClient);
    }

    @Test
    void processOrderRequest_forwardsUsernameToProductClient() {
        when(productClient.getProductById(1L, USER)).thenReturn(product(1, "5.00"));

        orderService.processOrderRequest(request(new OrderItemDTO(1L, 1)), USER);

        verify(productClient).getProductById(1L, USER);
    }

    // ---------- createOrder ----------

    @Test
    void createOrder_savesOrderPublishesEventAndReturnsResponse() {
        when(productClient.getProductById(1L, USER)).thenReturn(product(1, "1200.00"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 42L);
            return o;
        });

        OrderResponseDTO response = orderService.createOrder(
                request(new OrderItemDTO(1L, 2)), USER);

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getUsername()).isEqualTo(USER);
        assertThat(saved.getValue().getCreatedAt()).isNotNull();
        assertThat(saved.getValue().getUpdatedAt()).isEqualTo(saved.getValue().getCreatedAt());
        assertThat(saved.getValue().getTotalPrice()).isEqualByComparingTo("2400.00");
        assertThat(saved.getValue().getItems()).hasSize(1);

        ArgumentCaptor<OrderCreatedEvent> event = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(orderEventProducer).sendOrderCreatedEvent(event.capture());
        assertThat(event.getValue()).isNotNull();

        assertThat(response.username()).isEqualTo(USER);
        assertThat(response.totalPrice()).isEqualByComparingTo("2400.00");
        assertThat(response.items()).hasSize(1);
    }

    @Test
    void createOrder_whenProductLookupFails_doesNotSaveOrPublish() {
        when(productClient.getProductById(anyLong(), anyString()))
                .thenThrow(new RuntimeException("product service down"));

        assertThatThrownBy(() -> orderService.createOrder(request(new OrderItemDTO(1L, 1)), USER))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("product service down");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderEventProducer);
    }

    // ---------- getOrderById ----------

    @Test
    void getOrderById_whenFound_returnsOrder() {
        Order existing = order(1L, USER, item(1, 1, "10.00"));
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(existing));

        assertThat(orderService.getOrderById(1L)).isSameAs(existing);
    }

    @Test
    void getOrderById_whenMissing_throwsNotFound() {
        when(orderRepository.findByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // ---------- updateOrder ----------

    @Test
    void updateOrder_replacesItemsAndTotal() {
        Order existing = order(2L, USER, item(2, 1, "1200.00"));
        when(orderRepository.findByIdWithItems(2L)).thenReturn(Optional.of(existing));
        when(productClient.getProductById(2L, USER)).thenReturn(product(2, "1200.00"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.updateOrder(2L, USER,
                request(new OrderItemDTO(2L, 3)));

        assertThat(result.getTotalPrice()).isEqualByComparingTo("3600.00");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getQuantity()).isEqualTo(3);
        verify(orderRepository).save(existing);
    }

    @Test
    void updateOrder_whenOrderMissing_throwsNotFound() {
        when(orderRepository.findByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateOrder(99L, USER, request(new OrderItemDTO(1L, 1))))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateOrder_whenDifferentUser_throwsAndDoesNothing() {
        Order existing = order(3L, "bob", item(1, 1, "10.00"));
        when(orderRepository.findByIdWithItems(3L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.updateOrder(3L, USER,
                request(new OrderItemDTO(1L, 5))))
                .isInstanceOf(OrderNotFoundException.class);

        verifyNoInteractions(productClient);
        verify(orderRepository, never()).save(any());
        assertThat(existing.getItems().getFirst().getQuantity()).isEqualTo(1); // untouched
    }

    // ---------- deleteOrder ----------

    @Test
    void deleteOrder_whenOwner_deletesById() {
        when(orderRepository.findByIdWithItems(4L))
                .thenReturn(Optional.of(order(4L, USER,
                        item(1, 1, "10.00"))));

        orderService.deleteOrder(4L, USER);

        verify(orderRepository).deleteById(4L);
    }

    @Test
    void deleteOrder_whenDifferentUser_throwsAndDoesNotDelete() {
        when(orderRepository.findByIdWithItems(5L))
                .thenReturn(Optional.of(order(5L, "bob",
                        item(1, 1, "10.00"))));

        assertThatThrownBy(() -> orderService.deleteOrder(5L, USER))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteOrder_whenMissing_throwsNotFound() {
        when(orderRepository.findByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.deleteOrder(99L, USER))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).deleteById(anyLong());
    }

    // ---------- queries (assumes the DTO-returning versions from option B) ----------

    @Test
    void getOrderByIdForUser_whenOwnedByAnotherUser_throwsNotFound() {
        when(orderRepository.findByIdWithItems(6L))
                .thenReturn(Optional.of(order(6L, "bob",
                        item(7, 1, "10.00"))));

        assertThatThrownBy(() -> orderService.getOrderByIdForUser(6L, USER))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getAllOrdersByUsername_returnsOnlyRepositoryResultsMappedToDto() {
        when(orderRepository.findAllOrdersByUsername(USER))
                .thenReturn(List.of(order(7L, USER,
                        item(7, 3, "2.00"))));

        List<OrderResponseDTO> result = orderService.getAllOrdersByUsername(USER);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().username()).isEqualTo(USER);
        assertThat(result.getFirst().items()).hasSize(1);
    }

    @Test
    void getAllOrdersByUsername_whenNoOrders_returnsEmptyList() {
        when(orderRepository.findAllOrdersByUsername(USER)).thenReturn(List.of());

        assertThat(orderService.getAllOrdersByUsername(USER)).isEmpty();
    }

    // ---------- updateStatus ----------

    @Test
    void updateStatus_validTransition_updatesAndReturnsResponse() {
        Order existing = orderWithStatus(1L, USER, OrderStatus.PENDING, item(1, 1, "10.00"));
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(existing));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponseDTO response = orderService.updateStatus(1L, USER, OrderStatus.CONFIRMED);

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.orderId()).isEqualTo(1L);
        verify(orderRepository).save(existing);

        ArgumentCaptor<OrderStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderStatusChangedEvent.class);
        verify(orderEventProducer).sendOrderStatusChangedEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isEqualTo(new OrderStatusChangedEvent(
                1L, USER, OrderStatus.PENDING, OrderStatus.CONFIRMED));
    }

    @Test
    void updateStatus_invalidTransition_throwsConflict() {
        Order existing = orderWithStatus(1L, USER, OrderStatus.PENDING, item(1, 1, "10.00"));
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.updateStatus(1L, USER, OrderStatus.SHIPPED))
                .isInstanceOf(CannotModifyOrderException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("SHIPPED");

        verify(orderRepository, never()).save(any());
        verify(orderEventProducer, never()).sendOrderStatusChangedEvent(any());
    }

    @Test
    void updateStatus_terminalStateDelivered_throwsConflict() {
        Order existing = orderWithStatus(1L, USER, OrderStatus.DELIVERED, item(1, 1, "10.00"));
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.updateStatus(1L, USER, OrderStatus.CANCELLED))
                .isInstanceOf(CannotModifyOrderException.class)
                .hasMessageContaining("DELIVERED");
    }

    @Test
    void updateStatus_terminalStateCancelled_throwsConflict() {
        Order existing = orderWithStatus(1L, USER, OrderStatus.CANCELLED, item(1, 1, "10.00"));
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.updateStatus(1L, USER, OrderStatus.PENDING))
                .isInstanceOf(CannotModifyOrderException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void updateStatus_differentUser_throwsNotFound() {
        Order existing = orderWithStatus(1L, "bob", OrderStatus.PENDING, item(1, 1, "10.00"));
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.updateStatus(1L, USER, OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateStatus_missingOrder_throwsNotFound() {
        when(orderRepository.findByIdWithItems(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.updateStatus(99L, USER, OrderStatus.CONFIRMED))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void updateStatus_fullLifecycle_pendingToDelivered() {
        // PENDING → CONFIRMED
        Order order1 = orderWithStatus(1L, USER, OrderStatus.PENDING, item(1, 1, "10.00"));
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order1));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderResponseDTO r1 = orderService.updateStatus(1L, USER, OrderStatus.CONFIRMED);
        assertThat(r1.status()).isEqualTo(OrderStatus.CONFIRMED);

        // CONFIRMED → SHIPPED
        order1.setStatus(OrderStatus.CONFIRMED);
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order1));

        OrderResponseDTO r2 = orderService.updateStatus(1L, USER, OrderStatus.SHIPPED);
        assertThat(r2.status()).isEqualTo(OrderStatus.SHIPPED);

        // SHIPPED → DELIVERED
        order1.setStatus(OrderStatus.SHIPPED);
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(order1));

        OrderResponseDTO r3 = orderService.updateStatus(1L, USER, OrderStatus.DELIVERED);
        assertThat(r3.status()).isEqualTo(OrderStatus.DELIVERED);
    }

    // ---------- getOrdersByUsernameAndStatus ----------

    @Test
    void getOrdersByUsernameAndStatus_returnsMatchingOrders() {
        when(orderRepository.findAllOrdersByUsernameAndStatus(USER, OrderStatus.CONFIRMED))
                .thenReturn(List.of(
                        orderWithStatus(1L, USER, OrderStatus.CONFIRMED, item(1, 1, "10.00")),
                        orderWithStatus(2L, USER, OrderStatus.CONFIRMED, item(2, 1, "20.00"))
                ));

        List<OrderResponseDTO> result = orderService.getOrdersByUsernameAndStatus(USER, OrderStatus.CONFIRMED);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(OrderResponseDTO::status)
                .containsOnly(OrderStatus.CONFIRMED);
    }

    @Test
    void getOrdersByUsernameAndStatus_noMatch_returnsEmptyList() {
        when(orderRepository.findAllOrdersByUsernameAndStatus(USER, OrderStatus.SHIPPED))
                .thenReturn(List.of());

        List<OrderResponseDTO> result = orderService.getOrdersByUsernameAndStatus(USER, OrderStatus.SHIPPED);

        assertThat(result).isEmpty();
    }

    // ---------- createOrder sets PENDING status ----------

    @Test
    void createOrder_setsDefaultPendingStatus() {
        when(productClient.getProductById(1L, USER)).thenReturn(product(1, "1200.00"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 42L);
            return o;
        });

        orderService.createOrder(request(new OrderItemDTO(1L, 2)), USER);

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
    }
}
