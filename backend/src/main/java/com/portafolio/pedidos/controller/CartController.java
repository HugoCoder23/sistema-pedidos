package com.portafolio.pedidos.controller;

import com.portafolio.pedidos.dto.Requests.AddToCartRequest;
import com.portafolio.pedidos.dto.Requests.UpdateCartItemRequest;
import com.portafolio.pedidos.dto.Responses.CartResponse;
import com.portafolio.pedidos.security.CurrentUserProvider;
import com.portafolio.pedidos.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
@Tag(name = "Carrito (cliente)")
public class CartController {

    private final CartService cart;
    private final CurrentUserProvider currentUser;

    public CartController(CartService cart, CurrentUserProvider currentUser) {
        this.cart = cart;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Consultar el carrito con subtotal, envío y total calculados en el servidor")
    public CartResponse get() {
        return cart.get(currentUser.get());
    }

    @PostMapping
    @Operation(summary = "Agregar un producto (si ya existe, suma la cantidad)")
    public CartResponse add(@Valid @RequestBody AddToCartRequest request) {
        return cart.add(currentUser.get(), request);
    }

    @PatchMapping("/items/{id}")
    @Operation(summary = "Cambiar la cantidad de un artículo")
    public CartResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCartItemRequest request) {
        return cart.update(currentUser.get(), id, request.quantity());
    }

    @DeleteMapping("/items/{id}")
    @Operation(summary = "Quitar un artículo")
    public CartResponse remove(@PathVariable Long id) {
        return cart.remove(currentUser.get(), id);
    }

    @DeleteMapping
    @Operation(summary = "Vaciar el carrito")
    public CartResponse clear() {
        return cart.clear(currentUser.get());
    }
}
