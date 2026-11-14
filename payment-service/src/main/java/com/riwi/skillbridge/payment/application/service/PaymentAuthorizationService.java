package com.riwi.skillbridge.payment.application.service;

import com.riwi.skillbridge.payment.application.port.in.AuthorizePaymentUseCase;
import com.riwi.skillbridge.payment.application.port.out.BookingViewRepositoryPort;
import com.riwi.skillbridge.payment.application.port.out.OfferingViewRepositoryPort;
import com.riwi.skillbridge.payment.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.payment.application.port.out.PaymentGatewayPort;
import com.riwi.skillbridge.payment.application.port.out.PaymentGatewayPort.GatewayResult;
import com.riwi.skillbridge.payment.application.port.out.PaymentResultRepositoryPort;
import com.riwi.skillbridge.payment.application.port.out.ProcessedWebhookEventPort;
import com.riwi.skillbridge.payment.domain.event.PaymentRejected;
import com.riwi.skillbridge.payment.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.payment.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.payment.domain.exception.IdempotencyKeyConflictException;
import com.riwi.skillbridge.payment.domain.exception.PaymentGatewayUnavailableException;
import com.riwi.skillbridge.payment.domain.exception.ReadModelNotSynchronizedException;
import com.riwi.skillbridge.payment.domain.model.BookingView;
import com.riwi.skillbridge.payment.domain.model.OfferingView;
import com.riwi.skillbridge.payment.domain.model.PaymentCommand;
import com.riwi.skillbridge.payment.domain.model.PaymentResult;
import com.riwi.skillbridge.payment.domain.model.PaymentStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Misma lógica que {@code PaymentAuthorizationService} del monolito, con estas diferencias deliberadas:
 * <ul>
 *   <li>Propiedad: el actor (claim {@code uid}) debe ser el {@code customerId} de la reserva; si no, 404
 *       "Reserva no encontrada" (criterio del monolito en {@code BookingService.cancel}).</li>
 *   <li>Reserva/oferta desconocida en el read model: 409 {@code *_NOT_SYNCHRONIZED}; nunca se acepta el pago.</li>
 *   <li>Idempotency-Key acotada al cliente (el monolito la compartía entre usuarios).</li>
 *   <li>Importe validado contra el precio de la oferta (422 si no coincide).</li>
 *   <li>Plazo vencido: DECLINED + PaymentRejected, pero no cancela la reserva (es del monolito: cambio aditivo).</li>
 *   <li>Stripe sin configurar o caído: 503, sin cachear, sin evento.</li>
 *   <li>Reserva CONFIRMED/COMPLETED en el read model ({@code BookingStatusChanged}): DECLINED "ya pagada".</li>
 * </ul>
 */
@Service
public class PaymentAuthorizationService implements AuthorizePaymentUseCase {

    static final String CANCELLED_MESSAGE = "La reserva ya se encuentra cancelada.";
    static final String ALREADY_PAID_MESSAGE = "La reserva ya está pagada.";

    private final PaymentGatewayPort gateway;
    private final PaymentResultRepositoryPort results;
    private final PaymentEventPublisherPort publisher;
    private final BookingViewRepositoryPort bookings;
    private final OfferingViewRepositoryPort offerings;
    private final ProcessedWebhookEventPort processedEvents;
    private final PaymentApprovedAssembler assembler;
    private final Clock clock;
    private final Duration paymentWindow;

    public PaymentAuthorizationService(PaymentGatewayPort gateway,
                                       PaymentResultRepositoryPort results,
                                       PaymentEventPublisherPort publisher,
                                       BookingViewRepositoryPort bookings,
                                       OfferingViewRepositoryPort offerings,
                                       ProcessedWebhookEventPort processedEvents,
                                       PaymentApprovedAssembler assembler,
                                       Clock clock,
                                       @Value("${app.payments.payment-window-hours:12}") long paymentWindowHours) {
        this.gateway = gateway;
        this.results = results;
        this.publisher = publisher;
        this.bookings = bookings;
        this.offerings = offerings;
        this.processedEvents = processedEvents;
        this.assembler = assembler;
        this.clock = clock;
        this.paymentWindow = Duration.ofHours(paymentWindowHours);
    }

