package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CancelBookingCommand;
import com.riwi.skillbridge.application.port.in.CancelBookingUseCase;
import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.ListCustomerBookingsUseCase;
import com.riwi.skillbridge.application.port.in.PaymentResultUseCase;
import com.riwi.skillbridge.application.port.out.*;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.UserAccount;
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
public class BookingService implements CreateBookingUseCase, ListCustomerBookingsUseCase, CancelBookingUseCase, PaymentResultUseCase {
    private final BookingRepositoryPort bookingRepository;
    private final OfferingRepositoryPort offeringRepository;
    private final UserRepositoryPort userRepositoryPort;

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
            throw new BusinessRuleException("El servicio no estǭ activo");
        }

        UserAccount customer = userRepositoryPort.findByEmail(customerEmail)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        Booking booking = new Booking(UUID.randomUUID(), offeringId, customer.id(), scheduledAt, BookingStatus.CREATED);
        Booking saved = bookingRepository.save(booking);
        notificationPublisher.publish(NotificationMessage.bookingCreated(saved.id(), saved.customerId()));
        
        String correlationId = CorrelationIdHolder.get() != null ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        BookingCreatedPayload payload = new BookingCreatedPayload(saved.id(), saved.offeringId(), saved.customerId(), saved.scheduledAt(), saved.status().name());
        BusinessEvent<BookingCreatedPayload> event = new BusinessEvent<>(
            UUID.randomUUID(), "BookingCreated", saved.id().toString(), "Booking", Instant.now(), correlationId, 1, payload,
            customer.id().toString(), customer.email(), customer.role().name(), "CREATE", "BOOKING", saved.id().toString()
        );
        eventPublisher.publish(event);
        return saved;
    }

    @Override
    public List<Booking> bookingsList(String email) {
        return bookingRepository.findByCustomerEmail(email);
    }

    @Override
    @Transactional
    public Booking cancel(CancelBookingCommand command) {
        Booking booking = bookingRepository.findById(command.bookingId())
                .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));

        UserAccount customer = userRepositoryPort.findByEmail(command.customerEmail())
                .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));
        if (!booking.customerId().equals(customer.id())) {
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
            UUID.randomUUID(), "BookingCancelled", finalBooking.id().toString(), "Booking", Instant.now(), correlationId, 1, payload,
            customer.id().toString(), customer.email(), customer.role().name(), "CANCEL", "BOOKING", finalBooking.id().toString()
        );
        eventPublisher.publish(event);
        
        return finalBooking;
    }


    @Override
    public void processPaymentApproved(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));

        if (booking.status() != BookingStatus.CREATED) {
            throw new BusinessRuleException("La reserva no esta en estado CREATED");
        }

        Booking confirmedBooking = new Booking(
            booking.id(),
            booking.offeringId(),
            booking.customerId(),
            booking.scheduledAt(),
            BookingStatus.CONFIRMED
        );

        bookingRepository.save(confirmedBooking);
    }

    @Override
    public void processPaymentRejected(UUID bookingId, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new DomainNotFoundException("Reserva no encontrada"));

        // No alteramos el estado de la reserva, la mantenemos en CREATED
        // para permitir que el usuario pueda intentar realizar el pago nuevamente.
    }
}

