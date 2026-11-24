package com.portafolio.pedidos.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private OrderStatus toStatus;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(nullable = false, length = 160)
    private String actor;

    @Column(length = 300)
    private String note;

    protected OrderStatusHistory() {}

    OrderStatusHistory(Order order, OrderStatus from, OrderStatus to, String actor, String note) {
        this.order = order;
        this.fromStatus = from;
        this.toStatus = to;
        this.actor = actor;
        this.note = note;
        this.changedAt = Instant.now();
    }

    public Long getId() { return id; }
    public OrderStatus getFromStatus() { return fromStatus; }
    public OrderStatus getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
    public String getActor() { return actor; }
    public String getNote() { return note; }
}
