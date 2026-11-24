package com.portafolio.pedidos.service;

import com.portafolio.pedidos.config.AppProperties;
import com.portafolio.pedidos.dto.PageResponse;
import com.portafolio.pedidos.dto.Requests.AddressRequest;
import com.portafolio.pedidos.dto.Requests.CreateOrderRequest;
import com.portafolio.pedidos.dto.Responses.OrderResponse;
import com.portafolio.pedidos.dto.Responses.OrderSummaryResponse;
import com.portafolio.pedidos.entity.*;
import com.portafolio.pedidos.exception.BusinessRuleException;
import com.portafolio.pedidos.exception.ResourceNotFoundException;
import com.portafolio.pedidos.mapper.OrderMapper;
import com.portafolio.pedidos.repository.CartItemRepository;
import com.portafolio.pedidos.repository.OrderRepository;
import com.portafolio.pedidos.repository.OrderSpecifications;
import com.portafolio.pedidos.repository.ProductRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Operaciones del cliente sobre sus pedidos. */
@Service
public class OrderService {

    private final CartItemRepository cartItems;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final PricingService pricing;
    private final OrderNumberGenerator numbers;
    private final OrderWorkflow workflow;
    private final NotificationService notifications;
    private final OrderMapper mapper;
    private final int maxPerItem;

    public OrderService(CartItemRepository cartItems, ProductRepository products, OrderRepository orders,
                        PricingService pricing, OrderNumberGenerator numbers, OrderWorkflow workflow,
                        NotificationService notifications, OrderMapper mapper, AppProperties props) {
        this.cartItems = cartItems;
        this.products = products;
        this.orders = orders;
        this.pricing = pricing;
        this.numbers = numbers;
        this.workflow = workflow;
        this.notifications = notifications;
        this.mapper = mapper;
        this.maxPerItem = props.orders().maxQuantityPerItem();
    }

    /**
     * Convierte el carrito en un pedido, todo en UNA transacción:
     * <ol>
     *   <li>Relee del servidor los productos, precios y estado (nunca usa importes del navegador).</li>
     *   <li>Descuenta el stock con un UPDATE condicional por producto, en orden de id para evitar interbloqueos.
     *       Si alguno no alcanza, se lanza una excepción y se revierte todo (incluidos los descuentos previos).</li>
     *   <li>Guarda el pedido con snapshots de nombre, SKU y precio, calcula subtotal, envío y total.</li>
     *   <li>Vacía el carrito, registra el historial y la notificación.</li>
     * </ol>
     */
    @Transactional
    public OrderResponse create(User customer, CreateOrderRequest request) {
        List<CartItem> items = cartItems.findByUserIdOrderByIdAsc(customer.getId()).stream()
                .sorted(Comparator.comparing(i -> i.getProduct().getId()))
                .toList();
        if (items.isEmpty()) {
            throw new BusinessRuleException("CART_EMPTY", "Tu carrito está vacío");
        }

        Order order = new Order(numbers.next(), customer, toAddress(request.shippingAddress()));
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        int count = 0;
        for (CartItem item : items) {
            Product product = item.getProduct();
            int qty = item.getQuantity();
            if (!product.isActive()) {
                throw new BusinessRuleException("PRODUCT_UNAVAILABLE",
                        product.getName() + " ya no está disponible. Quítalo del carrito para continuar");
            }
            if (qty > maxPerItem) {
                throw new BusinessRuleException("QUANTITY_LIMIT",
                        "Puedes comprar como máximo " + maxPerItem + " unidades de " + product.getName());
            }
            if (products.decrementStock(product.getId(), qty) == 0) {
                int available = products.findStockById(product.getId()).orElse(0);
                throw new BusinessRuleException("INSUFFICIENT_STOCK", available == 0
                        ? product.getName() + " se agotó"
                        : "Solo quedan " + available + " unidades de " + product.getName());
            }
            OrderItem line = new OrderItem(product.getId(), product.getSku(), product.getName(), product.getPrice(), qty);
            order.addItem(line);
            subtotal = subtotal.add(line.getLineTotal());
            count += qty;
        }
        order.setTotals(subtotal, pricing.shippingFor(subtotal), count);
        order.recordCreation(customer.getEmail());
        orders.saveAndFlush(order);
        cartItems.deleteAllByUser(customer.getId());
        notifications.orderCreated(order);
        return mapper.toResponse(order, customer);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> myOrders(User customer, OrderStatus status, Pageable pageable) {
        Specification<Order> spec = Specification.where(OrderSpecifications.customer(customer.getId()))
                .and(OrderSpecifications.status(status));
        return PageResponse.of(orders.findAll(spec, pageable), mapper::toSummary);
    }

    @Transactional(readOnly = true)
    public OrderResponse getMine(User customer, Long orderId) {
        return mapper.toResponse(findOwned(customer, orderId), customer);
    }

    @Transactional
    public OrderResponse cancelMine(User customer, Long orderId, String reason) {
        Order order = findOwned(customer, orderId);
        if (!OrderPolicy.customerCanCancel(order.getStatus())) {
            throw new BusinessRuleException("INVALID_TRANSITION",
                    "Solo puedes cancelar pedidos pendientes de pago o pagados que aún no se preparan");
        }
        workflow.apply(order, OrderStatus.CANCELLED, customer.getEmail(),
                reason == null || reason.isBlank() ? "Cancelado por el cliente" : reason);
        orders.flush();
        return mapper.toResponse(order, customer);
    }

    /**
     * Devuelve 404 (no 403) cuando el pedido es de otro cliente: no se revela que el pedido existe.
     */
    Order findOwned(User customer, Long orderId) {
        return orders.findByIdAndCustomerId(orderId, customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
    }

    private static ShippingAddress toAddress(AddressRequest a) {
        return new ShippingAddress(a.recipient().trim(), a.phone().trim(), a.line1().trim(), blankToNull(a.line2()),
                a.city().trim(), a.state().trim(), a.postalCode().trim(), blankToNull(a.instructions()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
