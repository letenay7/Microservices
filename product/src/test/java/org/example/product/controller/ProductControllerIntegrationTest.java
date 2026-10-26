package org.example.product.controller;

import org.example.product.BaseIntegrationTest;
import org.example.product.dto.ProductRequest;
import org.example.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ProductControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String generateRandomUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/products - Should create a product and save to Postgres")
    void shouldCreateProduct() throws Exception {
        ProductRequest request = new ProductRequest(
                "Gaming Laptop",
                "High performance laptop",
                new BigDecimal("1299.99"),
                5
        );

        String uuid = generateRandomUUID();

        mockMvc.perform(post("/api/products")
                        .header("X-Auth-Username", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Gaming Laptop"))
                .andExpect(jsonPath("$.price").value(1299.99))
                .andExpect(jsonPath("$.quantity").value(5)).
                andExpect(jsonPath("$.createdBy").value(uuid));
    }

    @Test
    @DisplayName("GET /api/products - Should return all created products")
    void shouldGetAllProducts() throws Exception {
        ProductRequest product1 = new ProductRequest(
                "Keyboard", "Mechanical", new BigDecimal("89.99"), 20);
        ProductRequest product2 = new ProductRequest(
                "Mouse", "Wireless", new BigDecimal("49.99"), 15);

        String uuid = generateRandomUUID();

        mockMvc.perform(post("/api/products")
                        .header("X-Auth-Username", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products")
                        .header("X-Auth-Username", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product2)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Keyboard"))
                .andExpect(jsonPath("$[1].name").value("Mouse"));
    }

    @Test
    @DisplayName("GET /api/products/{id} - Should fetch single product (and cache it)")
    void shouldGetProductById() throws Exception {
        ProductRequest request = new ProductRequest(
                "4K Monitor", "32-inch UHD", new BigDecimal("450.00"), 8);
        String uuid = generateRandomUUID();

        String response = mockMvc.perform(post("/api/products")
                        .header("X-Auth-Username", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long productId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("4K Monitor"));

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("32-inch UHD"));
    }

    @Test
    @DisplayName("DELETE /api/products/{id} - Should delete product from DB and evict cache")
    void shouldDeleteProduct() throws Exception {
        ProductRequest request = new ProductRequest(
                "Temporary Item", "To be deleted", new BigDecimal("10.00"), 1);

        String uuid = generateRandomUUID();

        String response = mockMvc.perform(post("/api/products")
                        .header("X-Auth-Username", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long productId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(delete("/api/products/" + productId))
                .andExpect(status().isNoContent());

        assertFalse(productRepository.existsById(productId));
    }

    @Test
    @DisplayName("PUT should update product")
    void shouldUpdateProduct() throws Exception {
        ProductRequest request = new ProductRequest(
                "Keyboard", "Mechanical", new BigDecimal("89.99"), 20);
        String uuid = generateRandomUUID();
        String response = mockMvc.perform(post("/api/products")
                        .header("X-Auth-Username", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long productId = objectMapper.readTree(response).get("id").asLong();

        ProductRequest request2 = new ProductRequest(
                "Keyboard", "Mechanical", new BigDecimal("69.99"), 20);
        mockMvc.perform(put("/api/products/" + productId)
                        .header("X-Auth-Username", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(69.99));

    }

    @Test
    @DisplayName("PUT Should update product quantity")
    void shouldUpdateProductQuantity() throws Exception {
        ProductRequest request = new ProductRequest(
                "Mouse", "Mechanical", new BigDecimal("89.99"), 20);
        String uuid = generateRandomUUID();

        String response = mockMvc.perform(post("/api/products")
                        .header("X-Auth-Username", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long productId = objectMapper.readTree(response).get("id").asLong();

        ProductRequest request2 = new ProductRequest(
                "Mouse", "Mechanical", new BigDecimal("89.99"), 5);

        mockMvc.perform(put("/api/products/update-quantity/" + productId)
                        .header("X-Auth-Username", uuid)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(25));
    }
}