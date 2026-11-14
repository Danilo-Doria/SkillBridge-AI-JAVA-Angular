package com.riwi.skillbridge.payment.application.service;

import com.riwi.skillbridge.payment.application.port.in.HandleStripeWebhookUseCase;
import com.riwi.skillbridge.payment.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.payment.application.port.out.PaymentResultRepositoryPort;
import com.riwi.skillbridge.payment.application.port.out.ProcessedWebhookEventPort;
import com.riwi.skillbridge.payment.application.port.out.WebhookVerifierPort;
import com.riwi.skillbridge.payment.application.port.out.WebhookVerifierPort.VerifiedWebhookEvent;
import com.riwi.skillbridge.payment.domain.event.PaymentRejected;
import com.riwi.skillbridge.payment.domain.exception.InvalidWebhookException;
import com.riwi.skillbridge.payment.domain.exception.PaymentGatewayUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Webhook de Stripe. Orden estricto:
 * <ol>
 *   <li>Firma verificada sobre el cuerpo crudo (si falla: excepción, ningún efecto).</li>
 *   <li>Registro del id de evento de Stripe y publicación en la MISMA transacción: si Kafka no confirma, se revierte
 *       el registro y Stripe reintentará; si el evento ya estaba registrado, no se publica nada (idempotencia).</li>
 * </ol>
 * El {@code eventId} del envelope se deriva del id de Stripe, así un reintento tras un commit fallido produce el mismo
 * eventId y los consumidores (idempotentes por eventId) lo descartan.
 */
@Service
public class StripeWebhookService implements HandleStripeWebhookUseCase {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookService.class);
    private static final String PAYMENT_INTENT = "payment_intent";

    private final WebhookVerifierPort verifier;
    private final ProcessedWebhookEventPort processedEvents;
    private final PaymentResultRepositoryPort results;
    private final PaymentEventPublisherPort publisher;
    private final PaymentApprovedAssembler assembler;

    public StripeWebhookService(WebhookVerifierPort verifier, ProcessedWebhookEventPort processedEvents,
                                PaymentResultRepositoryPort results, PaymentEventPublisherPort publisher,
                                PaymentApprovedAssembler assembler) {
        this.verifier = verifier;
        this.processedEvents = processedEvents;
        this.results = results;
        this.publisher = publisher;
        this.assembler = assembler;
    }

    @Override
    @Transactional
    public Outcome handle(String rawPayload, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            throw new InvalidWebhookException("Missing Stripe-Signature header");
        }
        if (!verifier.isConfigured()) {
            throw new PaymentGatewayUnavailableException("El webhook de Stripe no está configurado.");
        }
        VerifiedWebhookEvent event = verifier.verify(rawPayload, signatureHeader);
        log.info("Verified Stripe webhook event received: type={}, id={}", event.type(), event.eventId());

        boolean handledType = ProcessedWebhookEventPort.PAYMENT_SUCCEEDED.equals(event.type())
            || ProcessedWebhookEventPort.PAYMENT_FAILED.equals(event.type());
        if (!handledType || !PAYMENT_INTENT.equals(event.objectType())) {
            log.debug("Stripe event {} ({}) does not change payment state. Ignored.", event.eventId(), event.type());
            return Outcome.IGNORED;
        }
        UUID bookingId = parseBookingId(event);
        if (bookingId == null) {
            return Outcome.IGNORED;
        }

        if (!processedEvents.registerIfNew(event.eventId(), event.type(), bookingId, event.paymentIntentId())) {
            log.info("Duplicate Stripe event {} for bookingId={}: already processed, no effects", event.eventId(), bookingId);
            return Outcome.DUPLICATE;
        }

        UUID eventId = PaymentAuthorizationService.stableEventId("stripe:" + event.eventId());
        if (ProcessedWebhookEventPort.PAYMENT_SUCCEEDED.equals(event.type())) {
            UUID paymentId = event.paymentIntentId() == null ? null
                : results.findPaymentIdByIntent(event.paymentIntentId()).orElse(null);
            log.info("Payment succeeded for bookingId={} (Stripe PaymentIntent={}). Emitting PaymentApproved.",
                bookingId, event.paymentIntentId());
            publisher.publishApproved(assembler.assemble(bookingId, paymentId, eventId));
        } else {
            String reason = event.failureMessage() != null && !event.failureMessage().isBlank()
                ? event.failureMessage() : "Payment processing failed in Stripe";
            log.info("Payment failed for bookingId={} (Stripe PaymentIntent={}). Emitting PaymentRejected.",
                bookingId, event.paymentIntentId());
            publisher.publishRejected(new PaymentRejected(bookingId, reason), eventId);
        }
        return Outcome.PROCESSED;
    }

    private static UUID parseBookingId(VerifiedWebhookEvent event) {
        if (event.bookingId() == null || event.bookingId().isBlank()) {
            log.warn("Stripe PaymentIntent {} metadata does not contain 'bookingId'. Skipping.", event.paymentIntentId());
            return null;
        }
        try {
            return UUID.fromString(event.bookingId());
        } catch (IllegalArgumentException e) {
            log.warn("Stripe PaymentIntent {} has an invalid 'bookingId' in metadata. Skipping.", event.paymentIntentId());
            return null;
        }
    }
}
