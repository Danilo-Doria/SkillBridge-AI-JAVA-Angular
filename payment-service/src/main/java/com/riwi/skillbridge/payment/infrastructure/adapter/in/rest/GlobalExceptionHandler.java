package com.riwi.skillbridge.payment.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.payment.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.payment.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.payment.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.payment.domain.exception.IdempotencyKeyConflictException;
import com.riwi.skillbridge.payment.domain.exception.MessagePublishException;
import com.riwi.skillbridge.payment.domain.exception.PaymentGatewayUnavailableException;
import com.riwi.skillbridge.payment.domain.exception.ReadModelNotSynchronizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Mismos títulos/códigos que {@code GlobalExceptionHandler} del monolito, más los propios del servicio. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    static final String RETRY_AFTER_SECONDS = "5";

    @ExceptionHandler(DomainNotFoundException.class)
    ProblemDetail notFound(DomainNotFoundException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        p.setTitle("Resource not found");
        return p;
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail business(BusinessRuleException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        p.setTitle("Business rule violation");
        return p;
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyKeyConflictException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** 409 + Retry-After: la reserva/oferta aún no llegó al read model. El pago no se acepta. */
    @ExceptionHandler(ReadModelNotSynchronizedException.class)
    ResponseEntity<ProblemDetail> notSynchronized(ReadModelNotSynchronizedException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        p.setTitle("Booking not yet available");
        p.setProperty("code", ex.getCode());
        addCorrelation(p);
        return ResponseEntity.status(HttpStatus.CONFLICT).header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS).body(p);
    }

    @ExceptionHandler(PaymentGatewayUnavailableException.class)
    ProblemDetail gatewayUnavailable(PaymentGatewayUnavailableException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        p.setTitle("Payment gateway unavailable");
        p.setProperty("code", "PAYMENT_GATEWAY_UNAVAILABLE");
        addCorrelation(p);
        return p;
    }

    @ExceptionHandler(MessagePublishException.class)
    ProblemDetail publishFailed(MessagePublishException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
            "No se pudo registrar el resultado del pago. Inténtalo de nuevo.");
        p.setTitle("Messaging unavailable");
        p.setProperty("code", "MESSAGING_UNAVAILABLE");
        addCorrelation(p);
        return p;
    }

    @ExceptionHandler({IllegalArgumentException.class})
    ProblemDetail badRequest(IllegalArgumentException ex) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        p.setTitle("Invalid request");
        return p;
    }

    @ExceptionHandler({MissingRequestHeaderException.class, HttpMessageNotReadableException.class})
    ProblemDetail malformed(Exception ex) {
        log.debug("Malformed request: {}", ex.getClass().getSimpleName());
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            ex instanceof MissingRequestHeaderException mh ? "Falta la cabecera " + mh.getHeaderName()
                : "Cuerpo de la solicitud inválido");
        p.setTitle("Invalid request");
        return p;
    }

    private static void addCorrelation(ProblemDetail p) {
        String correlationId = CorrelationIdHolder.get();
        if (correlationId != null) {
            p.setProperty("correlationId", correlationId);
        }
    }
}
