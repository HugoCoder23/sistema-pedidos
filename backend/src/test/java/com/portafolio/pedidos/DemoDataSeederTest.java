package com.portafolio.pedidos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** El dataset demo se carga con los servicios reales y deja datos coherentes. */
@SpringBootTest(properties = {
        "app.seed.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:pedidos-seed;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class DemoDataSeederTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("Carga cuentas, catálogo y pedidos en todos los estados con stock consistente")
    void seedsConsistentData() {
        assertThat(count("select count(*) from users")).isEqualTo(4);
        assertThat(count("select count(*) from products")).isEqualTo(18);
        assertThat(count("select count(*) from orders")).isEqualTo(10);
        assertThat(count("select count(distinct status) from orders")).isEqualTo(6);
        assertThat(count("select count(*) from cart_items")).isEqualTo(2);
        // Audífonos Aurora: 25 iniciales - 1 - 1 vendidos
        assertThat(count("select stock from products where sku = 'AUD-001'")).isEqualTo(23);
        // Cafetera: el pedido cancelado devolvió su unidad
        assertThat(count("select stock from products where sku = 'HOG-001'")).isEqualTo(14);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }
}
