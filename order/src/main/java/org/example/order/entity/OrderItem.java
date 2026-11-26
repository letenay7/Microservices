package org.example.order.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(nullable = false)
    private Long productId;

    @NotNull
    @Column(nullable = false)
    private String productName;

    @NotNull
    @Column(nullable = false)
    private Integer quantity;

    @NotNull
    @Column(nullable = false)
    private BigDecimal price;
}
