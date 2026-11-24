package com.portafolio.pedidos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.portafolio.pedidos.entity.*;
import com.portafolio.pedidos.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Pruebas mínimas de aceptación de la guía, de punta a punta contra la API (H2 en memoria). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrdersApiIntegrationTest {

    static final String PASSWORD = "Password123";
    static final Map<String, Object> ADDRESS = Map.of(
            "recipient", "Cliente Prueba", "phone", "55 1111 2222", "line1", "Calle Falsa 123",
            "city", "Ciudad de México", "state", "CDMX", "postalCode", "06600");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;

    String customerA;
    String customerB;
    String adminToken;
    String operatorToken;
    Category category;

    @BeforeEach
    void setUp() throws Exception {
        for (String table : List.of("notifications", "order_status_history", "payments", "order_items", "orders",
                "cart_items", "products", "categories", "users")) {
            jdbc.update("delete from " + table);
        }
        String hash = encoder.encode(PASSWORD);
        users.save(new User("Cliente A", "a@test.local", hash, Role.CUSTOMER));
        users.save(new User("Cliente B", "b@test.local", hash, Role.CUSTOMER));
        users.save(new User("Admin", "admin@test.local", hash, Role.ADMIN));
        users.save(new User("Operador", "op@test.local", hash, Role.OPERATOR));
        customerA = login("a@test.local");
        customerB = login("b@test.local");
        adminToken = login("admin@test.local");
        operatorToken = login("op@test.local");
        category = categories.save(new Category("Audio", "audio"));
    }

    // ---------- Carrito ----------

    @Test
    @DisplayName("Agregar, actualizar y quitar productos del carrito")
    void cartCrud() throws Exception {
        Product p = product("SKU-1", "500.00", 10);
        perform(post("/api/cart"), customerA, Map.of("productId", p.getId(), "quantity", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.subtotal").value(1000.00))
                .andExpect(jsonPath("$.shippingCost").value(99.00))
                .andExpect(jsonPath("$.total").value(1099.00));
        // Agregar el mismo producto suma la cantidad
        JsonNode cart = body(perform(post("/api/cart"), customerA, Map.of("productId", p.getId(), "quantity", 1))
                .andExpect(jsonPath("$.items[0].quantity").value(3)));
        long itemId = cart.at("/items/0/id").asLong();

        perform(patch("/api/cart/items/" + itemId), customerA, Map.of("quantity", 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(4))
                .andExpect(jsonPath("$.subtotal").value(2000.00))
                .andExpect(jsonPath("$.shippingCost").value(0)); // envío gratis desde 1500

        // Otro cliente no puede tocar ese artículo
        perform(delete("/api/cart/items/" + itemId), customerB, null).andExpect(status().isNotFound());

        perform(delete("/api/cart/items/" + itemId), customerA, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.canCheckout").value(false));
    }

    @Test
    @DisplayName("Rechazar cantidad cero, negativa o mayor al stock")
    void rejectsInvalidQuantities() throws Exception {
        Product p = product("SKU-1", "100.00", 3);
        perform(post("/api/cart"), customerA, Map.of("productId", p.getId(), "quantity", 0))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("quantity"));
        perform(post("/api/cart"), customerA, Map.of("productId", p.getId(), "quantity", -2))
                .andExpect(status().isBadRequest());
        perform(post("/api/cart"), customerA, Map.of("productId", p.getId(), "quantity", 4))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"));
    }

    @Test
    @DisplayName("Productos inactivos no se pueden agregar ni se ven en el catálogo público")
    void inactiveProduct() throws Exception {
        Product p = product("SKU-OFF", "100.00", 3);
        p.setActive(false);
        products.save(p);
        perform(post("/api/cart"), customerA, Map.of("productId", p.getId(), "quantity", 1))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCT_UNAVAILABLE"));
        mvc.perform(get("/api/products/" + p.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$.totalElements").value(0));
    }

    // ---------- Pedido ----------

    @Test
    @DisplayName("Confirmar pedido con totales calculados en el backend, descuento de stock y carrito vacío")
    void createOrder() throws Exception {
        Product a = product("SKU-A", "349.50", 10);
        Product b = product("SKU-B", "120.00", 5);
        addToCart(customerA, a, 2);
        addToCart(customerA, b, 1);

        // Aunque el cliente envíe importes, se ignoran: el cuerpo no tiene campos de precio.
        JsonNode order = body(perform(post("/api/orders"), customerA,
                Map.of("shippingAddress", ADDRESS, "total", 1, "subtotal", 1))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/orders/")))
                .andExpect(jsonPath("$.number", startsWith("PED-")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.subtotal").value(819.00))
                .andExpect(jsonPath("$.shippingCost").value(99.00))
                .andExpect(jsonPath("$.total").value(918.00))
                .andExpect(jsonPath("$.itemCount").value(3))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.history[0].to").value("PENDING"))
                .andExpect(jsonPath("$.canPay").value(true)));

        assertThat(stock(a)).isEqualTo(8);
        assertThat(stock(b)).isEqualTo(4);
        perform(get("/api/cart"), customerA, null).andExpect(jsonPath("$.items", hasSize(0)));
        perform(get("/api/orders/my"), customerA, null)
                .andExpect(jsonPath("$.content[0].number").value(order.get("number").asText()));
        perform(get("/api/notifications/my"), customerA, null)
                .andExpect(jsonPath("$[0].subject", containsString(order.get("number").asText())));
    }

    @Test
    @DisplayName("Validar dirección y carrito vacío antes de confirmar")
    void validatesCheckout() throws Exception {
        perform(post("/api/orders"), customerA, Map.of("shippingAddress", ADDRESS))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));
        addToCart(customerA, product("SKU-1", "10.00", 5), 1);
        perform(post("/api/orders"), customerA,
                Map.of("shippingAddress", Map.of("recipient", "", "phone", "x", "line1", "a", "city", "b",
                        "state", "c", "postalCode", "123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItems(
                        "shippingAddress.recipient", "shippingAddress.phone", "shippingAddress.postalCode")));
    }

    @Test
    @DisplayName("El precio histórico no cambia al editar el producto")
    void historicalPriceIsKept() throws Exception {
        Product p = product("SKU-1", "500.00", 10);
        addToCart(customerA, p, 1);
        long orderId = createOrder(customerA);

        JsonNode current = body(perform(get("/api/admin/products/" + p.getId()), adminToken, null));
        perform(put("/api/admin/products/" + p.getId()), adminToken, Map.of(
                "sku", "SKU-1", "name", "Nombre nuevo", "price", 999.99, "categoryId", category.getId(),
                "version", current.get("version").asLong()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(999.99))
                .andExpect(jsonPath("$.stock").value(9)); // editar no toca el stock

        perform(get("/api/orders/" + orderId), customerA, null)
                .andExpect(jsonPath("$.items[0].name").value("Producto SKU-1"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(500.00))
                .andExpect(jsonPath("$.total").value(599.00));
    }

    @Test
    @DisplayName("Editar con una versión vieja responde 409 (bloqueo optimista)")
    void optimisticLockOnProduct() throws Exception {
        Product p = product("SKU-1", "500.00", 10);
        Map<String, Object> body = Map.of("sku", "SKU-1", "name", "X", "price", 10, "categoryId", category.getId(),
                "version", p.getVersion());
        perform(put("/api/admin/products/" + p.getId()), adminToken, body).andExpect(status().isOk());
        perform(put("/api/admin/products/" + p.getId()), adminToken, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    @DisplayName("Rechazar compra sin stock (el stock cambió después de agregar al carrito)")
    void rejectsOutOfStockAtCheckout() throws Exception {
        Product p = product("SKU-1", "100.00", 2);
        addToCart(customerA, p, 2);
        addToCart(customerB, p, 1);
        createOrder(customerB);
        perform(post("/api/orders"), customerA, Map.of("shippingAddress", ADDRESS))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.message", containsString("Solo quedan 1")));
        assertThat(stock(p)).isEqualTo(1);
        // El carrito de A se conserva para que pueda ajustar la cantidad
        perform(get("/api/cart"), customerA, null)
                .andExpect(jsonPath("$.items[0].issue", containsString("Solo quedan 1")))
                .andExpect(jsonPath("$.canCheckout").value(false));
    }

    @Test
    @DisplayName("Si una partida no alcanza, se revierte el descuento de las demás (transacción)")
    void checkoutIsAtomic() throws Exception {
        Product ok = product("SKU-A", "100.00", 5);
        Product scarce = product("SKU-B", "100.00", 1);
        addToCart(customerA, ok, 2);
        addToCart(customerA, scarce, 1);
        // Se agota mientras el cliente decide
        perform(patch("/api/admin/products/" + scarce.getId() + "/stock"), adminToken, Map.of("delta", -1))
                .andExpect(status().isOk());
        perform(post("/api/orders"), customerA, Map.of("shippingAddress", ADDRESS))
                .andExpect(status().isUnprocessableEntity());
        assertThat(stock(ok)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from orders", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Concurrencia: 8 clientes compran a la vez las últimas 3 unidades y solo 3 lo logran")
    void concurrentPurchaseOfLastUnits() throws Exception {
        Product p = product("SKU-HOT", "100.00", 3);
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String email = "c" + i + "@test.local";
            users.save(new User("C" + i, email, encoder.encode(PASSWORD), Role.CUSTOMER));
            String t = login(email);
            addToCart(t, p, 1);
            tokens.add(t);
        }
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String t : tokens) {
            results.add(pool.submit(() -> {
                start.await();
                return perform(post("/api/orders"), t, Map.of("shippingAddress", ADDRESS))
                        .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get(30, TimeUnit.SECONDS));
        pool.shutdown();

        assertThat(statuses).filteredOn(s -> s == 201).hasSize(3);
        assertThat(statuses).filteredOn(s -> s == 422).hasSize(5);
        assertThat(stock(p)).isZero();
        assertThat(jdbc.queryForObject("select coalesce(sum(quantity),0) from order_items", Integer.class))
                .isEqualTo(3);
    }

    // ---------- Pago y estados ----------

    @Test
    @DisplayName("Pago simulado: rechazado deja PENDING, aprobado pasa a PAID; no se puede pagar dos veces")
    void simulatedPayment() throws Exception {
        addToCart(customerA, product("SKU-1", "200.00", 5), 1);
        long orderId = createOrder(customerA);
        perform(post("/api/payments/simulate"), customerA, Map.of("orderId", orderId, "outcome", "DECLINED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("DECLINED"))
                .andExpect(jsonPath("$.order.status").value("PENDING"));
        perform(post("/api/payments/simulate"), customerA, Map.of("orderId", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment.status").value("APPROVED"))
                .andExpect(jsonPath("$.payment.amount").value(299.00))
                .andExpect(jsonPath("$.order.status").value("PAID"))
                .andExpect(jsonPath("$.order.payments", hasSize(2)));
        perform(post("/api/payments/simulate"), customerA, Map.of("orderId", orderId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_PAYABLE"));
        // Otro cliente no puede pagar (ni ver) el pedido
        perform(post("/api/payments/simulate"), customerB, Map.of("orderId", orderId))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Transiciones válidas e inválidas de estado y permisos por rol")
    void statusTransitions() throws Exception {
        addToCart(customerA, product("SKU-1", "200.00", 5), 1);
        long orderId = createOrder(customerA);
        String url = "/api/admin/orders/" + orderId;

        // No se puede preparar un pedido sin pagar
        perform(patch(url), operatorToken, Map.of("status", "PROCESSING"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        // PAID solo lo asigna un pago
        perform(patch(url), adminToken, Map.of("status", "PAID"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PAYMENT_REQUIRED"));

        pay(customerA, orderId);
        perform(patch(url), operatorToken, Map.of("status", "PROCESSING")).andExpect(status().isOk());
        // El operador no puede cancelar
        perform(patch(url), operatorToken, Map.of("status", "CANCELLED")).andExpect(status().isForbidden());
        perform(patch(url), operatorToken, Map.of("status", "SHIPPED", "note", "Guía 123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowedTransitions", contains("DELIVERED")));
        // Ya enviado no se puede cancelar ni retroceder
        perform(patch(url), adminToken, Map.of("status", "CANCELLED"))
                .andExpect(status().isUnprocessableEntity());
        perform(patch(url), adminToken, Map.of("status", "PROCESSING"))
                .andExpect(status().isUnprocessableEntity());
        perform(patch(url), operatorToken, Map.of("status", "DELIVERED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.history[*].to",
                        contains("PENDING", "PAID", "PROCESSING", "SHIPPED", "DELIVERED")))
                .andExpect(jsonPath("$.allowedTransitions", hasSize(0)));

        // El cliente ve el historial, pero no los correos del personal
        perform(get("/api/orders/" + orderId), customerA, null)
                .andExpect(jsonPath("$.history[2].actor").value("Equipo de la tienda"))
                .andExpect(jsonPath("$.history[0].actor").value("a@test.local"));
    }

    @Test
    @DisplayName("Cliente A no puede consultar el pedido del cliente B; el cliente no entra al panel admin")
    void ownership() throws Exception {
        addToCart(customerB, product("SKU-1", "200.00", 5), 1);
        long orderOfB = createOrder(customerB);
        perform(get("/api/orders/" + orderOfB), customerA, null)
                .andExpect(status().isNotFound());
        perform(post("/api/orders/" + orderOfB + "/cancel"), customerA, null)
                .andExpect(status().isNotFound());
        perform(get("/api/orders/my"), customerA, null).andExpect(jsonPath("$.totalElements").value(0));
        perform(get("/api/admin/orders/" + orderOfB), customerA, null).andExpect(status().isForbidden());
        perform(get("/api/admin/reports/summary"), operatorToken, null).andExpect(status().isForbidden());
        perform(get("/api/cart"), adminToken, null).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/my")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Cancelar devuelve el stock, reembolsa el pago y deja historial")
    void cancelRestoresStock() throws Exception {
        Product p = product("SKU-1", "200.00", 5);
        addToCart(customerA, p, 2);
        long orderId = createOrder(customerA);
        pay(customerA, orderId);
        assertThat(stock(p)).isEqualTo(3);

        perform(post("/api/orders/" + orderId + "/cancel"), customerA, Map.of("reason", "Ya no lo necesito"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.payments[0].status").value("REFUNDED"))
                .andExpect(jsonPath("$.history[-1:].from", contains("PAID")))
                .andExpect(jsonPath("$.history[-1:].note", contains("Ya no lo necesito")))
                .andExpect(jsonPath("$.canCancel").value(false));
        assertThat(stock(p)).isEqualTo(5);

        // Cancelar dos veces no devuelve stock dos veces
        perform(post("/api/orders/" + orderId + "/cancel"), customerA, null)
                .andExpect(status().isUnprocessableEntity());
        assertThat(stock(p)).isEqualTo(5);
    }

    @Test
    @DisplayName("El cliente no puede cancelar un pedido en preparación; el admin sí y el stock vuelve")
    void customerCannotCancelProcessing() throws Exception {
        Product p = product("SKU-1", "200.00", 5);
        addToCart(customerA, p, 1);
        long orderId = createOrder(customerA);
        pay(customerA, orderId);
        perform(patch("/api/admin/orders/" + orderId), operatorToken, Map.of("status", "PROCESSING"))
                .andExpect(status().isOk());
        perform(post("/api/orders/" + orderId + "/cancel"), customerA, null)
                .andExpect(status().isUnprocessableEntity());
        perform(patch("/api/admin/orders/" + orderId), adminToken, Map.of("status", "CANCELLED", "note", "Sin envío"))
                .andExpect(status().isOk());
        assertThat(stock(p)).isEqualTo(5);
    }

    @Test
    @DisplayName("Ajuste de inventario nunca deja stock negativo")
    void stockAdjustment() throws Exception {
        Product p = product("SKU-1", "200.00", 2);
        perform(patch("/api/admin/products/" + p.getId() + "/stock"), adminToken, Map.of("delta", 5))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stock").value(7));
        perform(patch("/api/admin/products/" + p.getId() + "/stock"), adminToken, Map.of("delta", -8))
                .andExpect(status().isUnprocessableEntity());
        perform(patch("/api/admin/products/" + p.getId() + "/stock"), adminToken, Map.of("delta", 0))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Catálogo público con búsqueda, filtros y paginación; registro de clientes")
    void catalogAndRegister() throws Exception {
        product("SKU-1", "100.00", 0);
        product("SKU-2", "300.00", 4);
        product("SKU-3", "900.00", 10);
        mvc.perform(get("/api/products").param("minPrice", "200").param("sort", "price,desc"))
                .andExpect(jsonPath("$.content[*].sku", contains("SKU-3", "SKU-2")));
        mvc.perform(get("/api/products").param("inStock", "true").param("q", "sku-1"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/products").param("size", "1").param("category", "audio"))
                .andExpect(jsonPath("$.totalPages").value(3));
        mvc.perform(get("/api/products").param("sort", "hack"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/categories")).andExpect(jsonPath("$[0].productCount").value(3));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Nuevo", "email", "NUEVO@test.local",
                                "password", "abc12345"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.email").value("nuevo@test.local"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Otro", "email", "nuevo@test.local",
                                "password", "abc12345"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    @DisplayName("Reporte del panel: ventas, estados y stock bajo")
    void report() throws Exception {
        Product p = product("SKU-1", "1000.00", 6);
        addToCart(customerA, p, 2);
        pay(customerA, createOrder(customerA));
        addToCart(customerB, p, 1);
        createOrder(customerB);
        perform(get("/api/admin/reports/summary").param("days", "7"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revenue").value(2000.00))
                .andExpect(jsonPath("$.paidOrders").value(1))
                .andExpect(jsonPath("$.ordersByStatus.PENDING").value(1))
                .andExpect(jsonPath("$.salesByDay", hasSize(7)))
                .andExpect(jsonPath("$.topProducts[0].units").value(2))
                .andExpect(jsonPath("$.lowStock[0].sku").value("SKU-1"));
    }

    // ---------- utilidades ----------

    private Product product(String sku, String price, int stock) {
        return products.save(new Product(sku, "Producto " + sku, "Descripción", new BigDecimal(price), stock, null,
                category));
    }

    private int stock(Product p) {
        return products.findStockById(p.getId()).orElseThrow();
    }

    private void addToCart(String token, Product p, int qty) throws Exception {
        perform(post("/api/cart"), token, Map.of("productId", p.getId(), "quantity", qty))
                .andExpect(status().isOk());
    }

    private long createOrder(String token) throws Exception {
        return body(perform(post("/api/orders"), token, Map.of("shippingAddress", ADDRESS))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private void pay(String token, long orderId) throws Exception {
        perform(post("/api/payments/simulate"), token, Map.of("orderId", orderId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.order.status").value("PAID"));
    }

    private String login(String email) throws Exception {
        return body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())).get("token").asText();
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                                  String token, Object body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(req);
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }
}
