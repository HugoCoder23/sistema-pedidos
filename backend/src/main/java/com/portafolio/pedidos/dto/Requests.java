package com.portafolio.pedidos.dto;

import com.portafolio.pedidos.entity.OrderStatus;
import com.portafolio.pedidos.validation.NonZero;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/** Cuerpos de entrada de la API. Nunca incluyen importes: precios y totales se calculan en el servidor. */
public final class Requests {

    private Requests() {}

    public record LoginRequest(
            @NotBlank(message = "El email es obligatorio") @Email(message = "Email inválido") String email,
            @NotBlank(message = "La contraseña es obligatoria") String password) {}

    public record RegisterRequest(
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 100) String name,
            @NotBlank(message = "El email es obligatorio") @Email(message = "Email inválido") @Size(max = 160)
            String email,
            @NotBlank(message = "La contraseña es obligatoria")
            @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Debe incluir al menos una letra y un número")
            String password) {}

    public record AddToCartRequest(
            @NotNull(message = "El producto es obligatorio") Long productId,
            @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad mínima es 1")
            @Max(value = 999, message = "Cantidad fuera de rango") Integer quantity) {}

    public record UpdateCartItemRequest(
            @NotNull(message = "La cantidad es obligatoria") @Min(value = 1, message = "La cantidad mínima es 1")
            @Max(value = 999, message = "Cantidad fuera de rango") Integer quantity) {}

    public record AddressRequest(
            @NotBlank(message = "El destinatario es obligatorio") @Size(max = 120) String recipient,
            @NotBlank(message = "El teléfono es obligatorio")
            @Pattern(regexp = "^[0-9+()\\-\\s]{8,20}$", message = "Teléfono inválido") String phone,
            @NotBlank(message = "La calle y número son obligatorios") @Size(max = 160) String line1,
            @Size(max = 160) String line2,
            @NotBlank(message = "La ciudad es obligatoria") @Size(max = 80) String city,
            @NotBlank(message = "El estado es obligatorio") @Size(max = 80) String state,
            @NotBlank(message = "El código postal es obligatorio")
            @Pattern(regexp = "^\\d{5}$", message = "El código postal debe tener 5 dígitos") String postalCode,
            @Size(max = 300) String instructions) {}

    public record CreateOrderRequest(
            @NotNull(message = "La dirección de entrega es obligatoria") @Valid AddressRequest shippingAddress) {}

    public record CancelOrderRequest(@Size(max = 300) String reason) {}

    public enum SimulatedOutcome { APPROVED, DECLINED }

    public record SimulatePaymentRequest(
            @NotNull(message = "El pedido es obligatorio") Long orderId,
            @Schema(description = "Resultado a simular. Por omisión APPROVED.") SimulatedOutcome outcome) {}

    public record UpdateOrderStatusRequest(
            @NotNull(message = "El estado es obligatorio") OrderStatus status,
            @Size(max = 300) String note) {}

    public record ProductRequest(
            @NotBlank(message = "El SKU es obligatorio") @Size(max = 40)
            @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "Solo letras, números y guiones") String sku,
            @NotBlank(message = "El nombre es obligatorio") @Size(max = 150) String name,
            @Size(max = 2000) String description,
            @NotNull(message = "El precio es obligatorio")
            @DecimalMin(value = "0.01", message = "El precio debe ser mayor a 0")
            @Digits(integer = 10, fraction = 2, message = "Máximo 2 decimales") BigDecimal price,
            @NotNull(message = "La categoría es obligatoria") Long categoryId,
            @Size(max = 500) @Pattern(regexp = "^(https?://|/).*$", message = "Debe ser una URL http(s) o una ruta /")
            String imageUrl,
            Boolean active,
            @Schema(description = "Solo al crear. Después el stock se cambia con PATCH /stock")
            @Min(value = 0, message = "El stock inicial no puede ser negativo") Integer initialStock,
            @Schema(description = "Versión leída (bloqueo optimista). Obligatoria al editar.") Long version) {}

    public record StockAdjustmentRequest(
            @NotNull(message = "La variación es obligatoria") @NonZero
            @Min(value = -100000) @Max(value = 100000) Integer delta,
            @Size(max = 200) String reason) {}

    public record CategoryRequest(@NotBlank(message = "El nombre es obligatorio") @Size(max = 60) String name) {}
}
