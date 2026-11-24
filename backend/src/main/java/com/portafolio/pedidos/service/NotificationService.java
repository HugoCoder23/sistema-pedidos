package com.portafolio.pedidos.service;

import com.portafolio.pedidos.dto.Responses.NotificationResponse;
import com.portafolio.pedidos.entity.Notification;
import com.portafolio.pedidos.entity.Order;
import com.portafolio.pedidos.entity.OrderStatus;
import com.portafolio.pedidos.entity.User;
import com.portafolio.pedidos.mapper.OrderMapper;
import com.portafolio.pedidos.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Notificaciones por email SIMULADAS: se guardan en la tabla {@code notifications} y se escriben en el log.
 * Sustituir por un envío real (SMTP / proveedor) solo requiere cambiar {@link #send}.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository repository;
    private final OrderMapper mapper;

    public NotificationService(NotificationRepository repository, OrderMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public void orderCreated(Order order) {
        send(order, "Recibimos tu pedido " + order.getNumber(),
                "Hola " + order.getCustomer().getName() + ", registramos tu pedido " + order.getNumber()
                        + " por " + money(order) + ". Completa el pago para que lo preparemos.");
    }

    public void paymentDeclined(Order order) {
        send(order, "Pago rechazado para el pedido " + order.getNumber(),
                "El pago simulado del pedido " + order.getNumber()
                        + " fue rechazado. Tu pedido sigue pendiente; puedes intentar de nuevo.");
    }

    public void statusChanged(Order order, OrderStatus to) {
        String body = switch (to) {
            case PAID -> "Confirmamos el pago de " + money(order) + ". Pronto empezaremos a preparar tu pedido.";
            case PROCESSING -> "Estamos preparando tu pedido.";
            case SHIPPED -> "Tu pedido va en camino a " + order.getShippingAddress().getCity() + ".";
            case DELIVERED -> "Tu pedido fue entregado. ¡Gracias por tu compra!";
            case CANCELLED -> "Tu pedido fue cancelado. Si ya habías pagado, el reembolso se procesa automáticamente.";
            case PENDING -> "Tu pedido está pendiente de pago.";
        };
        send(order, "Pedido " + order.getNumber() + ": " + label(to), body);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> forUser(User user) {
        return repository.findTop20ByUserIdOrderByCreatedAtDescIdDesc(user.getId()).stream()
                .map(mapper::toResponse).toList();
    }

    private void send(Order order, String subject, String body) {
        User customer = order.getCustomer();
        repository.save(new Notification(customer.getId(), order.getId(), customer.getEmail(), subject, body));
        log.info("[email simulado] para={} asunto=\"{}\"", customer.getEmail(), subject);
    }

    private static String money(Order order) {
        return NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")).format(order.getTotal());
    }

    private static String label(OrderStatus s) {
        return switch (s) {
            case PENDING -> "pendiente de pago";
            case PAID -> "pago confirmado";
            case PROCESSING -> "en preparación";
            case SHIPPED -> "enviado";
            case DELIVERED -> "entregado";
            case CANCELLED -> "cancelado";
        };
    }
}
