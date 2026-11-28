package org.example.order.controller;

import org.example.order.dto.OrderItemDTO;
import org.example.order.dto.OrderRequestDTO;
import org.example.order.dto.OrderResponseDTO;
import org.example.order.dto.OrderStatusUpdateDTO;
import org.example.order.entity.Order;
import org.example.order.entity.OrderItem;
import org.example.order.entity.OrderStatus;
import org.example.order.exception.CannotModifyOrderException;
import org.example.order.exception.OrderNotFoundException;
import org.example.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice test: only the controller (plus any @ControllerAdvice) is loaded.
 * No database, Kafka or WireMock, so it runs in milliseconds.
 * Business logic is covered in OrderServiceTest; end-to-end flow in OrderControllerIntegrationTests.
 */
@WebMvcTest(OrderController.class)
class OrderControllerTests {

    private static final String HEADER = "X-Auth-Username";
    private static String USER;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    private String generateRandomUUID() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @BeforeEach
    void setUSER() {
        USER = generateRandomUUID();

    }

    // ---------- helpers ----------

    private OrderRequestDTO requestOf(long productId, int quantity) {
        return new OrderRequestDTO(List.of(new OrderItemDTO(productId, quantity)));
    }

    private String json(Object o) {
        return objectMapper.writeValueAsString(o);
    }

    private OrderItem item(long productId, int quantity, BigDecimal price) {
        OrderItem item = new OrderItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setPrice(price);
        return item;
    }

    private OrderResponseDTO responseDto(long id, String username, BigDecimal total, OrderItem... items) {
        return new OrderResponseDTO(id, username, new ArrayList<>(List.of(items)),
                total, null, System.currentTimeMillis());
    }

    private OrderResponseDTO confirmedResponseDto(long id, String username, BigDecimal total,
                                                  OrderItem... items) {
        return new OrderResponseDTO(id, username, new ArrayList<>(List.of(items)),
                total, OrderStatus.CONFIRMED, System.currentTimeMillis());
    }

    private Order orderEntity(long id, String username, String total, OrderItem... items) {
        Order order = new Order();
        order.setId(id);
        order.setUsername(username);
        order.setItems(new ArrayList<>(List.of(items)));
        order.setTotalPrice(new BigDecimal(total));
        return order;
    }

    // ---------- POST /api/orders ----------

    @Nested
    class CreateOrder {

