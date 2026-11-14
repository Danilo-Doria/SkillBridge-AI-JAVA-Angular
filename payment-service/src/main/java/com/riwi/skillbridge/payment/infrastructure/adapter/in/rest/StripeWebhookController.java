package com.riwi.skillbridge.payment.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.payment.application.port.in.HandleStripeWebhookUseCase;
import com.riwi.skillbridge.payment.domain.exception.InvalidWebhookException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Mismo contrato que {@code StripeWebhookController} del monolito: cuerpo crudo ({@code String}, sin re-serializar)
 * y {@code Stripe-Signature}; 200 {@code {"received": true}}; 400 {@code {"error": ...}} si la firma falta o no es
 * válida (antes de cualquier efecto). Además: 503 ProblemDetail si el secreto no está configurado o Kafka no confirma
 * (Stripe reintenta).
 */
@RestController
@RequestMapping("/api/webhooks")
public class StripeWebhookController {

    private final HandleStripeWebhookUseCase webhookUseCase;

    public StripeWebhookController(HandleStripeWebhookUseCase webhookUseCase) {
        this.webhookUseCase = webhookUseCase;
    }

    @PostMapping("/stripe")
    public ResponseEntity<Map<String, Object>> handleStripeWebhook(
            @RequestBody(required = false) String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        webhookUseCase.handle(payload == null ? "" : payload, sigHeader);
        return ResponseEntity.ok(Map.of("received", true));
    }

    @ExceptionHandler(InvalidWebhookException.class)
    ResponseEntity<Map<String, Object>> invalid(InvalidWebhookException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
