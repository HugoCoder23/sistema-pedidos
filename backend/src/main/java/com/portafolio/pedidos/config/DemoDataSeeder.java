package com.portafolio.pedidos.config;

import com.portafolio.pedidos.dto.Requests.*;
import com.portafolio.pedidos.dto.Responses.OrderResponse;
import com.portafolio.pedidos.entity.*;
import com.portafolio.pedidos.repository.CategoryRepository;
import com.portafolio.pedidos.repository.ProductRepository;
import com.portafolio.pedidos.repository.UserRepository;
import com.portafolio.pedidos.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carga datos FICTICIOS de demostración cuando la base está vacía: catálogo, cuentas demo y pedidos
 * en distintos estados. Los pedidos se crean con los mismos servicios que usa la API (carrito →
 * pedido → pago → cambios de estado), así el stock y el historial son coherentes.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final AppProperties props;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final PasswordEncoder encoder;
    private final CartService cart;
    private final OrderService orders;
    private final PaymentService payments;
    private final AdminOrderService adminOrders;
    private final JdbcTemplate jdbc;

    public DemoDataSeeder(AppProperties props, UserRepository users, CategoryRepository categories,
                          ProductRepository products, PasswordEncoder encoder, CartService cart, OrderService orders,
                          PaymentService payments, AdminOrderService adminOrders, JdbcTemplate jdbc) {
        this.props = props;
        this.users = users;
        this.categories = categories;
        this.products = products;
        this.encoder = encoder;
        this.cart = cart;
        this.orders = orders;
        this.payments = payments;
        this.adminOrders = adminOrders;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled() || users.count() > 0) return;
        String hash = encoder.encode(props.seed().demoPassword());
        User ana = users.save(new User("Ana Martínez", "cliente@demo.local", hash, Role.CUSTOMER));
        User bruno = users.save(new User("Bruno Díaz", "cliente2@demo.local", hash, Role.CUSTOMER));
        User admin = users.save(new User("Admin Demo", "admin@demo.local", hash, Role.ADMIN));
        User operator = users.save(new User("Olivia Operadora", "operador@demo.local", hash, Role.OPERATOR));

        Map<String, Category> cat = new HashMap<>();
        for (String[] c : new String[][]{{"Audio", "audio"}, {"Calzado", "calzado"}, {"Relojes", "relojes"},
                {"Computación", "computacion"}, {"Hogar", "hogar"}}) {
            cat.put(c[1], categories.save(new Category(c[0], c[1])));
        }

        Map<String, Product> p = new HashMap<>();
        product(p, "AUD-001", "Audífonos inalámbricos Aurora", "799.00", 25, cat.get("audio"),
                "Audífonos over-ear con cancelación de ruido, 30 h de batería y carga rápida por USB-C.");
        product(p, "AUD-002", "Bocina portátil Pulse", "1149.00", 18, cat.get("audio"),
                "Bocina Bluetooth resistente al agua (IPX7) con sonido 360° y 12 h de reproducción.");
        product(p, "AUD-003", "Audífonos de estudio Vértice", "2299.00", 4, cat.get("audio"),
                "Monitores de estudio cerrados con respuesta plana, cable desmontable y estuche rígido.");
        product(p, "AUD-004", "Barra de sonido Nocturna", "3499.00", 7, cat.get("audio"),
                "Barra de sonido 2.1 con subwoofer inalámbrico, HDMI ARC y modo nocturno.");
        product(p, "CAL-001", "Tenis para correr Brisa", "1299.00", 30, cat.get("calzado"),
                "Tenis ligeros con suela de espuma reactiva y malla transpirable. Ideales para 5 a 21 km.");
        product(p, "CAL-002", "Botas de montaña Cumbre", "2149.00", 9, cat.get("calzado"),
                "Botas impermeables con suela de alta tracción y soporte de tobillo para senderismo.");
        product(p, "CAL-003", "Sandalias Marea", "449.00", 40, cat.get("calzado"),
                "Sandalias de secado rápido con plantilla acolchada.");
        product(p, "REL-001", "Reloj inteligente Órbita", "2499.00", 12, cat.get("relojes"),
                "Pantalla AMOLED, GPS, ritmo cardiaco y 7 días de batería. Compatible con iOS y Android.");
        product(p, "REL-002", "Reloj clásico Meridiano", "1899.00", 2, cat.get("relojes"),
                "Reloj analógico de acero inoxidable con correa de piel y cristal de zafiro.");
        product(p, "REL-003", "Pulsera deportiva Ritmo", "699.00", 0, cat.get("relojes"),
                "Monitor de actividad y sueño con 14 días de batería.");
        product(p, "COM-001", "Teclado mecánico Tecla", "1349.00", 15, cat.get("computacion"),
                "Teclado 75 % con switches táctiles intercambiables y retroiluminación.");
        product(p, "COM-002", "Mouse ergonómico Curva", "549.00", 35, cat.get("computacion"),
                "Mouse vertical inalámbrico que reduce la tensión de la muñeca.");
        product(p, "COM-003", "Monitor 27\" Horizonte", "4999.00", 6, cat.get("computacion"),
                "Monitor IPS QHD de 27 pulgadas a 144 Hz con soporte ajustable.");
        product(p, "COM-004", "Hub USB-C Puerto", "799.00", 22, cat.get("computacion"),
                "Adaptador 7 en 1: HDMI 4K, 3 USB-A, lector SD y carga de 100 W.");
        product(p, "HOG-001", "Cafetera de goteo Aroma", "1099.00", 14, cat.get("hogar"),
                "Cafetera programable de 12 tazas con jarra térmica.");
        product(p, "HOG-002", "Lámpara de escritorio Halo", "629.00", 20, cat.get("hogar"),
                "Lámpara LED regulable con 5 temperaturas de color y puerto de carga.");
        product(p, "HOG-003", "Termo de acero Ruta", "349.00", 50, cat.get("hogar"),
                "Termo de doble pared que mantiene bebidas frías 24 h y calientes 12 h.");
        Product discontinued = product(p, "HOG-004", "Molino de café manual", "899.00", 3, cat.get("hogar"),
                "Producto descontinuado (inactivo): no aparece en el catálogo público.");
        discontinued.setActive(false);
        products.save(discontinued);

        AddressRequest anaHome = new AddressRequest("Ana Martínez", "55 1234 5678", "Av. Reforma 123, Int. 4",
                "Col. Juárez", "Ciudad de México", "CDMX", "06600", "Dejar en recepción");
        AddressRequest brunoHome = new AddressRequest("Bruno Díaz", "33 8765 4321", "Calle Hidalgo 45", null,
                "Guadalajara", "Jalisco", "44100", null);

        // Pedidos en distintos estados y días, para que el panel y los reportes tengan contenido.
        seedOrder(ana, anaHome, Map.of(p.get("AUD-001"), 1, p.get("HOG-003"), 2), 13, admin, operator,
                OrderStatus.DELIVERED);
        seedOrder(bruno, brunoHome, Map.of(p.get("COM-003"), 1), 11, admin, operator, OrderStatus.DELIVERED);
        seedOrder(ana, anaHome, Map.of(p.get("CAL-001"), 1), 9, admin, operator, OrderStatus.SHIPPED);
        seedOrder(bruno, brunoHome, Map.of(p.get("REL-001"), 1, p.get("COM-002"), 1), 7, admin, operator,
                OrderStatus.DELIVERED);
        seedOrder(ana, anaHome, Map.of(p.get("HOG-001"), 1), 6, admin, operator, OrderStatus.CANCELLED);
        seedOrder(bruno, brunoHome, Map.of(p.get("COM-001"), 1, p.get("COM-004"), 1), 4, admin, operator,
                OrderStatus.PROCESSING);
        seedOrder(ana, anaHome, Map.of(p.get("AUD-002"), 1), 3, admin, operator, OrderStatus.SHIPPED);
        seedOrder(bruno, brunoHome, Map.of(p.get("CAL-003"), 2, p.get("HOG-002"), 1), 2, admin, operator,
                OrderStatus.PAID);
        seedOrder(ana, anaHome, Map.of(p.get("AUD-001"), 1), 1, admin, operator, OrderStatus.PAID);
        seedOrder(bruno, brunoHome, Map.of(p.get("CAL-002"), 1), 0, admin, operator, OrderStatus.PENDING);

        // El carrito de Ana queda con artículos para probar el checkout de inmediato.
        cart.add(ana, new AddToCartRequest(p.get("REL-002").getId(), 1));
        cart.add(ana, new AddToCartRequest(p.get("COM-002").getId(), 2));

        log.info("Datos demo cargados: 4 cuentas, {} productos y 10 pedidos (contraseña: DEMO_PASSWORD)",
                products.count());
    }

    private Product product(Map<String, Product> map, String sku, String name, String price, int stock,
                            Category category, String description) {
        Product prod = products.save(new Product(sku, name, description, new BigDecimal(price), stock, null, category));
        map.put(sku, prod);
        return prod;
    }

    private void seedOrder(User customer, AddressRequest address, Map<Product, Integer> lines, int daysAgo,
                           User admin, User operator, OrderStatus target) {
        lines.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(e -> cart.add(customer, new AddToCartRequest(e.getKey().getId(), e.getValue())));
        OrderResponse order = orders.create(customer, new CreateOrderRequest(address));
        List<OrderStatus> path = switch (target) {
            case PENDING -> List.of();
            case PAID -> List.of(OrderStatus.PAID);
            case PROCESSING -> List.of(OrderStatus.PAID, OrderStatus.PROCESSING);
            case SHIPPED -> List.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED);
            case DELIVERED -> List.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED,
                    OrderStatus.DELIVERED);
            case CANCELLED -> List.of(OrderStatus.PAID, OrderStatus.CANCELLED);
        };
        for (OrderStatus step : path) {
            if (step == OrderStatus.PAID) {
                payments.simulate(customer, new SimulatePaymentRequest(order.id(), SimulatedOutcome.APPROVED));
            } else {
                User actor = step == OrderStatus.CANCELLED ? admin : operator;
                String note = step == OrderStatus.SHIPPED ? "Guía de paquetería DEMO-" + order.id() : null;
                adminOrders.changeStatus(actor, order.id(), new UpdateOrderStatusRequest(step, note));
            }
        }
        backdate(order.id(), daysAgo);
    }

    /** Reparte los pedidos demo en días anteriores para que los reportes muestren una tendencia. */
    private void backdate(Long orderId, int daysAgo) {
        if (daysAgo == 0) return;
        Instant base = Instant.now().minus(Duration.ofDays(daysAgo)).minus(Duration.ofHours(3));
        Timestamp created = Timestamp.from(base);
        jdbc.update("update orders set created_at = ?, updated_at = ? where id = ?", created,
                Timestamp.from(base.plus(Duration.ofHours(daysAgo > 1 ? 30 : 2))), orderId);
        List<Long> historyIds = jdbc.queryForList(
                "select id from order_status_history where order_id = ? order by id", Long.class, orderId);
        for (int i = 0; i < historyIds.size(); i++) {
            jdbc.update("update order_status_history set changed_at = ? where id = ?",
                    Timestamp.from(base.plus(Duration.ofHours(i * (daysAgo > 1 ? 10L : 1L)).plusMinutes(i * 7L))),
                    historyIds.get(i));
        }
        jdbc.update("update payments set created_at = ?, updated_at = ? where order_id = ?",
                Timestamp.from(base.plus(Duration.ofMinutes(5))), Timestamp.from(base.plus(Duration.ofMinutes(5))),
                orderId);
        jdbc.update("update notifications set created_at = ? where order_id = ?", created, orderId);
    }
}