        @Test
        void validRequest_returns201AndBody() throws Exception {
            OrderRequestDTO request = requestOf(1L, 2);
            when(orderService.createOrder(eq(request), eq(USER)))
                    .thenReturn(
                            responseDto(7,  USER, new BigDecimal("2400.00"),
                            item(1, 2, new BigDecimal("1200.00"))));

            mockMvc.perform(post("/api/orders")
                            .header(HEADER, USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.orderId").value(7))
                    .andExpect(jsonPath("$.username").value(USER))
                    .andExpect(jsonPath("$.totalPrice").value(2400.00))
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].productId").value(1))
                    .andExpect(jsonPath("$.items[0].quantity").value(2));

            verify(orderService).createOrder(request, USER);
        }

        @Test
        void missingAuthHeader_returns400() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(requestOf(1L, 1))))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }

        @Test
        void malformedJson_returns400() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .header(HEADER, USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ not valid json"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }

        @Test
        void missingBody_returns400() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .header(HEADER, USER)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }

        @Test
        void wrongContentType_returns415() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .header(HEADER, USER)
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("hello"))
                    .andExpect(status().isUnsupportedMediaType());
        }
    }

    // ---------- GET /api/orders ----------

    @Nested
    class ListOrders {

        @Test
        void noOrders_returnsEmptyArray() throws Exception {
            when(orderService.getAllOrdersByUsername(USER)).thenReturn(List.of());

            mockMvc.perform(get("/api/orders").header(HEADER, USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void returnsOnlyCallersOrders() throws Exception {
            when(orderService.getAllOrdersByUsername(USER))
                    .thenReturn(
                            List.of(responseDto(1, USER, new BigDecimal("10.00"),
                                            item(1, 1, new BigDecimal("10.00"))),
                                    responseDto(2, USER, new BigDecimal("21.00"),
                                            item(4, 3, new BigDecimal("7.00")))));

            mockMvc.perform(get("/api/orders").header(HEADER, USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0].username").value(USER))
                    .andExpect(jsonPath("$[0].orderId").value(1))
                    .andExpect(jsonPath("$[1].orderId").value(2))
                    .andExpect(jsonPath("$[1].totalPrice").value(21.00))
                    .andExpect(jsonPath("$[1].items[0].productId").value(4))
                    .andExpect(jsonPath("$[1].items[0].quantity").value(3));
        }

        @Test
        void byUsername_matchingHeader_returnsOrders() throws Exception {
            when(orderService.getAllOrdersByUsername(USER))
                    .thenReturn(
                            List.of(responseDto(3, USER, new BigDecimal("14.00"),
                                    item(8, 4, new BigDecimal("3.50")))));

            mockMvc.perform(get("/api/orders")
                            .param("username", USER)
                            .header(HEADER, USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].username").value(USER))
                    .andExpect(jsonPath("$[0].orderId").value(3))
                    .andExpect(jsonPath("$[0].totalPrice").value(14.00))
                    .andExpect(jsonPath("$[0].items[0].productId").value(8))
                    .andExpect(jsonPath("$[0].items[0].quantity").value(4));
        }

        @Test
        void byUsername_differentFromHeader_returns409AndNeverQueries() throws Exception {
            mockMvc.perform(get("/api/orders")
                            .param("username", "mallory")
                            .header(HEADER, USER))
                    .andExpect(status().isConflict());

            verify(orderService, never()).getAllOrdersByUsername(any());
        }

        @Test
        void byUsername_missingHeader_returns400() throws Exception {
            mockMvc.perform(get("/api/orders").param("username", USER))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------- GET /api/orders/{id} ----------
    // NOTE: fails until @GetMapping("/id") is changed to @GetMapping("/{id}") in the controller.

    @Nested
    class GetById {

        @Test
        void existingOrder_returns200() throws Exception {
            when(orderService.getOrderByIdForUser(5L, USER))
                    .thenReturn(orderEntity(5, USER, "21.00",
                            item(2, 3, new BigDecimal("7.00"))));

            mockMvc.perform(get("/api/orders/{id}", 5).header(HEADER, USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(5))
                    .andExpect(jsonPath("$.username").value(USER))
                    .andExpect(jsonPath("$.totalPrice").value(21.00))
                    .andExpect(jsonPath("$.items[0].productId").value(2))
                    .andExpect(jsonPath("$.items[0].quantity").value(3));

            verify(orderService).getOrderByIdForUser(5L, USER);
        }

        @Test
        void unknownOrder_returns404() throws Exception {
            when(orderService.getOrderByIdForUser(99L, USER))
                    .thenThrow(new OrderNotFoundException("Order not found"));

            mockMvc.perform(get("/api/orders/{id}", 99).header(HEADER, USER))
                    .andExpect(status().isNotFound());
        }

        @Test
        void nonNumericId_returns400() throws Exception {
            mockMvc.perform(get("/api/orders/{id}", "abc").header(HEADER, USER))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void someoneElsesOrder_returns404() throws Exception {
            when(orderService.getOrderByIdForUser(5L, USER))
                    .thenThrow(new OrderNotFoundException("Order not found"));

            mockMvc.perform(get("/api/orders/{id}", 5).header(HEADER, USER))
                    .andExpect(status().isNotFound());
        }
    }

    // ---------- PUT /api/orders/{id} ----------

    @Nested
    class UpdateOrder {

        @Test
        void validRequest_returns200WithUpdatedOrder() throws Exception {
            OrderRequestDTO request = requestOf(6L, 3);
            when(orderService.updateOrder(eq(3L), eq(USER), eq(request)))
                    .thenReturn(orderEntity(3, USER, "3600.00",
                            item(6, 3, new BigDecimal("1200.00"))));

            mockMvc.perform(put("/api/orders/{id}", 3)
                            .header(HEADER, USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(3))
                    .andExpect(jsonPath("$.totalPrice").value(3600.00))
                    .andExpect(jsonPath("$.items[0].productId").value(6))
                    .andExpect(jsonPath("$.items[0].quantity").value(3));
        }

        @Test
        void unknownOrder_returns404() throws Exception {
            when(orderService.updateOrder(eq(99L), eq(USER), any()))
                    .thenThrow(new OrderNotFoundException("not found"));

            mockMvc.perform(put("/api/orders/{id}", 99)
                            .header(HEADER, USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(requestOf(1L, 1))))
                    .andExpect(status().isNotFound());
        }

        @Test
        void someoneElsesOrder_returns409() throws Exception {
            when(orderService.updateOrder(eq(5L), eq(USER), any()))
                    .thenThrow(new CannotModifyOrderException("not yours"));

            mockMvc.perform(put("/api/orders/{id}", 5)
                            .header(HEADER, USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(requestOf(1L, 1))))
                    .andExpect(status().isConflict());
        }

        @Test
        void missingAuthHeader_returns400() throws Exception {
            mockMvc.perform(put("/api/orders/{id}", 5)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(requestOf(1L, 1))))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }
    }

    // ---------- GET /api/orders (with status filter) ----------

    @Nested
    class ListOrdersByStatus {

        @Test
        void withStatusFilter_returnsFilteredOrders() throws Exception {
            when(orderService.getOrdersByUsernameAndStatus(eq(USER), eq(OrderStatus.CONFIRMED)))
                    .thenReturn(List.of(
                            confirmedResponseDto(8, USER, new BigDecimal("30.00"),
                                    item(3, 4, new BigDecimal("7.50")))
                    ));

            mockMvc.perform(get("/api/orders")
                            .param("status", "CONFIRMED")
                            .header(HEADER, USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].orderId").value(8))
                    .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
        }

        @Test
        void withStatusFilter_noMatch_returnsEmptyArray() throws Exception {
            when(orderService.getOrdersByUsernameAndStatus(eq(USER), eq(OrderStatus.SHIPPED)))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/orders")
                            .param("status", "SHIPPED")
                            .header(HEADER, USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void withInvalidStatus_returns400() throws Exception {
            mockMvc.perform(get("/api/orders")
                            .param("status", "INVALID_STATUS")
                            .header(HEADER, USER))
                    .andExpect(status().isBadRequest());
        }
    }

    // ---------- PATCH /api/orders/{id}/status ----------

    @Nested
    class UpdateStatus {

        @Test
        void validTransition_returns200WithUpdatedStatus() throws Exception {
            OrderStatusUpdateDTO request = new OrderStatusUpdateDTO(OrderStatus.CONFIRMED);
            when(orderService.updateStatus(eq(5L), eq(USER), eq(OrderStatus.CONFIRMED)))
                    .thenReturn(confirmedResponseDto(5, USER, new BigDecimal("10.00"),
                            item(9, 2, new BigDecimal("5.00"))));

            mockMvc.perform(patch("/api/orders/{id}/status", 5)
                            .header(HEADER, USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value(5))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.items[0].productId").value(9))
                    .andExpect(jsonPath("$.items[0].quantity").value(2));

            verify(orderService).updateStatus(5L, USER, OrderStatus.CONFIRMED);
        }

        @Test
        void invalidTransition_returns409() throws Exception {
            OrderStatusUpdateDTO request = new OrderStatusUpdateDTO(OrderStatus.SHIPPED);
            when(orderService.updateStatus(eq(5L), eq(USER), eq(OrderStatus.SHIPPED)))
                    .thenThrow(new CannotModifyOrderException("Cannot transition order from PENDING to SHIPPED"));

            mockMvc.perform(patch("/api/orders/{id}/status", 5)
                            .header(HEADER, USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Cannot transition order from PENDING to SHIPPED"));
        }

        @Test
        void unknownOrder_returns404() throws Exception {
            OrderStatusUpdateDTO request = new OrderStatusUpdateDTO(OrderStatus.CONFIRMED);
            when(orderService.updateStatus(eq(99L), eq(USER), eq(OrderStatus.CONFIRMED)))
                    .thenThrow(new OrderNotFoundException("Order not found"));

            mockMvc.perform(patch("/api/orders/{id}/status", 99)
                            .header(HEADER, USER)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request)))
                    .andExpect(status().isNotFound());
        }

        @Test
        void missingAuthHeader_returns400() throws Exception {
            OrderStatusUpdateDTO request = new OrderStatusUpdateDTO(OrderStatus.CONFIRMED);

            mockMvc.perform(patch("/api/orders/{id}/status", 5)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(request)))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }

        @Test
        void missingBody_returns400() throws Exception {
            mockMvc.perform(patch("/api/orders/{id}/status", 5)
                            .header(HEADER, USER)
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }
    }

    // ---------- GET /api/orders/{id}/history ----------

    @Nested
    class GetOrderHistory {

        @Test
        void returnsHistoryForOrderOwner() throws Exception {
            when(orderService.getOrderHistory(5L, USER))
                    .thenReturn(List.of(OrderStatus.PENDING, OrderStatus.CONFIRMED));

            mockMvc.perform(get("/api/orders/{id}/history", 5).header(HEADER, USER))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[0]").value("PENDING"))
                    .andExpect(jsonPath("$[1]").value("CONFIRMED"));

            verify(orderService).getOrderHistory(5L, USER);
        }
    }

    // ---------- DELETE /api/orders/{id} ----------

    @Nested
    class DeleteOrder {

        @Test
        void ownOrder_returns204() throws Exception {
            mockMvc.perform(delete("/api/orders/{id}", 5).header(HEADER, USER))
                    .andExpect(status().isNoContent());

            verify(orderService).deleteOrder(5L, USER);
        }

        @Test
        void unknownOrder_returns404() throws Exception {
            doThrow(new OrderNotFoundException("not found")).when(orderService).deleteOrder(99L, USER);

            mockMvc.perform(delete("/api/orders/{id}", 99).header(HEADER, USER))
                    .andExpect(status().isNotFound());
        }

        @Test
        void someoneElsesOrder_returns409() throws Exception {
            doThrow(new CannotModifyOrderException("not yours")).when(orderService).deleteOrder(5L, USER);

            mockMvc.perform(delete("/api/orders/{id}", 5).header(HEADER, USER))
                    .andExpect(status().isConflict());
        }

        @Test
        void missingAuthHeader_returns400() throws Exception {
            mockMvc.perform(delete("/api/orders/{id}", 5))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(orderService);
        }
    }
}