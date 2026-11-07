package org.example.order.controller;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.example.order.client.ProductClient;
import org.example.order.dto.OrderItemDTO;
import org.example.order.dto.OrderRequestDTO;
import org.example.order.event.OrderCreatedEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderResilienceIntegrationTests {

    private static final String USER = "resilience-user";
    private static final String CB_NAME = "productService";

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
        registry.add("product-service.url", wireMockServer::baseUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private ProductClient productClient;

    // Without this (or @EmbeddedKafka) the producer blocks ~60s trying to reach localhost:9092.
    @MockitoBean
    private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @BeforeEach
    void resetState() {
        wireMockServer.resetAll();
        // circuit breaker state is shared by every test in this context, so always start CLOSED
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
    }

    // find() instead of circuitBreaker(name): the latter silently creates a default instance
    // when the name is wrong, which would hide a misconfigured test
    private CircuitBreaker breaker() {
        return circuitBreakerRegistry.find(CB_NAME).orElseThrow(() -> new AssertionError(
                "No circuit breaker named '" + CB_NAME + "'. Existing: "
                        + circuitBreakerRegistry.getAllCircuitBreakers().stream().map(CircuitBreaker::getName).toList()));
    }

    private String orderJson() throws Exception {
        return objectMapper.writeValueAsString(
                new OrderRequestDTO(List.of(new OrderItemDTO(1L, 2))));
    }

    private void stubProduct(int status, String body) {
        var response = aResponse().withStatus(status);
        if (body != null) {
            response.withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE).withBody(body);
        }
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock
                .get(urlEqualTo("/api/products/1")).willReturn(response));
    }

    private static final String PRODUCT_JSON =
            "{\"productId\": 1, \"name\": \"Wireless Mouse\", \"price\": 49.99}";

    /** Fires a create-order request and ignores the outcome (errors are expected here). */
    private void fireAndIgnore() {
        try {
            mockMvc.perform(post("/api/orders")
                    .header("X-Auth-Username", USER)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(orderJson()));
        } catch (Exception ignored) {
            // MockMvc rethrows exceptions no @ExceptionHandler maps to a response
        }
    }



    @Test
    void whenProductServiceIsHealthy_thenOrderIsCreatedAndCircuitStaysClosed() throws Exception {
        stubProduct(200, PRODUCT_JSON);

        mockMvc.perform(post("/api/orders")
                        .header("X-Auth-Username", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalPrice").value(99.98));

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void whenProductServiceFails_thenOrderIsRejected() throws Exception {
        stubProduct(500, null);

        // Replace is5xxServerError() with the exact status your handler returns (e.g. 503)
        mockMvc.perform(post("/api/orders")
                        .header("X-Auth-Username", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson()))
                .andExpect(status().isInternalServerError());

        // >1 means retries ran; once you know max-attempts, verify the exact count instead
        wireMockServer.verify(moreThanOrExactly(1), getRequestedFor(urlEqualTo("/api/products/1")));
    }

    @Test
    void afterRepeatedFailures_circuitOpensAndStopsCallingProductService() {
        stubProduct(500, null);

        for (int i = 0; i < 10; i++) {
            fireAndIgnore();
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // open circuit: the next request must fail fast without touching the product service
        wireMockServer.resetRequests();
        fireAndIgnore();
        wireMockServer.verify(0, getRequestedFor(urlEqualTo("/api/products/1")));
    }

    @Test
    void whenCircuitIsHalfOpenAndProductServiceRecovers_thenCircuitClosesAgain() throws Exception {
        stubProduct(500, null);
        for (int i = 0; i < 10; i++) {
            fireAndIgnore();
        }
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // skip the 60s wait: move straight to HALF_OPEN, as if wait-duration-in-open-state had elapsed
        breaker().transitionToHalfOpenState();
        stubProduct(200, PRODUCT_JSON);

        mockMvc.perform(post("/api/orders")
                        .header("X-Auth-Username", USER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orderJson()))
                .andExpect(status().isCreated());

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}