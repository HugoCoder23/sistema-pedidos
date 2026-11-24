package com.portafolio.pedidos.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida del pedido y transiciones permitidas.
 *
 * <pre>
 * PENDING --pago--> PAID --> PROCESSING --> SHIPPED --> DELIVERED
 *    |               |            |
 *    +---------------+------------+--> CANCELLED (devuelve el stock reservado)
 * </pre>
 */
public enum OrderStatus {
    PENDING, PAID, PROCESSING, SHIPPED, DELIVERED, CANCELLED;

    public Set<OrderStatus> next() {
        return switch (this) {
            case PENDING -> EnumSet.of(PAID, CANCELLED);
            case PAID -> EnumSet.of(PROCESSING, CANCELLED);
            case PROCESSING -> EnumSet.of(SHIPPED, CANCELLED);
            case SHIPPED -> EnumSet.of(DELIVERED);
            case DELIVERED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return next().contains(target);
    }

    /** El stock se descontó al crear el pedido y la mercancía sigue en bodega: cancelar lo devuelve. */
    public boolean stockIsReserved() {
        return this == PENDING || this == PAID || this == PROCESSING;
    }
}
