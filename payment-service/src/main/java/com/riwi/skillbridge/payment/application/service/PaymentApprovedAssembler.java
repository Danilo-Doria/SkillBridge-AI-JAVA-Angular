package com.riwi.skillbridge.payment.application.service;

import com.riwi.skillbridge.payment.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.payment.application.port.out.BookingViewRepositoryPort;
import com.riwi.skillbridge.payment.application.port.out.OfferingViewRepositoryPort;
import com.riwi.skillbridge.payment.domain.event.PaymentApproved;
import com.riwi.skillbridge.payment.domain.model.BookingView;
import com.riwi.skillbridge.payment.domain.model.OfferingView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Construye {@link PaymentApproved} v2 SOLO con los read models locales (booking_view, offering_view).
 * Si falta algún dato (p. ej. {@code customerEmail}, que hoy solo llega como {@code actorUsername} de
 * {@code BookingCreated}), NO se inventa: se emite la forma mínima v1, igual que el monolito.
 */
@Component
public class PaymentApprovedAssembler {
    private static final Logger log = LoggerFactory.getLogger(PaymentApprovedAssembler.class);

    private final BookingViewRepositoryPort bookings;
    private final OfferingViewRepositoryPort offerings;
    private final Clock clock;

    public PaymentApprovedAssembler(BookingViewRepositoryPort bookings, OfferingViewRepositoryPort offerings,
                                    Clock clock) {
        this.bookings = bookings;
        this.offerings = offerings;
        this.clock = clock;
    }

    /**
     * @param paymentId puede ser {@code null}
     * @param eventId   id estable del evento (deduplicación en consumidores ante reintentos)
     */
    public PaymentApproved assemble(UUID bookingId, UUID paymentId, UUID eventId) {
        String correlationId = CorrelationIdHolder.currentOrNew();
        Optional<BookingView> booking = bookings.findById(bookingId);
        if (booking.isEmpty()) {
            return degraded(eventId, correlationId, bookingId, "reserva no encontrada en booking_view");
        }
        BookingView b = booking.get();
        Optional<OfferingView> offering = b.offeringId() == null ? Optional.empty() : offerings.findById(b.offeringId());
        if (offering.isEmpty()) {
            return degraded(eventId, correlationId, bookingId, "oferta " + b.offeringId() + " no encontrada en offering_view");
        }
        OfferingView o = offering.get();
        PaymentApproved full = new PaymentApproved(PaymentApproved.CURRENT_VERSION, eventId, clock.instant(),
            correlationId, bookingId, paymentId, b.customerId(), b.customerEmail(), o.id(), o.title(), o.providerId(),
            b.scheduledAt(), o.durationHours(), o.mode());
        if (!full.hasFullMentorshipState()) {
            return degraded(eventId, correlationId, bookingId, "estado incompleto (customerEmail/scheduledAt/mode/durationHours)");
        }
        return full;
    }

    private PaymentApproved degraded(UUID eventId, String correlationId, UUID bookingId, String reason) {
        log.warn("PaymentApproved para bookingId={} se emite en forma mínima (v1): {}", bookingId, reason);
        return PaymentApproved.minimal(eventId, clock.instant(), correlationId, bookingId);
    }
}
