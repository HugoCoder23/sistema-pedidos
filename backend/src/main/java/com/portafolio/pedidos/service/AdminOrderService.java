package com.portafolio.pedidos.service;

import com.portafolio.pedidos.dto.PageResponse;
import com.portafolio.pedidos.dto.Requests.UpdateOrderStatusRequest;
import com.portafolio.pedidos.dto.Responses.OrderResponse;
import com.portafolio.pedidos.dto.Responses.OrderSummaryResponse;
import com.portafolio.pedidos.entity.Order;
import com.portafolio.pedidos.entity.OrderStatus;
import com.portafolio.pedidos.entity.User;
import com.portafolio.pedidos.exception.BusinessRuleException;
import com.portafolio.pedidos.exception.ResourceNotFoundException;
import com.portafolio.pedidos.mapper.OrderMapper;
import com.portafolio.pedidos.repository.OrderRepository;
import com.portafolio.pedidos.repository.OrderSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** Gestión de pedidos para administradores y operadores. */
@Service
public class AdminOrderService {

    private final OrderRepository orders;
    private final OrderWorkflow workflow;
    private final OrderMapper mapper;

    public AdminOrderService(OrderRepository orders, OrderWorkflow workflow, OrderMapper mapper) {
        this.orders = orders;
        this.workflow = workflow;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> search(OrderStatus status, String text, Instant from, Instant to,
                                                     Pageable pageable) {
        Specification<Order> spec = Specification.where(OrderSpecifications.status(status))
                .and(OrderSpecifications.text(text))
                .and(OrderSpecifications.createdFrom(from))
                .and(OrderSpecifications.createdBefore(to));
        return PageResponse.of(orders.findAll(spec, pageable), mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(User staff, Long id) {
        return mapper.toResponse(find(id), staff);
    }

    @Transactional
    public OrderResponse changeStatus(User staff, Long id, UpdateOrderStatusRequest request) {
        Order order = find(id);
        OrderStatus target = request.status();
        if (target == OrderStatus.PAID) {
            throw new BusinessRuleException("PAYMENT_REQUIRED",
                    "El estado PAID solo se asigna cuando se aprueba un pago");
        }
        if (!order.getStatus().canTransitionTo(target)) {
            throw new BusinessRuleException("INVALID_TRANSITION",
                    "No se puede cambiar un pedido de " + order.getStatus() + " a " + target);
        }
        if (!OrderPolicy.allowedTransitions(order, staff).contains(target)) {
            throw new AccessDeniedException("Tu rol no puede cambiar el pedido a " + target);
        }
        workflow.apply(order, target, staff.getEmail(), request.note());
        orders.flush();
        return mapper.toResponse(order, staff);
    }

    private Order find(Long id) {
        return orders.findWithCustomerById(id).orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
    }
}
