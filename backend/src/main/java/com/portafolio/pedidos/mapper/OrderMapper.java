package com.portafolio.pedidos.mapper;

import com.portafolio.pedidos.dto.Responses.*;
import com.portafolio.pedidos.entity.*;
import com.portafolio.pedidos.service.OrderPolicy;
import org.springframework.stereotype.Component;

import java.util.List;

/** Conversión de entidades a DTOs. Nunca se expone una entidad JPA directamente. */
@Component
public class OrderMapper {

    /** A partir de cuántas unidades se muestra "pocas unidades". */
    public static final int LOW_STOCK_THRESHOLD = 5;

    private static final String STORE_ACTOR = "Equipo de la tienda";

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }

    public CategoryRef toRef(Category c) {
        return new CategoryRef(c.getId(), c.getName(), c.getSlug());
    }

    public ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getPrice(),
                p.getStock(), availability(p.getStock()), p.getImageUrl(), p.isActive(), toRef(p.getCategory()),
                p.getVersion(), p.getCreatedAt(), p.getUpdatedAt());
    }

    public static Availability availability(int stock) {
        if (stock <= 0) return Availability.OUT_OF_STOCK;
        if (stock <= LOW_STOCK_THRESHOLD) return Availability.LOW_STOCK;
        return Availability.IN_STOCK;
    }

    public CustomerRef toRef(User u) {
        return new CustomerRef(u.getId(), u.getName(), u.getEmail());
    }

    public OrderSummaryResponse toSummary(Order o) {
        return new OrderSummaryResponse(o.getId(), o.getNumber(), o.getStatus(), o.getCreatedAt(), o.getItemCount(),
                o.getTotal(), toRef(o.getCustomer()));
    }

    /**
     * @param viewer quien consulta: determina las acciones permitidas y, si es cliente, oculta los
     *               correos del personal en el historial.
     */
    public OrderResponse toResponse(Order o, User viewer) {
        ShippingAddress a = o.getShippingAddress();
        boolean customerView = viewer.getRole() == Role.CUSTOMER;
        List<StatusChangeResponse> history = o.getHistory().stream()
                .map(h -> new StatusChangeResponse(h.getFromStatus(), h.getToStatus(), h.getChangedAt(),
                        customerView && !h.getActor().equalsIgnoreCase(viewer.getEmail()) ? STORE_ACTOR : h.getActor(),
                        h.getNote()))
                .toList();
        var allowed = OrderPolicy.allowedTransitions(o, viewer);
        return new OrderResponse(
                o.getId(), o.getNumber(), o.getStatus(), o.getCreatedAt(), o.getUpdatedAt(), toRef(o.getCustomer()),
                new AddressResponse(a.getRecipient(), a.getPhone(), a.getLine1(), a.getLine2(), a.getCity(),
                        a.getState(), a.getPostalCode(), a.getInstructions()),
                o.getItems().stream().map(i -> new OrderItemResponse(i.getProductId(), i.getSkuSnapshot(),
                        i.getProductNameSnapshot(), i.getUnitPriceSnapshot(), i.getQuantity(), i.getLineTotal())).toList(),
                o.getItemCount(), o.getSubtotal(), o.getShippingCost(), o.getTotal(),
                o.getPayments().stream().map(this::toResponse).toList(),
                history,
                List.copyOf(allowed),
                OrderPolicy.canPay(o, viewer),
                allowed.contains(OrderStatus.CANCELLED),
                o.getVersion());
    }

    public PaymentResponse toResponse(Payment p) {
        return new PaymentResponse(p.getId(), p.getProvider(), p.getReference(), p.getAmount(), p.getStatus(),
                p.getFailureReason(), p.getCreatedAt());
    }

    public NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(n.getId(), n.getOrderId(), n.getRecipient(), n.getSubject(), n.getBody(),
                n.getCreatedAt());
    }
}
