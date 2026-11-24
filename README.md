# Sistema de Pedidos · Tienda Nova

Tienda web de demostración donde un **cliente** explora el catálogo, arma un carrito persistente, confirma un pedido, lo paga (pago simulado) y sigue su estado; un **administrador** gestiona productos, inventario y pedidos, y un **operador** actualiza la preparación y el envío.

El foco del proyecto está en las reglas que hacen confiable una compra:

- Los importes se calculan **siempre en el backend**; el navegador nunca envía precios ni totales.
- Cada partida guarda un **snapshot** de SKU, nombre y precio: editar el catálogo no altera compras previas.
- El stock se descuenta con un **UPDATE atómico condicional**, así dos compras simultáneas no pueden vender las mismas últimas unidades (probado con 8 hilos concurrentes).
- El pedido sigue una **máquina de estados** explícita con permisos por rol e **historial** de cada cambio.

> Proyecto de portafolio con datos y pagos **ficticios**. No ingreses datos reales.

**Stack:** Java 17/21 · Spring Boot 3.3 (Web, Data JPA, Validation, Security + JWT) · Flyway · MySQL 8 (H2 en local) · React 18 + TypeScript + Vite · Docker Compose · JUnit 5 + MockMvc · GitHub Actions.

---

## Funcionalidades

| Área | Qué incluye |
|---|---|
| Catálogo (público) | Categorías con contador, búsqueda por nombre/SKU/descripción, filtros (precio, solo disponibles), ordenamiento, paginación, detalle con disponibilidad |
| Carrito | Persistente por usuario en el servidor; agregar, cambiar cantidad, quitar; subtotal, envío y total calculados por el backend; avisos si un artículo se agotó o cambió |
| Checkout | Dirección de entrega validada, resumen, confirmación; número de pedido único (`PED-251123-K7Q2MX`) |
| Pago | Pasarela **simulada** (aprobar o rechazar); sin datos de tarjeta; reintentos si se rechaza |
| Cliente | Historial con filtro por estado, detalle con progreso, pagos e historial, cancelación (PENDING/PAID), bandeja de correos simulados |
| Administración | Panel con ventas por día, ticket promedio, pedidos por estado, más vendidos y stock bajo; tabla de pedidos con filtros por estado, texto y fechas; cambio de estado con nota; CRUD de productos y ajustes de inventario |
| Calidad | Errores con formato único y códigos HTTP correctos, OpenAPI/Swagger, colección Postman, 37 pruebas automatizadas, CI |

## Capturas

Ejecuta el proyecto y abre <http://localhost:5176> (local) o <http://localhost:3000> (Docker). Pantallas principales:

1. **Inicio**: hero, categorías, búsqueda y grilla de productos.
2. **Detalle de producto**: precio, disponibilidad y selector de cantidad.
3. **Carrito**: cantidades, subtotal, barra de envío gratis y total estimado.
4. **Checkout**: dirección y resumen.
5. **Pago simulado** → **pantalla de éxito** con número y estado.
6. **Mis pedidos**: historial, detalle con línea de tiempo y correos recibidos.
7. **Panel admin**: métricas, pedidos con filtros, cambio de estado y productos.

## Arquitectura

```
┌──────────────────────┐   /api (JSON + JWT)   ┌──────────────────────────────────────┐    ┌─────────┐
│ React + TypeScript   │ ────────────────────▶ │ Spring Boot                          │───▶│ MySQL 8 │
│ (Vite / Nginx)       │                       │ controller → service → repository    │    │ Flyway  │
└──────────────────────┘                       │ security (JWT, roles) · exception    │    └─────────┘
                                               └──────────────────────────────────────┘
```

```
backend/src/main/java/com/portafolio/pedidos
├── config        Seguridad, OpenAPI, propiedades, datos demo
├── controller    REST: catálogo, carrito, pedidos, pagos, admin
├── dto           Requests / Responses (nunca se exponen entidades)
├── entity        Modelo JPA + máquina de estados (OrderStatus)
├── exception     ApiError uniforme + GlobalExceptionHandler
├── mapper        Entidad → DTO
├── repository    Spring Data + Specifications + UPDATE atómicos de stock
├── security      JWT, usuario actual, respuestas 401/403 en JSON
├── service       Reglas de negocio (OrderService, OrderWorkflow, OrderPolicy, CartService…)
└── validation    Validaciones personalizadas
```

### Modelo de datos (ERD)

