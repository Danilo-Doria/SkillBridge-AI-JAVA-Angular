package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CancelBookingCommand;
import com.riwi.skillbridge.application.port.in.CancelBookingUseCase;
import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.ListCustomerBookingsUseCase;
import com.riwi.skillbridge.application.port.out.*;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.service.BookingCancellationPolicy;
import org.springframework.stereotype.Service;

import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.port.out.event.BookingCreatedPayload;
import com.riwi.skillbridge.application.port.out.event.BookingCancelledPayload;
import com.riwi.skillbridge.application.common.CorrelationIdHolder;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService implements CreateBookingUseCase, ListCustomerBookingsUseCase, CancelBookingUseCase {
    private final BookingRepositoryPort bookingRepository;
    private final OfferingRepositoryPort offeringRepository;
    private final UserAccountPort userAccountPort;

    private final BookingCancellationPolicy cancellationPolicy;
    private final NotificationPublisherPort notificationPublisher;
    private final BookingEventPublisherPort eventPublisher;

    @Override
    public Booking create(UUID offeringId, Instant scheduledAt, String customerEmail) {
        if (scheduledAt.isBefore(Instant.now())) {
            throw new BusinessRuleException("La reserva debe programarse en una fecha futura");
        }

        Offering offering = offeringRepository.findById(offeringId)
            .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado"));
        if (!offering.active()) {
            throw new BusinessRuleException("El servicio no está activo");
        }

        UUID customerId = userAccountPort.findIdByEmail(customerEmail)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        Booking booking = new Booking(UUID.randomUUID(), offeringId, customerId, scheduledAt, BookingStatus.CREATED);
        Booking saved = bookingRepository.save(booking);
        notificationPublisher.publish(NotificationMessage.bookingCreated(saved.id(), saved.customerId()));
        
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        BookingCreatedPayload payload = new BookingCreatedPayload(saved.id(), saved.offeringId(), saved.customerId(), saved.scheduledAt(), saved.status().name());
        BusinessEvent<BookingCreatedPayload> event = new BusinessEvent<>(
            UUID.randomUUID(), "BookingCreated", saved.id().toString(), "Booking", Instant.now(), correlationId, 1, payload);
        eventPublisher.publish(event);
        return saved;
    }

    // nuevo servicio buscar reservacion por email usuario
    @Override
    public List<Booking> bookingsList(String email) {
        return bookingRepository.findByCustomerEmail(email);
    }

    @Override
    @Transactional
    public Booking cancel(CancelBookingCommand command) {
        Booking booking = bookingRepository.findById(command.bookingId())
                .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));

        UUID customerId = userAccountPort.findIdByEmail(command.customerEmail())
                .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));
        if (!booking.customerId().equals(customerId)) {
            throw new DomainNotFoundException("Reserva no encontrada");
        }

        Booking cancelled = booking.cancel();
        Booking finalBooking;
        if (cancelled == booking) {
            finalBooking = booking;
        } else {
            cancellationPolicy.validate(booking);
            finalBooking = bookingRepository.save(cancelled);
        }
        
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        BookingCancelledPayload payload = new BookingCancelledPayload(finalBooking.id(), finalBooking.customerId(), finalBooking.status().name());
        BusinessEvent<BookingCancelledPayload> event = new BusinessEvent<>(
            UUID.randomUUID(), "BookingCancelled", finalBooking.id().toString(), "Booking", Instant.now(), correlationId, 1, payload);
        eventPublisher.publish(event);
        
        return finalBooking;
    }
}
