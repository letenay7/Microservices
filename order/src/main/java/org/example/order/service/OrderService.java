package org.example.order.service;

import org.example.order.client.ProductClient;
import org.example.order.dto.OrderItemDTO;
import org.example.order.dto.OrderProcessDTO;
import org.example.order.dto.OrderRequestDTO;
import org.example.order.dto.ProductDTO;
import org.example.order.entity.Order;
import org.example.order.entity.OrderItem;
import org.example.order.exception.CannotModifyOrderException;
import org.example.order.exception.OrderNotFoundException;
import org.example.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderService(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    public Order getOrderById(Long orderId)  {
        return  orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found"));

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

    public Order createOrder(OrderRequestDTO orderRequestDTO, String username) {
        OrderProcessDTO processed = processOrderRequest(orderRequestDTO, username);
        Order order = new Order();
        order.setUsername(username);
        order.setItems(processed.orderItems());
        order.setTotalPrice(processed.totalPrice());
        return orderRepository.save(order);
    }

    public Order updateOrder(Long orderId, String username, OrderRequestDTO orderRequestDTO) {
        Order toUpdate = getOrderById(orderId);
        if (toUpdate == null) {
            throw new OrderNotFoundException("Order with id " + orderId + " was not found");
        }
        if (!toUpdate.getUsername().equals(username)) {
            throw new CannotModifyOrderException("Username doesn't match with requested username");
        }
        OrderProcessDTO updatedOrder = processOrderRequest(orderRequestDTO, username);
        toUpdate.getItems().clear();
        toUpdate.getItems().addAll(updatedOrder.orderItems());
        toUpdate.setTotalPrice(updatedOrder.totalPrice());
        toUpdate.setUsername(username);
        return orderRepository.save(toUpdate);

    }
    public void deleteOrder(Long orderId, String username) {
        Order toDelete = getOrderById(orderId);
        if (toDelete == null) {
            throw new OrderNotFoundException("Order with id " + orderId + " was not found");
        }
        if (!toDelete.getUsername().equals(username)) {
            throw new CannotModifyOrderException("Username doesn't match with requested username");
        }
        orderRepository.deleteById(orderId);
    }
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public List<Order> getAllOrdersByUsername(String username) {
        return orderRepository.findAllOrdersByUsername(username);
    }
}
