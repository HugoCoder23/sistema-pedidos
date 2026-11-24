package com.portafolio.pedidos.service;

import com.portafolio.pedidos.dto.Requests.SimulatePaymentRequest;
import com.portafolio.pedidos.dto.Requests.SimulatedOutcome;
import com.portafolio.pedidos.dto.Responses.PaymentResultResponse;
import com.portafolio.pedidos.entity.*;
import com.portafolio.pedidos.exception.BusinessRuleException;
import com.portafolio.pedidos.mapper.OrderMapper;
import com.portafolio.pedidos.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pasarela de pago SIMULADA para el portafolio. No recibe ni guarda datos de tarjeta: el importe
 * se toma del pedido (calculado en el servidor) y el resultado lo elige quien prueba la demo.
 * Para integrar un proveedor real (p. ej. Stripe en modo test) se sustituiría este servicio por uno
 * que cree el intento de pago y confirme el pedido desde el webhook del proveedor.
 */
@Service
public class PaymentService {

    public static final String PROVIDER = "SIMULATED";

    private final OrderService orderService;
    private final OrderRepository orders;
    private final OrderWorkflow workflow;
    private final OrderNumberGenerator references;
    private final NotificationService notifications;
    private final OrderMapper mapper;

    public PaymentService(OrderService orderService, OrderRepository orders, OrderWorkflow workflow,
                          OrderNumberGenerator references, NotificationService notifications, OrderMapper mapper) {
        this.orderService = orderService;
        this.orders = orders;
        this.workflow = workflow;
        this.references = references;
        this.notifications = notifications;
        this.mapper = mapper;
    }

    @Transactional
    public PaymentResultResponse simulate(User customer, SimulatePaymentRequest request) {
        Order order = orderService.findOwned(customer, request.orderId());
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessRuleException("ORDER_NOT_PAYABLE",
                    "El pedido " + order.getNumber() + " no está pendiente de pago");
        }
        boolean approved = request.outcome() != SimulatedOutcome.DECLINED;
        Payment payment = new Payment(order, PROVIDER, references.paymentReference(), order.getTotal(),
                approved ? PaymentStatus.APPROVED : PaymentStatus.DECLINED,
                approved ? null : "Tarjeta rechazada por el banco emisor (simulado)");
        order.addPayment(payment);
        if (approved) {
            workflow.apply(order, OrderStatus.PAID, customer.getEmail(),
                    "Pago aprobado · ref. " + payment.getReference());
        } else {
            notifications.paymentDeclined(order);
        }
        orders.saveAndFlush(order);
        return new PaymentResultResponse(mapper.toResponse(payment), mapper.toResponse(order, customer));
    }
}
