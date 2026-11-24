package com.portafolio.pedidos.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 30)
    private String number;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Embedded
    private ShippingAddress shippingAddress;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "shipping_cost", nullable = false, precision = 12, scale = 2)
    private BigDecimal shippingCost;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "item_count", nullable = false)
    private int itemCount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("changedAt, id")
    private List<OrderStatusHistory> history = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<Payment> payments = new ArrayList<>();

    /** Bloqueo optimista: dos cambios de estado simultáneos no se pisan (el segundo recibe 409). */
    @Version
    private long version;

    protected Order() {}

    public Order(String number, User customer, ShippingAddress shippingAddress) {
        this.number = number;
        this.customer = customer;
        this.shippingAddress = shippingAddress;
    }

    public void addItem(OrderItem item) {
        item.attachTo(this);
        items.add(item);
    }

    /** Fija los importes calculados en el servidor. */
    public void setTotals(BigDecimal subtotal, BigDecimal shippingCost, int itemCount) {
        this.subtotal = subtotal;
        this.shippingCost = shippingCost;
        this.total = subtotal.add(shippingCost);
        this.itemCount = itemCount;
    }

    /** Cambia el estado validando la máquina de estados y deja constancia en el historial. */
    public void transitionTo(OrderStatus target, String actor, String note) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Transición no permitida: " + status + " -> " + target);
        }
        history.add(new OrderStatusHistory(this, status, target, actor, note));
        this.status = target;
    }

    public void recordCreation(String actor) {
        history.add(new OrderStatusHistory(this, null, status, actor, "Pedido creado"));
    }

    public void addPayment(Payment payment) {
        payments.add(payment);
    }

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public User getCustomer() { return customer; }
    public OrderStatus getStatus() { return status; }
    public ShippingAddress getShippingAddress() { return shippingAddress; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getShippingCost() { return shippingCost; }
    public BigDecimal getTotal() { return total; }
    public int getItemCount() { return itemCount; }
    public List<OrderItem> getItems() { return items; }
    public List<OrderStatusHistory> getHistory() { return history; }
    public List<Payment> getPayments() { return payments; }
    public long getVersion() { return version; }
}
