package org.example.order.repository;

import org.example.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByUsername(String username);
    List<Order> findAllOrdersByUsername(String username);
}
