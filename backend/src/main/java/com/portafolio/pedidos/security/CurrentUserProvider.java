package com.portafolio.pedidos.security;

import com.portafolio.pedidos.entity.User;
import com.portafolio.pedidos.repository.UserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/** Resuelve el usuario autenticado a partir del JWT de la petición actual. */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            throw new InsufficientAuthenticationException("No hay un usuario autenticado");
        }
        Number uid = jwt.getClaim(JwtService.CLAIM_USER_ID);
        User user = userRepository.findById(uid.longValue())
                .orElseThrow(() -> new InsufficientAuthenticationException("El usuario ya no existe"));
        if (!user.isActive()) {
            throw new DisabledException("El usuario está desactivado");
        }
        return user;
    }
}