    @Override
    @Transactional
    public PaymentResult authorize(PaymentCommand command) {
        validate(command);
        if (!gateway.isConfigured()) {
            throw new PaymentGatewayUnavailableException("La pasarela de pagos no está configurada.");
        }

        BookingView booking = bookings.findById(command.bookingId())
            .orElseThrow(() -> new ReadModelNotSynchronizedException(ReadModelNotSynchronizedException.BOOKING,
                "La reserva aún no está disponible para pagos. Inténtalo de nuevo en unos segundos."));
        if (!booking.isOwnedBy(command.customerId())) {
            throw new DomainNotFoundException("Reserva no encontrada");
        }

        Optional<PaymentResult> existing = results.findByIdempotencyKey(command.customerId(), command.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().bookingId().equals(command.bookingId())) {
                throw new IdempotencyKeyConflictException("Idempotency-Key ya fue usada con una solicitud diferente");
            }
            return existing.get();
        }

        if (booking.isCancelled()) {
            return saveDeclined(command, CANCELLED_MESSAGE);
        }
        if (booking.isPaid()) {
            return saveDeclined(command, ALREADY_PAID_MESSAGE);
        }
        if (!booking.isAwaitingPayment()) {
            return saveDeclined(command, "La reserva no admite pagos en estado " + booking.status() + ".");
        }
        if (processedEvents.hasSucceededPayment(booking.bookingId())) {
            return saveDeclined(command, ALREADY_PAID_MESSAGE);
        }
        if (booking.isPaymentWindowExpired(clock.instant(), paymentWindow)) {
            PaymentResult expired = saveDeclined(command, "El plazo para pagar esta reserva ha expirado (menos de "
                + paymentWindow.toHours() + " horas para la sesión).");
            publisher.publishRejected(new PaymentRejected(command.bookingId(), expired.message()),
                stableEventId("PaymentRejected:expired:" + command.bookingId()));
            return expired;
        }

        OfferingView offering = Optional.ofNullable(booking.offeringId()).flatMap(offerings::findById)
            .orElseThrow(() -> new ReadModelNotSynchronizedException(ReadModelNotSynchronizedException.OFFERING,
                "La oferta de la reserva aún no está disponible para pagos. Inténtalo de nuevo en unos segundos."));
        if (offering.price() != null && command.amount().compareTo(offering.price()) != 0) {
            throw new BusinessRuleException("El importe no coincide con el precio de la oferta.");
        }

        GatewayResult gatewayResult = gateway.createPaymentIntent(command);
        PaymentResult result = new PaymentResult(UUID.randomUUID(), command.bookingId(), gatewayResult.status(),
            gatewayResult.message(), gatewayResult.clientSecret(), gatewayResult.paymentIntentId());
        results.save(command.customerId(), command.idempotencyKey(), result);

        // eventId estable entre reintentos del cliente (cliente + Idempotency-Key): el paymentId es nuevo en cada
        // intento, así que si un envío llegó pero se perdió el ack, el reintento sale con el MISMO id (HU-104).
        String attempt = command.customerId() + ":" + command.idempotencyKey();
        if (result.status() == PaymentStatus.APPROVED) {
            publisher.publishApproved(assembler.assemble(result.bookingId(), result.paymentId(),
                stableEventId("PaymentApproved:authorize:" + attempt)));
        } else if (result.status() == PaymentStatus.DECLINED) {
            publisher.publishRejected(new PaymentRejected(result.bookingId(), result.message()),
                stableEventId("PaymentRejected:authorize:" + attempt));
        }
        // PENDING (lo normal con Stripe Elements): el webhook emitirá la aprobación o el rechazo definitivo.
        return result;
    }

    private PaymentResult saveDeclined(PaymentCommand command, String message) {
        PaymentResult declined = PaymentResult.declined(command.bookingId(), message);
        results.save(command.customerId(), command.idempotencyKey(), declined);
        return declined;
    }

    private static void validate(PaymentCommand command) {
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
            || command.idempotencyKey().length() > 255) {
            throw new IllegalArgumentException("Idempotency-Key es obligatoria (máx. 255 caracteres)");
        }
        if (command.bookingId() == null) {
            throw new IllegalArgumentException("bookingId es obligatorio");
        }
        if (command.amount() == null || command.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount debe ser positivo");
        }
        if (command.customerId() == null) {
            throw new IllegalArgumentException("Actor no identificado");
        }
    }

    static UUID stableEventId(String seed) {
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }
}
