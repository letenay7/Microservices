package org.example.product.service;

import org.example.product.domain.Product;
import org.example.product.dto.ProductRequest;
import org.example.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Cacheable(value = "products", key = "#id")
    public Product findById(Long id) {
        return productRepository.findById(id).orElseThrow(()
                -> new RuntimeException("No product found with id " + id));
    }

    public Product saveProduct(ProductRequest request) {
        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .price(request.price())
                .quantity(request.quantity())
                .build();
        return productRepository.save(product);
    }

    @CacheEvict(value = "products", key = "#id")
    public Product updateProduct(Long id, ProductRequest request) {
        Product toUpdate = findById(id);
        toUpdate.setName(request.name());
        toUpdate.setDescription(request.description());
        toUpdate.setPrice(request.price());
        toUpdate.setQuantity(request.quantity());
        return productRepository.save(toUpdate);
    }

    @CacheEvict(value = "products", key = "#id")
    public void deleteProduct(Long id) {
        Product toDelete = findById(id);
        if (toDelete == null) {
            throw new RuntimeException("No product found with id " + id);
        }
        productRepository.deleteById(id);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
}
