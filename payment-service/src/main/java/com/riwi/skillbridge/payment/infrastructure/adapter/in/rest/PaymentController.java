package com.riwi.skillbridge.payment.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.payment.application.port.in.AuthorizePaymentUseCase;
import com.riwi.skillbridge.payment.domain.model.PaymentCommand;
import com.riwi.skillbridge.payment.domain.model.PaymentResult;
import com.riwi.skillbridge.payment.infrastructure.adapter.in.rest.dto.PaymentRequestDto;
import com.riwi.skillbridge.payment.infrastructure.adapter.in.rest.dto.PaymentResponseDto;
import com.riwi.skillbridge.payment.infrastructure.security.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mismo contrato que {@code PaymentController} del monolito; el actor sale del JWT validado. */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final AuthorizePaymentUseCase authorizePaymentUseCase;

    public PaymentController(AuthorizePaymentUseCase authorizePaymentUseCase) {
        this.authorizePaymentUseCase = authorizePaymentUseCase;
    }

    @PostMapping("/authorize")
    public ResponseEntity<PaymentResponseDto> authorizePayment(
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequestDto request) {
        PaymentResult result = authorizePaymentUseCase.authorize(new PaymentCommand(
            idempotencyKey, request.bookingId(), request.amount(), user == null ? null : user.id()));
        return ResponseEntity.ok(new PaymentResponseDto(result.bookingId(), result.status().name(), result.message(),
            result.clientSecret()));
    }
}