```mermaid
erDiagram
    USERS ||--o{ CART_ITEMS : tiene
    USERS ||--o{ ORDERS : realiza
    USERS ||--o{ NOTIFICATIONS : recibe
    CATEGORIES ||--o{ PRODUCTS : agrupa
    PRODUCTS ||--o{ CART_ITEMS : "en carrito"
    PRODUCTS ||--o{ ORDER_ITEMS : "referenciado por"
    ORDERS ||--|{ ORDER_ITEMS : contiene
    ORDERS ||--o{ PAYMENTS : "intentos de pago"
    ORDERS ||--|{ ORDER_STATUS_HISTORY : "cambios de estado"

    USERS { bigint id PK; varchar email UK; varchar role; boolean active }
    CATEGORIES { bigint id PK; varchar name UK; varchar slug UK }
    PRODUCTS { bigint id PK; varchar sku UK; varchar name; decimal price; int stock "CHECK >= 0"; varchar image_url; boolean active; bigint version }
    CART_ITEMS { bigint id PK; bigint user_id FK; bigint product_id FK; int quantity "UK(user, product)" }
    ORDERS { bigint id PK; varchar order_number UK; bigint user_id FK; varchar status; varchar ship_* "snapshot dirección"; decimal subtotal; decimal shipping_cost; decimal total; bigint version }
    ORDER_ITEMS { bigint id PK; bigint order_id FK; bigint product_id; varchar sku_snapshot; varchar product_name_snapshot; decimal unit_price_snapshot; int quantity; decimal line_total }
    PAYMENTS { bigint id PK; bigint order_id FK; varchar provider; varchar reference UK; decimal amount; varchar status }
    ORDER_STATUS_HISTORY { bigint id PK; bigint order_id FK; varchar from_status; varchar to_status; datetime changed_at; varchar actor; varchar note }
    NOTIFICATIONS { bigint id PK; bigint user_id FK; bigint order_id FK; varchar subject; varchar body }
```

## Reglas de negocio y cómo se garantizan

### Ciclo de vida del pedido

```
PENDING ──pago aprobado──▶ PAID ──▶ PROCESSING ──▶ SHIPPED ──▶ DELIVERED
   │                        │            │
   └────────────────────────┴────────────┴──────▶ CANCELLED
```

| Regla | Implementación |
|---|---|
| Importes calculados en el servidor | `CreateOrderRequest` solo contiene la dirección. `OrderService.create` relee productos y precios en la misma transacción; envío = $99 o gratis desde $1,500 (`PricingService`, configurable) |
| Precio histórico | `OrderItem` guarda `sku_snapshot`, `product_name_snapshot`, `unit_price_snapshot` y `line_total` |
| Validaciones antes de confirmar | Cantidad 1–20 por producto, producto activo, stock suficiente, dirección completa (CP de 5 dígitos, teléfono), carrito no vacío |
| Transaccionalidad | Crear el pedido, descontar stock de todas las partidas, guardar historial y vaciar el carrito ocurre en **una** transacción; si una partida no alcanza, se revierte todo |
| Sin sobreventa | `UPDATE products SET stock = stock - :q WHERE id = :id AND active AND stock >= :q`; si afecta 0 filas, se rechaza con 422. Las partidas se procesan en orden de id para evitar interbloqueos. `CHECK (stock >= 0)` como última defensa |
| La edición del catálogo no pisa el stock | La columna `stock` es `updatable = false` en la entidad: solo cambia por UPDATE atómicos (compra, cancelación, ajuste) |
| Cuándo se descuenta y se devuelve stock | Se **descuenta al confirmar** el pedido (reserva). Se **devuelve al cancelar** desde PENDING, PAID o PROCESSING. Desde SHIPPED/DELIVERED no se puede cancelar (sería una devolución) |
| Transiciones permitidas | `OrderStatus.next()` define la máquina de estados; `OrderPolicy` define qué rol puede ejecutar cada una |
| Pagos | `PAID` solo lo asigna un pago aprobado. Al cancelar un pedido pagado, el pago pasa a `REFUNDED`. No se guardan datos de tarjeta |
| Concurrencia en estados | `@Version` en `Order` y `Product`: dos cambios simultáneos → el segundo recibe 409 |
| Propiedad de pedidos | Los pedidos del cliente se buscan por `id + user_id`; un pedido ajeno responde **404** (no revela que existe) |
| Historial | Cada transición escribe `order_status_history` (estado anterior, nuevo, fecha, actor, nota). Al cliente se le oculta el email del personal |

### Permisos por rol

| Acción | Cliente | Operador | Admin |
|---|:-:|:-:|:-:|
| Ver catálogo | ✓ (también sin sesión) | ✓ | ✓ |
| Carrito, checkout, pago, historial propio | ✓ | | |
| Cancelar pedido propio (PENDING / PAID) | ✓ | | |
| Ver y filtrar todos los pedidos | | ✓ | ✓ |
| PAID → PROCESSING → SHIPPED → DELIVERED | | ✓ | ✓ |
| Cancelar cualquier pedido no enviado | | | ✓ |
| Productos, inventario, categorías, reportes | | | ✓ |

