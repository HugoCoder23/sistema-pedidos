package com.portafolio.pedidos.service;

import com.portafolio.pedidos.dto.Requests.LoginRequest;
import com.portafolio.pedidos.dto.Requests.RegisterRequest;
import com.portafolio.pedidos.dto.Responses.LoginResponse;
import com.portafolio.pedidos.dto.Responses.UserResponse;
import com.portafolio.pedidos.entity.Role;
import com.portafolio.pedidos.entity.User;
import com.portafolio.pedidos.exception.ConflictException;
import com.portafolio.pedidos.mapper.OrderMapper;
import com.portafolio.pedidos.repository.UserRepository;
import com.portafolio.pedidos.security.CurrentUserProvider;
import com.portafolio.pedidos.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OrderMapper mapper;
    private final CurrentUserProvider currentUser;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
                       OrderMapper mapper, CurrentUserProvider currentUser) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mapper = mapper;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(normalize(request.email()))
                .filter(User::isActive)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPasswordHash()))
                // Mismo mensaje para email inexistente, inactivo o contraseña incorrecta.
                .orElseThrow(() -> new BadCredentialsException("Credenciales inválidas"));
        return issue(user);
    }

    /** El registro público siempre crea clientes; el personal se da de alta por otra vía. */
    @Transactional
    public LoginResponse register(RegisterRequest request) {
        String email = normalize(request.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "Ya existe una cuenta con ese email");
        }
        User user = users.saveAndFlush(new User(request.name().trim(), email,
                passwordEncoder.encode(request.password()), Role.CUSTOMER));
        return issue(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me() {
        return mapper.toResponse(currentUser.get());
    }

    private LoginResponse issue(User user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResponse(token.value(), "Bearer", token.expiresAt(), mapper.toResponse(user));
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
