package com.portafolio.pedidos.controller;

import com.portafolio.pedidos.dto.PageResponse;
import com.portafolio.pedidos.dto.Requests.UpdateOrderStatusRequest;
import com.portafolio.pedidos.dto.Responses.OrderResponse;
import com.portafolio.pedidos.dto.Responses.OrderSummaryResponse;
import com.portafolio.pedidos.entity.OrderStatus;
import com.portafolio.pedidos.security.CurrentUserProvider;
import com.portafolio.pedidos.service.AdminOrderService;
import com.portafolio.pedidos.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/orders")
@Tag(name = "Administración: pedidos (ADMIN, OPERATOR)")
public class AdminOrderController {

    static final Map<String, String> SORTS = Map.of("createdAt", "createdAt", "total", "total", "number", "number",
            "status", "status");

    private final AdminOrderService service;
    private final CurrentUserProvider currentUser;

    public AdminOrderController(AdminOrderService service, CurrentUserProvider currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Pedidos con filtros por estado, texto (número, cliente) y rango de fechas")
    public PageResponse<OrderSummaryResponse> search(
            @RequestParam(required = false) OrderStatus status,
            @Parameter(description = "Número de pedido, nombre o email del cliente") @RequestParam(required = false) String q,
            @Parameter(description = "Desde (yyyy-MM-dd, incluido)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Hasta (yyyy-MM-dd, incluido)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("La fecha 'from' no puede ser posterior a 'to'");
        }
        return service.search(status, q,
                from == null ? null : from.atStartOfDay(ReportService.STORE_ZONE).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(ReportService.STORE_ZONE).toInstant(),
                PageRequests.of(page, size, sort, SORTS, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalle completo del pedido con pagos, historial y transiciones permitidas para tu rol")
    public OrderResponse get(@PathVariable Long id) {
        return service.get(currentUser.get(), id);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Cambiar el estado del pedido",
            description = "422 si la transición no es válida; 403 si tu rol no puede hacerla; 409 si otro cambio ganó la carrera.")
    public OrderResponse changeStatus(@PathVariable Long id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return service.changeStatus(currentUser.get(), id, request);
    }
}