## API REST

Documentación interactiva: **`/swagger-ui.html`** · Colección Postman: [`postman/pedidos-api.postman_collection.json`](postman/pedidos-api.postman_collection.json) (con ejemplos de éxito y error; el login guarda el token automáticamente).

| Método | Ruta | Responsabilidad | Rol |
|---|---|---|---|
| POST | `/api/auth/register` · `/api/auth/login` | Registro de cliente / inicio de sesión (JWT) | público |
| GET | `/api/auth/me` | Usuario actual | autenticado |
| GET | `/api/products` | Catálogo con `q`, `category`, `minPrice`, `maxPrice`, `inStock`, `page`, `size`, `sort` | público |
| GET | `/api/products/{id}` | Detalle del producto | público |
| GET | `/api/categories` | Categorías con número de productos | público |
| GET / POST / DELETE | `/api/cart` | Consultar / agregar artículo / vaciar | cliente |
| PATCH / DELETE | `/api/cart/items/{id}` | Cambiar cantidad / quitar artículo | cliente |
| POST | `/api/orders` | Confirmar pedido (201 + `Location`) | cliente |
| GET | `/api/orders/my` | Historial del cliente (paginado, filtro por estado) | cliente |
| GET | `/api/orders/{id}` | Detalle de un pedido propio | cliente |
| POST | `/api/orders/{id}/cancel` | Cancelar pedido propio | cliente |
| POST | `/api/payments/simulate` | Simular pago (`outcome`: APPROVED / DECLINED) | cliente |
| GET | `/api/notifications/my` | Correos simulados recibidos | autenticado |
| GET | `/api/admin/orders` | Pedidos con filtros `status`, `q`, `from`, `to` | admin, operador |
| GET / PATCH | `/api/admin/orders/{id}` | Consultar / cambiar estado | admin, operador |
| GET / POST / PUT | `/api/admin/products[/{id}]` | Listar (incluye inactivos) / crear / editar (con `version`) | admin |
| PATCH | `/api/admin/products/{id}/stock` | Ajuste de inventario (`delta` ≠ 0) | admin |
| POST | `/api/admin/categories` | Crear categoría | admin |
| GET | `/api/admin/reports/summary?days=30` | Ventas, estados, más vendidos, stock bajo | admin |

### Formato de error

```json
{
  "timestamp": "2026-11-23T18:20:11Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "INSUFFICIENT_STOCK",
  "message": "Solo quedan 1 unidades de Reloj clásico Meridiano",
  "path": "/api/orders"
}
```

| HTTP | Cuándo | Códigos |
|---|---|---|
| 400 | Datos inválidos (`fieldErrors` por campo) o parámetros incorrectos | `VALIDATION_ERROR`, `BAD_REQUEST` |
| 401 | Sin sesión, token inválido o credenciales incorrectas | `UNAUTHORIZED`, `INVALID_CREDENTIALS` |
| 403 | El rol no puede hacer la acción | `FORBIDDEN` |
| 404 | Recurso inexistente o ajeno | `NOT_FOUND` |
| 409 | Duplicados o modificación concurrente | `EMAIL_TAKEN`, `SKU_TAKEN`, `CONCURRENT_MODIFICATION` |
| 422 | Regla de negocio | `CART_EMPTY`, `INSUFFICIENT_STOCK`, `PRODUCT_UNAVAILABLE`, `QUANTITY_LIMIT`, `INVALID_TRANSITION`, `PAYMENT_REQUIRED`, `ORDER_NOT_PAYABLE` |

## Ejecutar

### Opción A: Docker Compose (MySQL + API + frontend)

```bash
cp .env.example .env        # y cambia contraseñas y JWT_SECRET
docker compose up --build
```

- Tienda: <http://localhost:3000>
- Swagger: <http://localhost:8080/swagger-ui.html>

### Opción B: desarrollo local (sin Docker)

Requisitos: **Java 17 o 21** y **Node 20+**. El perfil `local` usa H2 en memoria (modo MySQL) y carga los datos demo en cada arranque.

```bash
# Terminal 1: API en http://localhost:8080
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run

# Terminal 2: frontend en http://localhost:5176 (redirige /api al backend)
cd frontend
npm install
npm run dev
```

Si el backend usa otro puerto: `PORT=8084 ./mvnw spring-boot:run` y `VITE_API_PROXY=http://localhost:8084 npm run dev`.

### Cuentas de demostración

Contraseña de todas: `Demo1234!` (configurable con `DEMO_PASSWORD`). Son ficticias.

