package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CreateBookingUseCase;
import com.riwi.skillbridge.application.port.in.ListCustomerBookingsUseCase;
import com.riwi.skillbridge.application.port.in.PaymentResultUseCase;
import com.riwi.skillbridge.application.port.out.*;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService implements CreateBookingUseCase, ListCustomerBookingsUseCase, PaymentResultUseCase {
    private final BookingRepositoryPort bookingRepository;
    private final OfferingRepositoryPort offeringRepository;
    private final UserAccountPort userAccountPort;
    private final BookingEventPublisherPort eventPublisher;

    public BookingService(
        BookingRepositoryPort bookingRepository,
        OfferingRepositoryPort offeringRepository,
        UserAccountPort userAccountPort,
        BookingEventPublisherPort eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.offeringRepository = offeringRepository;
        this.userAccountPort = userAccountPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Booking create(UUID offeringId, Instant scheduledAt, String customerEmail) {
        if (scheduledAt.isBefore(Instant.now())) {
            throw new BusinessRuleException("La reserva debe programarse en una fecha futura");
        }

        Offering offering = offeringRepository.findById(offeringId)
            .orElseThrow(() -> new DomainNotFoundException("Servicio no encontrado"));
        if (!offering.active()) {
            throw new BusinessRuleException("El servicio no esta activo");
        }

        UUID customerId = userAccountPort.findIdByEmail(customerEmail)
            .orElseThrow(() -> new DomainNotFoundException("Usuario no encontrado"));

        Booking booking = new Booking(UUID.randomUUID(), offeringId, customerId, scheduledAt, BookingStatus.CREATED);
        Booking saved = bookingRepository.save(booking);
        eventPublisher.bookingCreated(saved);
        return saved;
    }

    @Override
    public List<Booking> bookingsList(String email) {
        return bookingRepository.findByCustomerEmail(email);
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
            
        // Si el pago es rechazado, podriamos cancelar la reserva o simplemente dejarla pendiente/fallida.
        // Por ahora, aplicamos la regla: "evita confirmar el flujo". La pasamos a CANCELLED o mantenemos en CREATED.
        // Lo estandar en este flujo sería CANCELLED o dejarla esperando otro intento.
        
        Booking cancelledBooking = new Booking(
            booking.id(), 
            booking.offeringId(), 
            booking.customerId(), 
            booking.scheduledAt(), 
            BookingStatus.CANCELLED
        );
        
        bookingRepository.save(cancelledBooking);
    }
}

