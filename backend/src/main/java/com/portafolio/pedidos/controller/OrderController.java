package com.portafolio.pedidos.controller;

import com.portafolio.pedidos.dto.PageResponse;
import com.portafolio.pedidos.dto.Requests.CancelOrderRequest;
import com.portafolio.pedidos.dto.Requests.CreateOrderRequest;
import com.portafolio.pedidos.dto.Responses.OrderResponse;
import com.portafolio.pedidos.dto.Responses.OrderSummaryResponse;
import com.portafolio.pedidos.entity.OrderStatus;
import com.portafolio.pedidos.security.CurrentUserProvider;
import com.portafolio.pedidos.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Pedidos (cliente)")
public class OrderController {

    private final OrderService orders;
    private final CurrentUserProvider currentUser;

    public OrderController(OrderService orders, CurrentUserProvider currentUser) {
        this.orders = orders;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(summary = "Confirmar el pedido con el contenido del carrito",
            description = "Relee precios y stock, descuenta inventario de forma atómica, guarda snapshots y vacía el carrito.")
    public ResponseEntity<OrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse order = orders.create(currentUser.get(), request);
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
    }

    @GetMapping("/my")
    @Operation(summary = "Historial de pedidos del cliente autenticado")
    public PageResponse<OrderSummaryResponse> my(@RequestParam(required = false) OrderStatus status,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return orders.myOrders(currentUser.get(), status,
                PageRequests.of(page, size, null, Map.of(), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle de un pedido propio (404 si es de otro cliente)")
    public OrderResponse get(@PathVariable Long id) {
        return orders.getMine(currentUser.get(), id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancelar un pedido propio (solo PENDING o PAID); devuelve el stock")
    public OrderResponse cancel(@PathVariable Long id, @Valid @RequestBody(required = false) CancelOrderRequest request) {
        return orders.cancelMine(currentUser.get(), id, request == null ? null : request.reason());
    }
}
