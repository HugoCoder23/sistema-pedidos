package com.portafolio.pedidos.service;

import com.portafolio.pedidos.config.AppProperties;
import com.portafolio.pedidos.dto.Requests.AddToCartRequest;
import com.portafolio.pedidos.dto.Responses.CartItemResponse;
import com.portafolio.pedidos.dto.Responses.CartResponse;
import com.portafolio.pedidos.entity.CartItem;
import com.portafolio.pedidos.entity.Product;
import com.portafolio.pedidos.entity.User;
import com.portafolio.pedidos.exception.BusinessRuleException;
import com.portafolio.pedidos.exception.ResourceNotFoundException;
import com.portafolio.pedidos.repository.CartItemRepository;
import com.portafolio.pedidos.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Carrito persistente por usuario. Valida cantidades contra el stock vigente, pero NO reserva stock:
 * la reserva ocurre al confirmar el pedido (ver {@link OrderService#create}).
 */
@Service
public class CartService {

    private final CartItemRepository cartItems;
    private final ProductRepository products;
    private final PricingService pricing;
    private final int maxPerItem;

    public CartService(CartItemRepository cartItems, ProductRepository products, PricingService pricing,
                       AppProperties props) {
        this.cartItems = cartItems;
        this.products = products;
        this.pricing = pricing;
        this.maxPerItem = props.orders().maxQuantityPerItem();
    }

    @Transactional(readOnly = true)
    public CartResponse get(User user) {
        return build(cartItems.findByUserIdOrderByIdAsc(user.getId()));
    }

    @Transactional
    public CartResponse add(User user, AddToCartRequest request) {
        Product product = products.findById(request.productId())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        ensureActive(product);
        CartItem item = cartItems.findByUserIdAndProductId(user.getId(), product.getId()).orElse(null);
        int newQuantity = (item == null ? 0 : item.getQuantity()) + request.quantity();
        validateQuantity(product, newQuantity);
        if (item == null) {
            cartItems.save(new CartItem(user.getId(), product, newQuantity));
        } else {
            item.setQuantity(newQuantity);
        }
        cartItems.flush();
        return get(user);
    }

    @Transactional
    public CartResponse update(User user, Long itemId, int quantity) {
        CartItem item = findOwned(user, itemId);
        ensureActive(item.getProduct());
        validateQuantity(item.getProduct(), quantity);
        item.setQuantity(quantity);
        cartItems.flush();
        return get(user);
    }

    @Transactional
    public CartResponse remove(User user, Long itemId) {
        cartItems.delete(findOwned(user, itemId));
        cartItems.flush();
        return get(user);
    }

    @Transactional
    public CartResponse clear(User user) {
        cartItems.deleteAllByUser(user.getId());
        return get(user);
    }

    private CartItem findOwned(User user, Long itemId) {
        // Buscar por id + dueño: un usuario no puede tocar artículos del carrito de otro.
        return cartItems.findByIdAndUserId(itemId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("El artículo no está en tu carrito"));
    }

    private void ensureActive(Product product) {
        if (!product.isActive()) {
            throw new BusinessRuleException("PRODUCT_UNAVAILABLE", product.getName() + " ya no está disponible");
        }
    }

    void validateQuantity(Product product, int quantity) {
        if (quantity < 1) {
            throw new BusinessRuleException("INVALID_QUANTITY", "La cantidad mínima es 1");
        }
        if (quantity > maxPerItem) {
            throw new BusinessRuleException("QUANTITY_LIMIT",
                    "Puedes comprar como máximo " + maxPerItem + " unidades de cada producto");
        }
        if (quantity > product.getStock()) {
            throw new BusinessRuleException("INSUFFICIENT_STOCK", product.getStock() == 0
                    ? product.getName() + " está agotado"
                    : "Solo hay " + product.getStock() + " unidades disponibles de " + product.getName());
        }
    }

    private CartResponse build(List<CartItem> items) {
        List<CartItemResponse> lines = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        int count = 0;
        boolean allValid = !items.isEmpty();
        for (CartItem item : items) {
            Product p = item.getProduct();
            BigDecimal lineTotal = p.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            String issue = null;
            if (!p.isActive()) issue = "Este producto ya no está disponible";
            else if (p.getStock() == 0) issue = "Agotado";
            else if (item.getQuantity() > p.getStock()) issue = "Solo quedan " + p.getStock() + " unidades";
            else if (item.getQuantity() > maxPerItem) issue = "Máximo " + maxPerItem + " unidades";
            if (issue != null) allValid = false;
            lines.add(new CartItemResponse(item.getId(), p.getId(), p.getSku(), p.getName(), p.getImageUrl(),
                    p.getCategory().getSlug(), p.getPrice(), item.getQuantity(), lineTotal, p.getStock(),
                    p.isActive(), issue));
            subtotal = subtotal.add(lineTotal);
            count += item.getQuantity();
        }
        BigDecimal shipping = pricing.shippingFor(subtotal);
        return new CartResponse(lines, count, subtotal, shipping, subtotal.add(shipping), pricing.freeShippingFrom(),
                pricing.remainingForFreeShipping(subtotal), allValid);
    }
}
