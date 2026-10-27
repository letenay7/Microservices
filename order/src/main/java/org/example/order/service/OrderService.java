package org.example.order.service;

import org.example.order.client.ProductClient;
import org.example.order.dto.OrderItemDTO;
import org.example.order.dto.OrderRequestDTO;
import org.example.order.dto.ProductDTO;
import org.example.order.entity.Order;
import org.example.order.entity.OrderItem;
import org.example.order.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    public OrderService(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }
    public Order createOrder(OrderRequestDTO orderRequestDTO, String username) {
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalPrice = new BigDecimal("0.00");
       for (OrderItemDTO item: orderRequestDTO.items()) {
           ProductDTO product =  productClient.getProductById(item.productId(),  username);

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

       Order order = new Order();
       order.setUsername(username);
       order.setItems(orderItems);
       order.setTotalPrice(totalPrice);
       return orderRepository.save(order);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }
    public List<Order> getAllOrdersByUsername(String username) {
        return orderRepository.findAllOrdersByUsername(username);
    }
}
