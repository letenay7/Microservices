package org.example.order.service;

import org.example.order.client.ProductClient;
import org.example.order.dto.*;
import org.example.order.entity.Order;
import org.example.order.entity.OrderItem;
import org.example.order.event.OrderCreatedEvent;
import org.example.order.exception.OrderNotFoundException;
import org.example.order.messaging.OrderEventProducer;
import org.example.order.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final OrderEventProducer orderEventProducer;

    public OrderService(OrderRepository orderRepository, ProductClient productClient,
                        OrderEventProducer orderEventProducer) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.orderEventProducer = orderEventProducer;
    }

    public Order getOrderById(Long orderId)  {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));

    }



    public Order getOrderByIdForUser(Long id, String username) {
        Order order = getOrderById(id);
        if (!order.getUsername().equals(username)) {
            throw new OrderNotFoundException("Order not found");
        }
        return order;
    }

    public OrderProcessDTO processOrderRequest(OrderRequestDTO orderRequestDTO, String username) {
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalPrice = new BigDecimal("0.00");
        for (OrderItemDTO item : orderRequestDTO.items()) {
            ProductDTO product = productClient.getProductById(item.productId(), username);

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(product.productId());
            Integer quantity = item.quantity();
            orderItem.setQuantity(quantity);
            BigDecimal productPrice = product.price();
            orderItem.setPrice(productPrice);

            orderItems.add(orderItem);

            // totalPrice += productPrice * quantity
            totalPrice = totalPrice.add(productPrice.multiply(BigDecimal.valueOf(quantity)));
        }
        return new OrderProcessDTO(orderItems, totalPrice);
    }

    @Transactional
    public OrderResponseDTO createOrder(OrderRequestDTO orderRequestDTO, String username) {
        OrderProcessDTO processed = processOrderRequest(orderRequestDTO, username);
        Order order = new Order();
        order.setUsername(username);
        List<OrderItem> orderItems = processed.orderItems();
        order.setItems(orderItems);
        order.setTotalPrice(processed.totalPrice());
        Order savedOrder = orderRepository.save(order);

        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getUsername(),
                savedOrder.getTotalPrice(),
                orderItems
        );

        orderEventProducer.sendOrderCreatedEvent(event);
        return mapToResponse(savedOrder, orderItems);
    }

    private OrderResponseDTO mapToResponse(Order order, List<OrderItem> orderItems) {
        return new OrderResponseDTO(
                order.getId(),
                order.getUsername(),
                orderItems,
                order.getTotalPrice(),
                System.currentTimeMillis()
        );
    }

    public Order updateOrder(Long orderId, String username, OrderRequestDTO orderRequestDTO) {
        Order toUpdate = getOrderByIdForUser(orderId, username);
        OrderProcessDTO updatedOrder = processOrderRequest(orderRequestDTO, username);
        toUpdate.getItems().clear();
        toUpdate.getItems().addAll(updatedOrder.orderItems());
        toUpdate.setTotalPrice(updatedOrder.totalPrice());
        toUpdate.setUsername(username);
        return orderRepository.save(toUpdate);
    }

    public void deleteOrder(Long orderId, String username) {
        Order toDelete = getOrderByIdForUser(orderId, username);
        orderRepository.deleteById(toDelete.getId());
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(order -> new OrderResponseDTO(
                        order.getId(),
                        order.getUsername(),
                        new ArrayList<>(order.getItems()),
                        order.getTotalPrice(),
                        System.currentTimeMillis()))
                .toList();

    }

    @Transactional(readOnly = true)
    public List<OrderResponseDTO> getAllOrdersByUsername(String username) {
        return orderRepository.findAllOrdersByUsername(username).stream()
                .map(order -> new OrderResponseDTO(
                        order.getId(),
                        order.getUsername(),
                        new ArrayList<>(order.getItems()),
                        order.getTotalPrice(),
                        System.currentTimeMillis()))
                .toList();
    }
}
