package com.portafolio.pedidos.controller;

import com.portafolio.pedidos.dto.Requests.LoginRequest;
import com.portafolio.pedidos.dto.Requests.RegisterRequest;
import com.portafolio.pedidos.dto.Responses.LoginResponse;
import com.portafolio.pedidos.dto.Responses.UserResponse;
import com.portafolio.pedidos.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión y obtener un JWT")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    @Operation(summary = "Crear una cuenta de cliente (devuelve un JWT)")
    public LoginResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Datos del usuario autenticado")
    public UserResponse me() {
        return authService.me();
    }
}
