package org.example.order.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static jakarta.persistence.EnumType.STRING;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String username; // Captured from X-Auth-Username header

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "order_id", referencedColumnName = "id")
    @NotNull
    private List<OrderItem> items;

    @Column(nullable = false)
    @NotNull
    private BigDecimal totalPrice;

    @Enumerated(STRING)
    @Column(nullable = false)
    @NotNull
    private OrderStatus status;

    @Column(nullable = false)
    @NotNull
    private Instant createdAt;

    @Column(nullable = false)
    @NotNull
    private Instant updatedAt;

    @Column(nullable = false)
    @NotNull
    @Builder.Default
    private List<OrderStatus> orderHistory = new ArrayList<>();

    public void addStatusToHistory(OrderStatus orderStatus) {
        this.orderHistory.add(orderStatus);
    }
}
