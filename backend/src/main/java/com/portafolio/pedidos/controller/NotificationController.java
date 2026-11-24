package com.portafolio.pedidos.controller;

import com.portafolio.pedidos.dto.Responses.NotificationResponse;
import com.portafolio.pedidos.security.CurrentUserProvider;
import com.portafolio.pedidos.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notificaciones (email simulado)")
public class NotificationController {

    private final NotificationService notifications;
    private final CurrentUserProvider currentUser;

    public NotificationController(NotificationService notifications, CurrentUserProvider currentUser) {
        this.notifications = notifications;
        this.currentUser = currentUser;
    }

    @GetMapping("/my")
    @Operation(summary = "Últimos 20 correos simulados enviados al usuario")
    public List<NotificationResponse> my() {
        return notifications.forUser(currentUser.get());
    }
}
