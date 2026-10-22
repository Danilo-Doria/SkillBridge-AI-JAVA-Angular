package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AuthorizePaymentUseCase;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.PaymentResultRepositoryPort;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.exception.ForbiddenOperationException;
import com.riwi.skillbridge.domain.model.Actor;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PaymentAuthorizationService implements AuthorizePaymentUseCase {

    private final BookingRepositoryPort bookingRepositoryPort;
    private final PaymentPort paymentPort;
    private final PaymentResultRepositoryPort repositoryPort;
    private final PaymentEventPublisherPort eventPublisherPort;

    public PaymentAuthorizationService(
            BookingRepositoryPort bookingRepositoryPort,
            PaymentPort paymentPort,
            PaymentResultRepositoryPort repositoryPort,
            PaymentEventPublisherPort eventPublisherPort) {
        this.bookingRepositoryPort = bookingRepositoryPort;
        this.paymentPort = paymentPort;
        this.repositoryPort = repositoryPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Override
    public PaymentResult authorize(PaymentCommand command, Actor actor) {
        if (command.bookingId() == null) {
            throw new IllegalArgumentException("bookingId es obligatorio");
        }
        // La propiedad de la reserva se comprueba antes de consultar la clave de idempotencia o la pasarela
        Booking booking = bookingRepositoryPort.findById(command.bookingId())
                .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));
        if (!booking.customerId().equals(actor.id())) {
            throw new ForbiddenOperationException("No puede pagar una reserva que no le pertenece");
        }

        Optional<PaymentResult> existing = repositoryPort.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            // La clave es global: un resultado guardado para otra reserva no se le revela a este actor
            if (!command.bookingId().equals(existing.get().bookingId())) {
                throw new ForbiddenOperationException("La clave de idempotencia pertenece a otra reserva");
            }
            return existing.get();
        }

        PaymentResult result = paymentPort.process(command);
        repositoryPort.save(command.idempotencyKey(), result);

        if (result.status() == PaymentStatus.APPROVED) {
            eventPublisherPort.publishApproved(new PaymentApproved(result.bookingId()));
        } else if (result.status() == PaymentStatus.DECLINED) {
            eventPublisherPort.publishRejected(new PaymentRejected(result.bookingId(), result.message()));
        }
        // Nota: Si el estado es PENDING (como en Stripe PaymentIntent en espera de confirmación del cliente),
        // no emitimos eventos aún; el Webhook asíncrono emitirá la aprobación o rechazo definitivo.

        return result;
    }
}
