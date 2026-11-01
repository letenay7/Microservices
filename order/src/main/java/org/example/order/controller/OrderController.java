package org.example.order.controller;

import org.example.order.dto.OrderRequestDTO;
import org.example.order.dto.OrderResponseDTO;
import org.example.order.entity.Order;
import org.example.order.exception.CannotModifyOrderException;
import org.example.order.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponseDTO> createOrder(
            @RequestBody OrderRequestDTO request,
            @RequestHeader("X-Auth-Username") String username) {

        Order createdOrder = orderService.createOrder(request, username);
        OrderResponseDTO response = new OrderResponseDTO(createdOrder.getId(),
                createdOrder.getUsername(), createdOrder.getItems(),
                createdOrder.getTotalPrice(), System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/id")
    public ResponseEntity<OrderResponseDTO> getOrderById(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        OrderResponseDTO response = new OrderResponseDTO(order.getId(), order.getUsername(),
                order.getItems(), order.getTotalPrice(), System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    @GetMapping(params = "username")
    public ResponseEntity<List<Order>> getAllOrdersByUsername(
            @RequestHeader("X-Auth-Username") String username,
            @RequestParam("username") String paramUsername) {
        if (!username.equals(paramUsername)) {
            throw new CannotModifyOrderException("Invalid username");
        }
        List<Order> orders = orderService.getAllOrdersByUsername(paramUsername);
        return ResponseEntity.ok(orders);
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> updateOrder(@PathVariable Long id,
                                             @RequestHeader("X-Auth-Username") String username,
                                             @RequestBody OrderRequestDTO request) {
        Order updatedOrder = orderService.updateOrder(id, username, request);
        OrderResponseDTO response = new OrderResponseDTO(updatedOrder.getId(),
                updatedOrder.getUsername(), updatedOrder.getItems(),
                updatedOrder.getTotalPrice(), System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Order> deleteOrder(@PathVariable Long id,
                                             @RequestHeader("X-Auth-Username") String username) {
        orderService.deleteOrder(id, username);
        return ResponseEntity.noContent().build();
    }
}