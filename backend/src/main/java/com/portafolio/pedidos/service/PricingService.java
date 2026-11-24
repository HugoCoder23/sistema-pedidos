package com.portafolio.pedidos.service;

import com.portafolio.pedidos.config.AppProperties;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Reglas de importes. Se usa tanto para el carrito como para el pedido: el navegador nunca envía montos. */
@Service
public class PricingService {

    private final BigDecimal flatRate;
    private final BigDecimal freeFrom;

    public PricingService(AppProperties props) {
        this.flatRate = props.shipping().flatRate().setScale(2, RoundingMode.HALF_UP);
        this.freeFrom = props.shipping().freeFrom().setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal shippingFor(BigDecimal subtotal) {
        if (subtotal.signum() <= 0 || subtotal.compareTo(freeFrom) >= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return flatRate;
    }

    /** Cuánto falta para el envío gratis (0 si ya aplica). */
    public BigDecimal remainingForFreeShipping(BigDecimal subtotal) {
        BigDecimal diff = freeFrom.subtract(subtotal);
        return diff.signum() > 0 ? diff.setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }

    public BigDecimal freeShippingFrom() {
        return freeFrom;
    }
}