| Rol | Email | Qué probar |
|---|---|---|
| Cliente | `cliente@demo.local` | Ya tiene artículos en el carrito y pedidos en varios estados |
| Cliente | `cliente2@demo.local` | Otro cliente (no puede ver los pedidos del primero) |
| Operador | `operador@demo.local` | Preparar, enviar y entregar pedidos pagados |
| Admin | `admin@demo.local` | Panel, productos, inventario, cancelaciones |

### Variables de entorno

| Variable | Por defecto | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `local` (H2) o `prod` (MySQL) |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | — | Conexión MySQL (perfil `prod`) |
| `JWT_SECRET` | solo dev | Clave HS256, mínimo 32 caracteres |
| `JWT_EXPIRATION_MINUTES` | `480` | Vigencia del token |
| `SHIPPING_FLAT_RATE` / `SHIPPING_FREE_FROM` | `99.00` / `1500.00` | Costo de envío y umbral de envío gratis |
| `MAX_QUANTITY_PER_ITEM` | `20` | Máximo de unidades por producto en un pedido |
| `SEED_DEMO_DATA` / `DEMO_PASSWORD` | `true` / `Demo1234!` | Datos ficticios si la base está vacía |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5176` | Orígenes permitidos |
| `PORT` | `8080` | Puerto del backend |

## Pruebas

```bash
cd backend && ./mvnw verify      # 37 pruebas: unitarias + integración (H2 en memoria)
cd frontend && npm run build     # type-check + build
```

Cobertura de las pruebas mínimas de aceptación (`OrdersApiIntegrationTest`):

| Prueba de la guía | Test |
|---|---|
| Agregar, actualizar y quitar del carrito | `cartCrud` |
| Rechazar cantidad cero, negativa o mayor al stock | `rejectsInvalidQuantities` |
| Confirmar pedido con totales del backend | `createOrder` (además envía `total: 1` y se ignora) |
| Precio histórico no cambia al editar el producto | `historicalPriceIsKept` |
| Rechazar compra sin stock | `rejectsOutOfStockAtCheckout`, `checkoutIsAtomic` |
| Concurrencia en últimas unidades | `concurrentPurchaseOfLastUnits` (8 hilos, 3 unidades → 3 éxitos, 5 rechazos, stock 0) |
| Transiciones válidas e inválidas | `statusTransitions` + `OrderStatusTest` (15 casos) |
| Cliente A no ve pedidos de B | `ownership` |
| Cancelar aplica la política de stock y deja historial | `cancelRestoresStock`, `customerCannotCancelProcessing` |

Además: pago simulado, bloqueo optimista, ajuste de inventario, catálogo/registro, reportes y carga de datos demo (`DemoDataSeederTest`).

CI: [`.github/workflows/ci.yml`](.github/workflows/ci.yml) ejecuta las pruebas del backend con Java 21 y el build del frontend en cada push y PR.

## Decisiones técnicas

- **Reservar stock al confirmar, no al agregar al carrito.** Un carrito abandonado no bloquea inventario; el carrito valida contra el stock vigente y el checkout vuelve a validar de forma atómica.
- **UPDATE condicional en vez de bloqueo pesimista o reintentos optimistas sobre `Product`.** Es una sola sentencia por partida, no depende de la versión del producto (que cambia al editar el catálogo) y funciona igual en MySQL y H2.
- **Número de pedido aleatorio y legible** en lugar del id: no revela el volumen de ventas; la restricción `UNIQUE` cubre colisiones.
- **404 para pedidos ajenos** en vez de 403, para no confirmar su existencia.
- **`PAID` no se puede asignar manualmente**: el único camino es un pago aprobado, lo que mantiene coherentes pagos y estados.
- **Pago simulado detrás de un servicio** (`PaymentService`): integrar un proveedor real en modo test (p. ej. Stripe) consiste en crear el intento de pago allí y confirmar el pedido desde su webhook.
- **Correos simulados persistidos** (`notifications`) para que el flujo sea visible en la demo sin configurar SMTP.

## Limitaciones y mejoras posibles

- Los pedidos PENDING reservan stock indefinidamente; un job que cancele pedidos sin pagar tras N minutos liberaría inventario.
- Sin refresh tokens (el JWT dura 8 h) ni recuperación de contraseña; el proyecto de autenticación del portafolio cubre esos temas.
- El ajuste de inventario no guarda un kardex de movimientos (solo se registra en el log).
- Los productos demo usan ilustraciones por categoría; se puede asignar una URL de imagen desde el panel.
- Reportes calculados en memoria: suficientes para la demo; a mayor volumen convendría agregarlos en SQL o en una tabla de resumen.
- Mejoras naturales: idempotency key en `POST /api/orders`, Testcontainers con MySQL real, direcciones guardadas por cliente y cupones.
