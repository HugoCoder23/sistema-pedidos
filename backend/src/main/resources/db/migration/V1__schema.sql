-- Esquema del sistema de pedidos. Compatible con MySQL 8 y con H2 en modo MySQL.
-- Los importes se guardan como DECIMAL(12,2) (nunca como coma flotante).

CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE categories (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(60) NOT NULL,
    slug VARCHAR(60) NOT NULL,
    CONSTRAINT uk_categories_name UNIQUE (name),
    CONSTRAINT uk_categories_slug UNIQUE (slug)
);

CREATE TABLE products (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku         VARCHAR(40)    NOT NULL,
    name        VARCHAR(150)   NOT NULL,
    description VARCHAR(2000),
    price       DECIMAL(12, 2) NOT NULL,
    -- Unidades disponibles para vender. Se descuenta con un UPDATE condicional (stock >= cantidad)
    -- para que dos compras simultáneas nunca vendan la misma unidad.
    stock       INT            NOT NULL,
    image_url   VARCHAR(500),
    active      BOOLEAN        NOT NULL DEFAULT TRUE,
    category_id BIGINT         NOT NULL,
    created_at  DATETIME(6)    NOT NULL,
    updated_at  DATETIME(6)    NOT NULL,
    version     BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_products_sku UNIQUE (sku),
    CONSTRAINT ck_products_stock CHECK (stock >= 0),
    CONSTRAINT ck_products_price CHECK (price > 0),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id)
);
CREATE INDEX ix_products_category ON products (category_id);
CREATE INDEX ix_products_active_name ON products (active, name);

-- Carrito persistente: una fila por (usuario, producto). El precio NO se guarda aquí:
-- el carrito siempre muestra el precio vigente y el pedido congela el precio al confirmar.
CREATE TABLE cart_items (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_cart_items_user_product UNIQUE (user_id, product_id),
    CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0),
    CONSTRAINT fk_cart_items_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE TABLE orders (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_number      VARCHAR(30)    NOT NULL,
    user_id           BIGINT         NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    -- Snapshot de la dirección de entrega al momento de comprar.
    ship_recipient    VARCHAR(120)   NOT NULL,
    ship_phone        VARCHAR(20)    NOT NULL,
    ship_line1        VARCHAR(160)   NOT NULL,
    ship_line2        VARCHAR(160),
    ship_city         VARCHAR(80)    NOT NULL,
    ship_state        VARCHAR(80)    NOT NULL,
    ship_postal_code  VARCHAR(10)    NOT NULL,
    ship_instructions VARCHAR(300),
    subtotal          DECIMAL(12, 2) NOT NULL,
    shipping_cost     DECIMAL(12, 2) NOT NULL,
    total             DECIMAL(12, 2) NOT NULL,
    item_count        INT            NOT NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_orders_number UNIQUE (order_number),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id)
);
CREATE INDEX ix_orders_user_created ON orders (user_id, created_at);
CREATE INDEX ix_orders_status_created ON orders (status, created_at);

-- Partidas con snapshot de SKU, nombre y precio unitario: editar el catálogo no altera compras previas.
CREATE TABLE order_items (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id              BIGINT         NOT NULL,
    product_id            BIGINT         NOT NULL,
    sku_snapshot          VARCHAR(40)    NOT NULL,
    product_name_snapshot VARCHAR(150)   NOT NULL,
    unit_price_snapshot   DECIMAL(12, 2) NOT NULL,
    quantity              INT            NOT NULL,
    line_total            DECIMAL(12, 2) NOT NULL,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
);
CREATE INDEX ix_order_items_order ON order_items (order_id);

-- Solo datos no sensibles del pago (nunca números de tarjeta).
CREATE TABLE payments (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id       BIGINT         NOT NULL,
    provider       VARCHAR(30)    NOT NULL,
    reference      VARCHAR(40)    NOT NULL,
    amount         DECIMAL(12, 2) NOT NULL,
    status         VARCHAR(20)    NOT NULL,
    failure_reason VARCHAR(200),
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    CONSTRAINT uk_payments_reference UNIQUE (reference),
    CONSTRAINT fk_payments_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);
CREATE INDEX ix_payments_order ON payments (order_id);

CREATE TABLE order_status_history (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT       NOT NULL,
    from_status VARCHAR(20),
    to_status   VARCHAR(20)  NOT NULL,
    changed_at  DATETIME(6)  NOT NULL,
    actor       VARCHAR(160) NOT NULL,
    note        VARCHAR(300),
    CONSTRAINT fk_history_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);
CREATE INDEX ix_history_order ON order_status_history (order_id, changed_at);

-- Bandeja de correos simulados (en la demo no se envía email real).
CREATE TABLE notifications (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    order_id   BIGINT,
    recipient  VARCHAR(160) NOT NULL,
    subject    VARCHAR(200) NOT NULL,
    body       VARCHAR(2000) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_notifications_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at);
