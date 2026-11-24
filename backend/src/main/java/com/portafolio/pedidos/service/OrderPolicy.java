package com.portafolio.pedidos.service;

import com.portafolio.pedidos.entity.Order;
import com.portafolio.pedidos.entity.OrderStatus;
import com.portafolio.pedidos.entity.Role;
import com.portafolio.pedidos.entity.User;

import java.util.EnumSet;
import java.util.Set;

/**
 * Quién puede mover un pedido a qué estado. La máquina de estados ({@link OrderStatus#next()}) dice qué
 * transiciones existen; esta política dice cuáles puede ejecutar cada rol.
 *
 * <ul>
 *   <li>PAID nunca se asigna a mano: solo lo pone un pago aprobado.</li>
 *   <li>Cliente: solo puede cancelar su propio pedido mientras está PENDING o PAID.</li>
 *   <li>Operador: preparación y envío (PROCESSING, SHIPPED, DELIVERED); no cancela.</li>
 *   <li>Administrador: cualquier transición válida excepto PAID.</li>
 * </ul>
 */
public final class OrderPolicy {

    private static final Set<OrderStatus> OPERATOR_TARGETS =
            EnumSet.of(OrderStatus.PROCESSING, OrderStatus.SHIPPED, OrderStatus.DELIVERED);

    private OrderPolicy() {}

    public static Set<OrderStatus> allowedTransitions(Order order, User viewer) {
        Set<OrderStatus> next = EnumSet.noneOf(OrderStatus.class);
        next.addAll(order.getStatus().next());
        next.remove(OrderStatus.PAID);
        return switch (viewer.getRole()) {
            case ADMIN -> next;
            case OPERATOR -> {
                next.retainAll(OPERATOR_TARGETS);
                yield next;
            }
            case CUSTOMER -> isOwner(order, viewer) && customerCanCancel(order.getStatus())
                    ? EnumSet.of(OrderStatus.CANCELLED) : EnumSet.noneOf(OrderStatus.class);
        };
    }

    public static boolean customerCanCancel(OrderStatus status) {
        return status == OrderStatus.PENDING || status == OrderStatus.PAID;
    }

    public static boolean canPay(Order order, User viewer) {
        return viewer.getRole() == Role.CUSTOMER && isOwner(order, viewer) && order.getStatus() == OrderStatus.PENDING;
    }

    public static boolean isOwner(Order order, User viewer) {
        return order.getCustomer().getId().equals(viewer.getId());
    }
}
