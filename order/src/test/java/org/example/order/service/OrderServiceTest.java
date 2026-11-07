package org.example.order.service;

import org.example.order.client.ProductClient;
import org.example.order.dto.OrderItemDTO;
import org.example.order.dto.OrderProcessDTO;
import org.example.order.dto.OrderRequestDTO;
import org.example.order.dto.OrderResponseDTO;
import org.example.order.dto.ProductDTO;
import org.example.order.entity.Order;
import org.example.order.entity.OrderItem;
import org.example.order.event.OrderCreatedEvent;
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
class OrderServiceTest {

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

    private static Order order(Long id, String username, OrderItem... items) {
        Order order = new Order();
        ReflectionTestUtils.setField(order, "id", id); // avoids depending on a setId()
        order.setUsername(username);
        order.setItems(new ArrayList<>(List.of(items)));
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItem i : items) {
            total = total.add(i.getPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
        }
        order.setTotalPrice(total);
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
        OrderItem created = result.orderItems().get(0);
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
        Order existing = order(1L, USER, item(1, 1, "1200.00"));
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(existing));
        when(productClient.getProductById(1L, USER)).thenReturn(product(1, "1200.00"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.updateOrder(1L, USER, request(new OrderItemDTO(1L, 3)));

        assertThat(result.getTotalPrice()).isEqualByComparingTo("3600.00");
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(3);
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
        Order existing = order(1L, "bob", item(1, 1, "10.00"));
        when(orderRepository.findByIdWithItems(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> orderService.updateOrder(1L, USER, request(new OrderItemDTO(1L, 5))))
                .isInstanceOf(OrderNotFoundException.class);

        verifyNoInteractions(productClient);
        verify(orderRepository, never()).save(any());
        assertThat(existing.getItems().get(0).getQuantity()).isEqualTo(1); // untouched
    }

    // ---------- deleteOrder ----------

    @Test
    void deleteOrder_whenOwner_deletesById() {
        when(orderRepository.findByIdWithItems(1L))
                .thenReturn(Optional.of(order(1L, USER, item(1, 1, "10.00"))));

        orderService.deleteOrder(1L, USER);

        verify(orderRepository).deleteById(1L);
    }

    @Test
    void deleteOrder_whenDifferentUser_throwsAndDoesNotDelete() {
        when(orderRepository.findByIdWithItems(1L))
                .thenReturn(Optional.of(order(1L, "bob", item(1, 1, "10.00"))));

        assertThatThrownBy(() -> orderService.deleteOrder(1L, USER))
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
        when(orderRepository.findByIdWithItems(1L))
                .thenReturn(Optional.of(order(1L, "bob", item(1, 1, "10.00"))));

        assertThatThrownBy(() -> orderService.getOrderByIdForUser(1L, USER))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void getAllOrdersByUsername_returnsOnlyRepositoryResultsMappedToDto() {
        when(orderRepository.findAllOrdersByUsername(USER))
                .thenReturn(List.of(order(1L, USER, item(1, 3, "2.00"))));

        List<OrderResponseDTO> result = orderService.getAllOrdersByUsername(USER);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).username()).isEqualTo(USER);
        assertThat(result.get(0).items()).hasSize(1);
    }

    @Test
    void getAllOrdersByUsername_whenNoOrders_returnsEmptyList() {
        when(orderRepository.findAllOrdersByUsername(USER)).thenReturn(List.of());

        assertThat(orderService.getAllOrdersByUsername(USER)).isEmpty();
    }
}