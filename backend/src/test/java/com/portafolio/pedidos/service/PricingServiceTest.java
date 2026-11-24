package com.portafolio.pedidos.service;

import com.portafolio.pedidos.config.AppProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private final PricingService pricing = new PricingService(new AppProperties(null, null,
            new AppProperties.Shipping(new BigDecimal("99"), new BigDecimal("1500")), null, null));

    @Test
    void chargesFlatRateBelowThreshold() {
        assertThat(pricing.shippingFor(new BigDecimal("1499.99"))).isEqualByComparingTo("99.00");
        assertThat(pricing.remainingForFreeShipping(new BigDecimal("1000"))).isEqualByComparingTo("500.00");
    }

    @Test
    void freeShippingFromThreshold() {
        assertThat(pricing.shippingFor(new BigDecimal("1500.00"))).isEqualByComparingTo("0");
        assertThat(pricing.remainingForFreeShipping(new BigDecimal("2000"))).isEqualByComparingTo("0");
    }

    @Test
    void emptyCartHasNoShipping() {
        assertThat(pricing.shippingFor(BigDecimal.ZERO)).isEqualByComparingTo("0");
    }
}
