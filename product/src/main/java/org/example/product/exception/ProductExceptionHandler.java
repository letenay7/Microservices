package org.example.product.exception;

import org.example.product.dto.ProductErrorDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class ProductExceptionHandler {

    @ExceptionHandler(ProductServiceException.class)
    public ResponseEntity<ProductErrorDTO> handleProductServiceException(ProductServiceException ex) {
        ProductErrorDTO errorDTO = new ProductErrorDTO(
                HttpStatus.NOT_FOUND.value(), ex.getMessage(), System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorDTO);
    }

    @ExceptionHandler(ProductConflictException.class)
    public ResponseEntity<ProductErrorDTO> handleProductConflictException(ProductConflictException ex) {
        ProductErrorDTO errorDTO = new ProductErrorDTO(
                HttpStatus.CONFLICT.value(), ex.getMessage(), System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorDTO);
    }
}
