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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Gaming Laptop"))
                .andExpect(jsonPath("$.price").value(1299.99))
                .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    @DisplayName("GET /api/products - Should return all created products")
    void shouldGetAllProducts() throws Exception {
        // Create 2 products
        ProductRequest product1 = new ProductRequest("Keyboard", "Mechanical", new BigDecimal("89.99"), 20);
        ProductRequest product2 = new ProductRequest("Mouse", "Wireless", new BigDecimal("49.99"), 15);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product1)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product2)))
                .andExpect(status().isCreated());

        // Fetch list
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("Keyboard"))
                .andExpect(jsonPath("$[1].name").value("Mouse"));
    }

    @Test
    @DisplayName("GET /api/products/{id} - Should fetch single product (and cache it)")
    void shouldGetProductById() throws Exception {
        ProductRequest request = new ProductRequest("4K Monitor", "32-inch UHD", new BigDecimal("450.00"), 8);

        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Extract generated ID from JSON response
        Long productId = objectMapper.readTree(response).get("id").asLong();

        // First call populates cache
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("4K Monitor"));

        // Second call reads directly from Redis
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("4K Monitor"));
    }

    @Test
    @DisplayName("DELETE /api/products/{id} - Should delete product from DB and evict cache")
    void shouldDeleteProduct() throws Exception {
        ProductRequest request = new ProductRequest("Temporary Item", "To be deleted", new BigDecimal("10.00"), 1);

        String response = mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long productId = objectMapper.readTree(response).get("id").asLong();

        // Execute DELETE
        mockMvc.perform(delete("/api/products/" + productId))
                .andExpect(status().isNoContent());

        // Verify entity no longer exists in Postgres repository
        assertFalse(productRepository.existsById(productId));
    }
}