package org.example.product.controller;

import org.example.product.domain.Product;
import org.example.product.dto.ProductRequest;
import org.example.product.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/products")
public class ProductController {
    @Autowired
    private ProductService productService;

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts(
            @RequestHeader(value = "X-Auth-Username", defaultValue = "empty") String username) {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id,
                                                  @RequestHeader(
                                                          value = "X-Auth-Username",
                                                          defaultValue = "empty")
                                                  String username) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody ProductRequest productRequest,
                                                 @RequestHeader("X-Auth-Username") String username) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(productService.saveProduct(productRequest, username));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProductById(@PathVariable Long id,
                                                  @RequestHeader(value = "X-Auth-Username",
                                                          defaultValue = "empty") String username) {
        try {
            productService.deleteProduct(id);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProductById(@PathVariable Long id,
                                                     @RequestBody ProductRequest productRequest,
                                                     @RequestHeader("X-Auth-Username") String username) {
        try {
            Product updated = productService.updateProduct(id, productRequest, username);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PutMapping("update-quantity/{id}")
    public ResponseEntity<Product> updateProductQuantity(@PathVariable Long id,
                                                         @RequestBody ProductRequest productRequest,
                                                         @RequestHeader("X-Auth-Username") String username) {
        try {
            Product toUpdate = productService.findById(id);
            if (toUpdate != null) {
                Product updated = productService.updateProductQuantity(
                        toUpdate, productRequest, username);
                return ResponseEntity.ok(updated);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
