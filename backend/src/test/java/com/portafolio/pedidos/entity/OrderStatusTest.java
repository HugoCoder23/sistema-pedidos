package com.portafolio.pedidos.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} = {2}")
    @CsvSource({
            "PENDING, PAID, true", "PENDING, CANCELLED, true", "PENDING, SHIPPED, false",
            "PAID, PROCESSING, true", "PAID, CANCELLED, true", "PAID, DELIVERED, false",
            "PROCESSING, SHIPPED, true", "PROCESSING, CANCELLED, true", "PROCESSING, PAID, false",
            "SHIPPED, DELIVERED, true", "SHIPPED, CANCELLED, false",
            "DELIVERED, CANCELLED, false", "CANCELLED, PENDING, false", "CANCELLED, PAID, false"})
    @DisplayName("Transiciones válidas e inválidas de la máquina de estados")
    void transitions(OrderStatus from, OrderStatus to, boolean expected) {
        assertThat(from.canTransitionTo(to)).isEqualTo(expected);
    }

    @Test
    @DisplayName("Solo se devuelve stock si la mercancía no ha salido de bodega")
    void stockReservation() {
        assertThat(OrderStatus.PENDING.stockIsReserved()).isTrue();
        assertThat(OrderStatus.PAID.stockIsReserved()).isTrue();
        assertThat(OrderStatus.PROCESSING.stockIsReserved()).isTrue();
        assertThat(OrderStatus.SHIPPED.stockIsReserved()).isFalse();
        assertThat(OrderStatus.DELIVERED.stockIsReserved()).isFalse();
    }
}
