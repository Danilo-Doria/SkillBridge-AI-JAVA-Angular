package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AuthorizePaymentUseCase;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.payment.PaymentRequestDto;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.payment.PaymentResponseDto;
import com.riwi.skillbridge.infrastructure.security.CurrentActorResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final AuthorizePaymentUseCase authorizePaymentUseCase;
    private final CurrentActorResolver actorResolver;

    public PaymentController(AuthorizePaymentUseCase authorizePaymentUseCase, CurrentActorResolver actorResolver) {
        this.authorizePaymentUseCase = authorizePaymentUseCase;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/authorize")
    public ResponseEntity<PaymentResponseDto> authorizePayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequestDto request,
            Authentication authentication) {

        PaymentCommand command = new PaymentCommand(
                idempotencyKey,
                request.bookingId(),
                request.amount(),
                request.cardNumber(),
                request.expiryDate(),
                request.cvv()
        );

        Actor actor = actorResolver.from(authentication);
        PaymentResult result = authorizePaymentUseCase.authorize(command, actor);

        PaymentResponseDto response = new PaymentResponseDto(
                result.bookingId(),
                result.status().name(),
                result.message(),
                result.clientSecret()
        );

        return ResponseEntity.ok(response);
    }
}
