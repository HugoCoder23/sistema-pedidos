package com.portafolio.pedidos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cors cors, Shipping shipping, Orders orders, Seed seed) {

    public record Jwt(String secret, long expirationMinutes) {}

    public record Cors(List<String> allowedOrigins) {}

    /** Costo de envío fijo y umbral a partir del cual el envío es gratis (MXN). */
    public record Shipping(BigDecimal flatRate, BigDecimal freeFrom) {}

    public record Orders(int maxQuantityPerItem) {}

    public record Seed(boolean enabled, String demoPassword) {}
}
