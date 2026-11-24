package org.example.order.messaging;

import lombok.extern.slf4j.Slf4j;
import org.example.order.event.OrderStatusChangedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Slf4j
@Component
public class OrderEventConsumer {

    private static final String TOPIC = "order-status-events";

    @KafkaListener(topics = TOPIC, groupId = "order-service-group")
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        log.info("Received status change: orderId={}, previou={}, new={}",
                event.orderId(), event.prevStatus(), event.newStatus());
        // React to the event — e.g., update audit log, trigger notifications, etc.
    }
}
