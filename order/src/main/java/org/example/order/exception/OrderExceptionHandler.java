package org.example.order.exception;

import org.example.order.dto.OrderErrorResponseDTO;
import org.example.order.dto.ProductErrorResponseDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class OrderExceptionHandler  {
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ProductErrorResponseDTO> handleProductNotFoundException(
            ProductNotFoundException ex) {
        ProductErrorResponseDTO errorResponseDTO = new ProductErrorResponseDTO(
                HttpStatus.NOT_FOUND.value(), ex.getMessage(), System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponseDTO);
    }

    @ExceptionHandler(ProductServiceException.class)
    public ResponseEntity<ProductErrorResponseDTO> handleProductServiceException(
            ProductServiceException ex) {
        ProductErrorResponseDTO errorResponseDTO = new ProductErrorResponseDTO(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), ex.getMessage(), System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponseDTO);
    }

    @ExceptionHandler(CannotModifyOrderException.class)
    public ResponseEntity<OrderErrorResponseDTO> handleCannotModifyOrderException(
            CannotModifyOrderException ex) {
        OrderErrorResponseDTO errorResponseDTO = new OrderErrorResponseDTO(
                HttpStatus.CONFLICT.value(), ex.getMessage(), System.currentTimeMillis());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponseDTO);
    }
}
