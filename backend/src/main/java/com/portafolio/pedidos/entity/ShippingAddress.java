package com.portafolio.pedidos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Snapshot de la dirección de entrega: se copia al pedido y no cambia después. */
@Embeddable
public class ShippingAddress {

    @Column(name = "ship_recipient", nullable = false, length = 120)
    private String recipient;

    @Column(name = "ship_phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "ship_line1", nullable = false, length = 160)
    private String line1;

    @Column(name = "ship_line2", length = 160)
    private String line2;

    @Column(name = "ship_city", nullable = false, length = 80)
    private String city;

    @Column(name = "ship_state", nullable = false, length = 80)
    private String state;

    @Column(name = "ship_postal_code", nullable = false, length = 10)
    private String postalCode;

    @Column(name = "ship_instructions", length = 300)
    private String instructions;

    protected ShippingAddress() {}

    public ShippingAddress(String recipient, String phone, String line1, String line2, String city, String state,
                           String postalCode, String instructions) {
        this.recipient = recipient;
        this.phone = phone;
        this.line1 = line1;
        this.line2 = line2;
        this.city = city;
        this.state = state;
        this.postalCode = postalCode;
        this.instructions = instructions;
    }

    public String getRecipient() { return recipient; }
    public String getPhone() { return phone; }
    public String getLine1() { return line1; }
    public String getLine2() { return line2; }
    public String getCity() { return city; }
    public String getState() { return state; }
    public String getPostalCode() { return postalCode; }
    public String getInstructions() { return instructions; }
}
