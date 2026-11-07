package org.example.order.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.example.order.dto.OrderItemDTO;
import org.example.order.dto.OrderRequestDTO;
import org.example.order.event.OrderCreatedEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// bootstrapServersProperty makes the embedded broker set spring.kafka.bootstrap-servers itself,
// so it does not need to be passed into @DynamicPropertySource (which only accepts a registry).
@SpringBootTest
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1, topics = {"order-events"}
)
@DirtiesContext
@ActiveProfiles("test")
class OrderKafkaIntegrationTest {

    private static final String USER = "kafka-test-user";

    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
                    .withDatabaseName("order_db")
                    .withUsername("test")
                    .withPassword("test");

    static WireMockServer wireMockServer;

    @BeforeAll
    static void startInfrastructure() {
        postgres.start();
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void stopInfrastructure() {
        wireMockServer.stop();
        postgres.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // the order service calls the product service when creating an order
        registry.add("product-service.url", wireMockServer::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaMessageListenerContainer<String, OrderCreatedEvent> container;
    private BlockingQueue<ConsumerRecord<String, OrderCreatedEvent>> records;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/products/1"))
                .withHeader("X-Auth-Username", equalTo(USER))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"productId\": 1, \"name\": \"Laptop\", \"price\": 1200.00}")));

        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps(embeddedKafkaBroker, "test", true);

        // trusted packages "*" so the deserializer accepts the event type
        JacksonJsonDeserializer<OrderCreatedEvent> valueDeserializer =
                new JacksonJsonDeserializer<>(OrderCreatedEvent.class, false);
        valueDeserializer.addTrustedPackages("*");

        DefaultKafkaConsumerFactory<String, OrderCreatedEvent> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps, new StringDeserializer(), valueDeserializer);

        container = new KafkaMessageListenerContainer<>(consumerFactory, new ContainerProperties("order-events"));
        records = new LinkedBlockingQueue<>();
        container.setupMessageListener((MessageListener<String, OrderCreatedEvent>) records::add);
        container.start();

        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @AfterEach
    void tearDown() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void whenOrderCreated_thenEventIsPublishedToKafka() throws Exception {
        OrderRequestDTO requestDTO = new OrderRequestDTO(List.of(new OrderItemDTO(1L, 2)));

        mockMvc.perform(post("/api/orders")
                        .header("X-Auth-Username", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDTO)))
                .andExpect(status().isCreated());

        ConsumerRecord<String, OrderCreatedEvent> consumed = records.poll(10, TimeUnit.SECONDS);
        assertThat(consumed).as("event published to order-events").isNotNull();

        OrderCreatedEvent event = consumed.value();
        // if OrderCreatedEvent is a record, use username() / items() instead of the getters
        assertThat(event.username()).isEqualTo(USER);
        assertThat(event.orderItems()).hasSize(1);
    }
}