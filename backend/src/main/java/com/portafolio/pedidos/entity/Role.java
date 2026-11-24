package com.portafolio.pedidos.entity;

public enum Role {
    /** Compra: catálogo, carrito, checkout e historial propio. */
    CUSTOMER,
    /** Actualiza preparación y envío de pedidos. */
    OPERATOR,
    /** Catálogo, inventario, pedidos y reportes. */
    ADMIN
}
