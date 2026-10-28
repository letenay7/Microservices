package org.example.order.client;

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
}
