package com.portafolio.pedidos.repository;

import com.portafolio.pedidos.entity.Order;
import com.portafolio.pedidos.entity.OrderStatus;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class OrderSpecifications {

    private OrderSpecifications() {}

    public static Specification<Order> customer(Long customerId) {
        return customerId == null ? null : (root, q, cb) -> cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<Order> status(OrderStatus status) {
        return status == null ? null : (root, q, cb) -> cb.equal(root.get("status"), status);
    }

    /** Busca por número de pedido, nombre o email del cliente. */
    public static Specification<Order> text(String text) {
        if (text == null || text.isBlank()) return null;
        String like = "%" + text.trim().toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        return (root, q, cb) -> {
            Join<Object, Object> customer = root.join("customer");
            return cb.or(
                    cb.like(cb.lower(root.get("number")), like, '\\'),
                    cb.like(cb.lower(customer.get("email")), like, '\\'),
                    cb.like(cb.lower(customer.get("name")), like, '\\'));
        };
    }

    public static Specification<Order> createdFrom(Instant from) {
        return from == null ? null : (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Order> createdBefore(Instant to) {
        return to == null ? null : (root, q, cb) -> cb.lessThan(root.get("createdAt"), to);
    }
}
