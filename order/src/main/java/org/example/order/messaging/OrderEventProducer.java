package org.example.order.messaging;

import org.example.order.event.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventProducer {

    private final KafkaTemplate<String, OrderCreatedEvent> orderCreatedTemplate;
    private final KafkaTemplate<String, OrderStatusChangedEvent> statusChangedTemplate;
    private static final String CREATED = "order-created-events";
    private static final String STATUS = "order-status-events";

    public OrderEventProducer(KafkaTemplate<String, OrderCreatedEvent> orderCreatedTemplate,
                              KafkaTemplate<String, OrderStatusChangedEvent> statusChangedTemplate) {
        this.orderCreatedTemplate = orderCreatedTemplate;
        this.statusChangedTemplate = statusChangedTemplate;
    }

    public void sendOrderCreatedEvent(OrderCreatedEvent event) {
        orderCreatedTemplate.send(CREATED, event.username(), event);
    }

    public void sendOrderStatusChangedEvent(OrderStatusChangedEvent event) {
        statusChangedTemplate.send(STATUS, String.valueOf(event.orderId()), event);
    }
}