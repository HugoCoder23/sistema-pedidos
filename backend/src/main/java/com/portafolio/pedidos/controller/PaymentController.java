package com.portafolio.pedidos.controller;

import com.portafolio.pedidos.dto.Requests.SimulatePaymentRequest;
import com.portafolio.pedidos.dto.Responses.PaymentResultResponse;
import com.portafolio.pedidos.security.CurrentUserProvider;
import com.portafolio.pedidos.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Pagos (simulados)")
public class PaymentController {

    private final PaymentService payments;
    private final CurrentUserProvider currentUser;

    public PaymentController(PaymentService payments, CurrentUserProvider currentUser) {
        this.payments = payments;
        this.currentUser = currentUser;
    }

    @PostMapping("/simulate")
    @Operation(summary = "Simular el pago de un pedido PENDING",
            description = "El importe sale del pedido, nunca de la petición. outcome=DECLINED simula un rechazo.")
    public PaymentResultResponse simulate(@Valid @RequestBody SimulatePaymentRequest request) {
        return payments.simulate(currentUser.get(), request);
    }
}
