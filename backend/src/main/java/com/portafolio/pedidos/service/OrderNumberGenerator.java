package com.portafolio.pedidos.service;

import com.portafolio.pedidos.repository.OrderRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Número de pedido legible y no secuencial, p. ej. {@code PED-251123-K7Q2MX}.
 * No revela cuántos pedidos hay y se genera sin consultar un contador global.
 * La restricción UNIQUE de la base es la última línea de defensa ante colisiones.
 */
@Component
public class OrderNumberGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final SecureRandom random = new SecureRandom();
    private final OrderRepository orders;

    public OrderNumberGenerator(OrderRepository orders) {
        this.orders = orders;
    }

    public String next() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "PED-" + LocalDate.now(ReportService.STORE_ZONE).format(DATE) + "-" + randomCode(6);
            if (!orders.existsByNumber(candidate)) return candidate;
        }
        throw new IllegalStateException("No se pudo generar un número de pedido único");
    }

    public String paymentReference() {
        return "SIM-" + randomCode(12);
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        return sb.toString();
    }
}
