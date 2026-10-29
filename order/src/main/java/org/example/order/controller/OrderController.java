package org.example.order.controller;

import org.example.order.dto.OrderRequestDTO;
import org.example.order.entity.Order;
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
    public ResponseEntity<Order> createOrder(
            @RequestBody OrderRequestDTO request,
            @RequestHeader("X-Auth-Username") String username) {

        Order createdOrder = orderService.createOrder(request, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdOrder);
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }


    @PutMapping("/{id}")
    public ResponseEntity<Order> updateOrder(@PathVariable Long id,
                                             @RequestHeader("X-Auth-Username") String username,
                                             @RequestBody OrderRequestDTO request) {
        Order updatedOrder = orderService.updateOrder(id, username, request);
        return ResponseEntity.ok(updatedOrder);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Order> deleteOrder(@PathVariable Long id,
                                             @RequestHeader("X-Auth-Username") String username) {
        orderService.deleteOrder(id, username);
        return ResponseEntity.noContent().build();
    }
}