package org.example.order.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.example.order.dto.ProductDTO;
import org.example.order.exception.ProductNotFoundException;
import org.example.order.exception.ProductServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient {
    private final RestClient restClient;

    public ProductClient(@Value("${product-service.url}") String productServiceUrl) {
        this.restClient = RestClient.builder().baseUrl(productServiceUrl).build();
    }
    @CircuitBreaker(name = "productService", fallbackMethod = "productUnavailable")
    @Retry(name = "productService")
    public ProductDTO getProductById(Long productId, String username) {
        return restClient.get()
                .uri("/api/products/{id}", productId)
                .header("X-Auth-Username", username) // Propagate identity downstream
                .retrieve()
                .onStatus(httpStatusCode -> httpStatusCode == HttpStatus.NOT_FOUND,
                        (request, response) -> {
                            throw new ProductNotFoundException(
                                    "Product with id: " + productId + " not found");
                        })
                .onStatus(HttpStatusCode::is5xxServerError,
                        (request, response) -> {
                            throw new ProductServiceException("Product service error");
                        })
                .body(ProductDTO.class);
    }

    private ProductDTO productUnavailable(Long productId, String username, Throwable t) {
        throw new ProductServiceException("Product service unavailable");
    }
}
