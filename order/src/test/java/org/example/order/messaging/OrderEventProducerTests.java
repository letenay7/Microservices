package org.example.order.messaging;

import org.example.order.entity.OrderStatus;
import org.example.order.event.OrderCreatedEvent;
import org.example.order.event.OrderStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderEventProducerTests {

    @Mock
    private KafkaTemplate<String, OrderCreatedEvent> orderCreatedTemplate;

    @Mock
    private KafkaTemplate<String, OrderStatusChangedEvent> statusChangedTemplate;

    @Test
    void sendOrderStatusChangedEvent_sendsToOrderStatusEventsUsingOrderIdAsKey() {
        OrderEventProducer producer = new OrderEventProducer(orderCreatedTemplate, statusChangedTemplate);
        OrderStatusChangedEvent event = new OrderStatusChangedEvent(
                42L, "alice", OrderStatus.PENDING, OrderStatus.CONFIRMED);

        producer.sendOrderStatusChangedEvent(event);

        verify(statusChangedTemplate).send("order-status-events", "42", event);
        verifyNoInteractions(orderCreatedTemplate);
    }
}