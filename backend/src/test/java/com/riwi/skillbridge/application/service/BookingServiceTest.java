package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationPublisherPort;
import com.riwi.skillbridge.application.port.out.NotificationType;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    @Test
    void shouldPersistAndPublishBookingCreated() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        NotificationPublisherPort publisher = mock(NotificationPublisherPort.class);

        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Offering offering = new Offering(offeringId, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true);
        when(offerings.findById(offeringId)).thenReturn(Optional.of(offering));
        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingService service = new BookingService(bookings, offerings, users, publisher);
        Booking result = service.create(offeringId, Instant.now().plusSeconds(3600), "user@example.com");

        assertEquals(offeringId, result.offeringId());
        assertEquals(userId, result.customerId());
        verify(bookings).save(any(Booking.class));

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(publisher).publish(captor.capture());

        NotificationMessage message = captor.getValue();
        assertEquals(result.id(), message.bookingId());
        assertEquals(userId, message.userId());
        assertEquals(NotificationType.BOOKING_CREATED, message.notificationType());
        assertNotNull(message.eventId());
        assertNotNull(message.occurredAt());
    }

    @Test
    void shouldNotPublishWhenScheduledDateIsInThePast() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        NotificationPublisherPort publisher = mock(NotificationPublisherPort.class);
        BookingService service = new BookingService(bookings,
            mock(OfferingRepositoryPort.class), mock(UserAccountPort.class), publisher);

        assertThrows(BusinessRuleException.class, () ->
            service.create(UUID.randomUUID(), Instant.now().minusSeconds(60), "user@example.com"));

        verify(bookings, never()).save(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void shouldNotPublishWhenOfferingIsInactive() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        NotificationPublisherPort publisher = mock(NotificationPublisherPort.class);

        UUID offeringId = UUID.randomUUID();
        when(offerings.findById(offeringId)).thenReturn(Optional.of(
            new Offering(offeringId, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, false)));

        BookingService service = new BookingService(bookings, offerings,
            mock(UserAccountPort.class), publisher);

        assertThrows(BusinessRuleException.class, () ->
            service.create(offeringId, Instant.now().plusSeconds(3600), "user@example.com"));

        verify(bookings, never()).save(any());
        verify(publisher, never()).publish(any());
    }

    @Test
    void shouldNotPublishWhenUserDoesNotExist() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        NotificationPublisherPort publisher = mock(NotificationPublisherPort.class);

        UUID offeringId = UUID.randomUUID();
        when(offerings.findById(offeringId)).thenReturn(Optional.of(
            new Offering(offeringId, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true)));
        when(users.findIdByEmail("ghost@example.com")).thenReturn(Optional.empty());

        BookingService service = new BookingService(bookings, offerings, users, publisher);

        assertThrows(DomainNotFoundException.class, () ->
            service.create(offeringId, Instant.now().plusSeconds(3600), "ghost@example.com"));

        verify(bookings, never()).save(any());
        verify(publisher, never()).publish(any());
    }
}
