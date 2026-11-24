package com.portafolio.pedidos.dto;

import com.portafolio.pedidos.entity.OrderStatus;
import com.portafolio.pedidos.entity.PaymentStatus;
import com.portafolio.pedidos.entity.Role;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class Responses {

    private Responses() {}

    public record UserResponse(Long id, String name, String email, Role role) {}

    public record LoginResponse(String token, String tokenType, Instant expiresAt, UserResponse user) {}

    public record CategoryResponse(Long id, String name, String slug, Long productCount) {}

    public record CategoryRef(Long id, String name, String slug) {}

    public enum Availability { IN_STOCK, LOW_STOCK, OUT_OF_STOCK }

    public record ProductResponse(
            Long id, String sku, String name, String description, BigDecimal price, int stock,
            Availability availability, String imageUrl, boolean active, CategoryRef category, long version,
            Instant createdAt, Instant updatedAt) {}

    public record CartItemResponse(
            Long id, Long productId, String sku, String name, String imageUrl, String categorySlug,
            BigDecimal unitPrice, int quantity, BigDecimal lineTotal, int stock, boolean active,
            /** Motivo por el que la partida no se puede comprar tal cual (null si está bien). */
            String issue) {}

    public record CartResponse(
            List<CartItemResponse> items, int itemCount, BigDecimal subtotal, BigDecimal shippingCost,
            BigDecimal total, BigDecimal freeShippingFrom, BigDecimal amountForFreeShipping, boolean canCheckout) {}

    public record AddressResponse(String recipient, String phone, String line1, String line2, String city,
                                  String state, String postalCode, String instructions) {}

    public record OrderItemResponse(Long productId, String sku, String name, BigDecimal unitPrice, int quantity,
                                    BigDecimal lineTotal) {}

    public record PaymentResponse(Long id, String provider, String reference, BigDecimal amount,
                                  PaymentStatus status, String failureReason, Instant createdAt) {}

    public record StatusChangeResponse(OrderStatus from, OrderStatus to, Instant changedAt, String actor,
                                       String note) {}

    public record CustomerRef(Long id, String name, String email) {}

    public record OrderSummaryResponse(Long id, String number, OrderStatus status, Instant createdAt, int itemCount,
                                       BigDecimal total, CustomerRef customer) {}

    public record OrderResponse(
            Long id, String number, OrderStatus status, Instant createdAt, Instant updatedAt, CustomerRef customer,
            AddressResponse shippingAddress, List<OrderItemResponse> items, int itemCount, BigDecimal subtotal,
            BigDecimal shippingCost, BigDecimal total, List<PaymentResponse> payments,
            List<StatusChangeResponse> history,
            /** Estados a los que quien consulta puede mover el pedido. */
            List<OrderStatus> allowedTransitions, boolean canPay, boolean canCancel, long version) {}

    public record PaymentResultResponse(PaymentResponse payment, OrderResponse order) {}

    public record NotificationResponse(Long id, Long orderId, String recipient, String subject, String body,
                                       Instant createdAt) {}

    public record DailySales(LocalDate day, long orders, BigDecimal revenue) {}

    public record TopProduct(Long productId, String name, long units, BigDecimal revenue) {}

    public record ReportResponse(
            int days, BigDecimal revenue, long paidOrders, BigDecimal averageTicket, long activeProducts,
            Map<OrderStatus, Long> ordersByStatus, List<DailySales> salesByDay, List<TopProduct> topProducts,
            List<ProductResponse> lowStock) {}
}
