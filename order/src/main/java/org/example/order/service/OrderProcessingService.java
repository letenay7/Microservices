package org.example.order.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.example.order.client.ProductClient;
import org.example.order.dto.ProductDTO;
import org.example.order.exception.ProductServiceException;
import org.springframework.stereotype.Service;

@Service
public class OrderProcessingService {

    private final ProductClient productClient;

    public OrderProcessingService(ProductClient productClient) {
        this.productClient = productClient;
    }

    @CircuitBreaker(name = "productService", fallbackMethod = "getProductFallback")
    public ProductDTO fetchProduct(Long productId, String username) {
        return productClient.getProductById(productId, username);
    }

    // Fallback method must match the exact parameter signature plus an Exception or Throwable
    private ProductDTO getProductFallback(Long productId, String username, Throwable t) {
        // Log the error and return a safe fallback or throw a custom business exception
        throw new ProductServiceException(
                "Product service is currently unavailable. Please try again later.");
    }
}