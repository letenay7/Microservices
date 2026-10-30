package org.example.order.controller;

import org.example.order.repository.OrderRepository;
import tools.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.example.order.dto.OrderItemDTO;
import org.example.order.dto.OrderRequestDTO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

// Explicit WireMock static imports to avoid wildcard conflicts
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

// Explicit MockMvc request builder static imports
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class OrderControllerIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    private static WireMockServer wireMockServer;
    private static String USER_ID;

    private String generateRandomUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(0);
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("product-service.url", wireMockServer::baseUrl);
    }

    @BeforeEach
    void Setup() {
        wireMockServer.resetAll();
        USER_ID = generateRandomUUID();
        orderRepository.deleteAll();
    }

    @Test
    void whenCreateOrder_thenSuccessfullyCallsProductServiceAndSavesOrder() throws Exception {
        // WireMock's 'get' is called explicitly using its class or by selecting imports carefully.
        // To prevent conflict, you can also prefix WireMock static calls with WireMock.get(...)
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/products/1"))
                .withHeader("X-Auth-Username", equalTo(USER_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"productId\": 1, \"name\": \"Laptop\", \"price\": 1200.00}")));

        OrderItemDTO itemRequest = new OrderItemDTO(1L, 2);
        OrderRequestDTO orderRequest = new OrderRequestDTO(List.of(itemRequest));

        mockMvc.perform(post("/api/orders")
                        .header("X-Auth-Username", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(USER_ID))
                .andExpect(jsonPath("$.totalPrice").value(2400.00))
                .andExpect(jsonPath("$.items[0].productId").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].price").value(1200.00));

        wireMockServer.verify(getRequestedFor(urlEqualTo("/api/products/1"))
                .withHeader("X-Auth-Username", equalTo(USER_ID)));
    }

    @Test
    void whenGetOrdersByUsername_thenReturnsOrderList() throws Exception {
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/products/1"))
                .withHeader("X-Auth-Username", equalTo(USER_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"productId\": 1, \"name\": \"Laptop\", \"price\": 1200.00}")));

        OrderItemDTO itemRequest = new OrderItemDTO(1L, 1);
        OrderRequestDTO orderRequest = new OrderRequestDTO(List.of(itemRequest));

        mockMvc.perform(post("/api/orders")
                        .header("X-Auth-Username", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated());

        // MockMvc GET request
        mockMvc.perform(get("/api/orders")
                        .header("X-Auth-Username", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].username").value(USER_ID));
    }

    @Test
    void whenUpdateOrder_thenSuccessfullyUpdatesAndReturnsOrder() throws Exception {
        // 1. Stub the Product Service response for the initial creation
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/products/1"))
                .withHeader("X-Auth-Username", equalTo(USER_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"productId\": 1, \"name\": \"Laptop\", \"price\": 1200.00}")));

        // 2. Create an initial order to obtain its generated ID
        OrderItemDTO initialItem = new OrderItemDTO(1L, 1);
        OrderRequestDTO initialRequest = new OrderRequestDTO(List.of(initialItem));

        String responseString = mockMvc.perform(post("/api/orders")
                        .header("X-Auth-Username", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(responseString).get("id").asLong();

        // 3. Stub the Product Service response for the update (if pricing/details are fetched again)
        wireMockServer.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/products/1"))
                .withHeader("X-Auth-Username", equalTo(USER_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"productId\": 1, \"name\": \"Laptop\", \"price\": 1200.00}")));

        // 4. Prepare the update payload (e.g., changing quantity to 3)
        OrderItemDTO updatedItem = new OrderItemDTO(1L, 3);
        OrderRequestDTO updateRequest = new OrderRequestDTO(List.of(updatedItem));

        // 5. Perform PUT request
        mockMvc.perform(put("/api/orders/{id}", orderId)
                        .header("X-Auth-Username", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                // 6. Assert response reflects updated quantity and calculated price
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.totalPrice").value(3600.00)) // 1200.00 * 3
                .andExpect(jsonPath("$.items[0].quantity").value(3));
    }

    @Test
    void whenDeleteOrder_thenSuccessfullyDeletesOrder() throws Exception {
        // 1. Stub the Product Service response for creation
        wireMockServer.stubFor(
                com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/api/products/1"))
                .withHeader("X-Auth-Username", equalTo(USER_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"productId\": 1, \"name\": \"Laptop\", \"price\": 1200.00}")));

        // 2. Create an initial order to get its ID
        OrderItemDTO itemRequest = new OrderItemDTO(1L, 1);
        OrderRequestDTO orderRequest = new OrderRequestDTO(List.of(itemRequest));

        String responseString = mockMvc.perform(post("/api/orders")
                        .header("X-Auth-Username", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(orderRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long orderId = objectMapper.readTree(responseString).get("id").asLong();


        // 3. Perform DELETE request
        mockMvc.perform(delete("/api/orders/{id}", orderId)
                        .header("X-Auth-Username", USER_ID))
                // 4. Assert successful deletion (change to isOk() if your controller returns 200 instead of 204)
                .andExpect(status().isNoContent());

        // 5. Verify the order is gone by fetching user orders and confirming empty state
        mockMvc.perform(get("/api/orders")
                        .header("X-Auth-Username", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }
}