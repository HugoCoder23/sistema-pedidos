package com.portafolio.pedidos.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

/** Registro de un intento de pago. Solo datos no sensibles: nunca números de tarjeta ni CVV. */
@Entity
@Table(name = "payments")
public class Payment extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, length = 30)
    private String provider;

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    protected Payment() {}

    public Payment(Order order, String provider, String reference, BigDecimal amount, PaymentStatus status,
                   String failureReason) {
        this.order = order;
        this.provider = provider;
        this.reference = reference;
        this.amount = amount;
        this.status = status;
        this.failureReason = failureReason;
    }

    public void markRefunded() { this.status = PaymentStatus.REFUNDED; }

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public String getProvider() { return provider; }
    public String getReference() { return reference; }
    public BigDecimal getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
}
