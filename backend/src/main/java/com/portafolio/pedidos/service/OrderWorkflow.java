package com.portafolio.pedidos.service;

import com.portafolio.pedidos.entity.*;
import com.portafolio.pedidos.exception.BusinessRuleException;
import com.portafolio.pedidos.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;

/**
 * Aplica transiciones de estado con sus efectos colaterales, dentro de la transacción del llamador:
 * <ul>
 *   <li>Valida la máquina de estados.</li>
 *   <li>Al cancelar con stock reservado (PENDING, PAID, PROCESSING) devuelve las unidades al inventario.
 *       Si ya se envió (SHIPPED) no se puede cancelar: la devolución sería otro proceso.</li>
 *   <li>Al cancelar un pedido pagado marca el pago como reembolsado (simulado).</li>
 *   <li>Registra el historial y la notificación al cliente.</li>
 * </ul>
 */
@Service
public class OrderWorkflow {

    private final ProductRepository products;
    private final NotificationService notifications;

    public OrderWorkflow(ProductRepository products, NotificationService notifications) {
        this.products = products;
        this.notifications = notifications;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Order order, OrderStatus target, String actor, String note) {
        OrderStatus from = order.getStatus();
        if (!from.canTransitionTo(target)) {
            throw new BusinessRuleException("INVALID_TRANSITION",
                    "No se puede cambiar un pedido de " + from + " a " + target);
        }
        if (target == OrderStatus.CANCELLED) {
            if (from.stockIsReserved()) {
                order.getItems().stream()
                        .sorted(Comparator.comparing(OrderItem::getProductId)) // mismo orden de bloqueo que al comprar
                        .forEach(i -> products.restoreStock(i.getProductId(), i.getQuantity()));
            }
            order.getPayments().stream()
                    .filter(p -> p.getStatus() == PaymentStatus.APPROVED)
                    .forEach(Payment::markRefunded);
        }
        order.transitionTo(target, actor, note == null || note.isBlank() ? null : note.trim());
        notifications.statusChanged(order, target);
    }
}
