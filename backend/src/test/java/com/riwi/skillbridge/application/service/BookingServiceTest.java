package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.CancelBookingCommand;
import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserAccountPort;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.exception.DomainNotFoundException;
import com.riwi.skillbridge.domain.service.BookingCancellationPolicy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BookingServiceTest {
    @Test
    void shouldPersistAndPublishBookingCreated() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);

        UUID offeringId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Offering offering = new Offering(offeringId, "Java", "Mentoría", "BACKEND", BigDecimal.TEN, true);
        when(offerings.findById(offeringId)).thenReturn(Optional.of(offering));
        when(users.findIdByEmail("user@example.com")).thenReturn(Optional.of(userId));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        BookingService service = new BookingService(bookings, offerings, users, publisher, cancellationPolicyAt(Instant.parse("2030-10-10T12:00:00Z")));
        Booking result = service.create(offeringId, Instant.now().plusSeconds(3600), "user@example.com");

        assertEquals(offeringId, result.offeringId());
        assertEquals(userId, result.customerId());
        verify(bookings).save(any(Booking.class));
        verify(publisher).bookingCreated(result);
    }

    @Test
    void shouldCancelOwnedCreatedBooking() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        Instant now = Instant.parse("2030-10-10T12:00:00Z");
        UUID customerId = UUID.randomUUID();
        Booking booking = booking(customerId, now.plus(Duration.ofHours(24)), BookingStatus.CREATED);
        when(bookings.findById(booking.id())).thenReturn(Optional.of(booking));
        when(users.findIdByEmail("customer@example.com")).thenReturn(Optional.of(customerId));
        when(bookings.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = service(bookings, offerings, users, publisher, now)
                .cancel(new CancelBookingCommand(booking.id(), "customer@example.com"));

        assertEquals(BookingStatus.CANCELLED, result.status());
        verify(bookings).save(result);
        verifyNoInteractions(publisher);
    }

    @Test
    void shouldHideBookingOwnedByAnotherCustomer() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        Instant now = Instant.parse("2030-10-10T12:00:00Z");
        Booking booking = booking(UUID.randomUUID(), now.plus(Duration.ofHours(24)), BookingStatus.CREATED);
        when(bookings.findById(booking.id())).thenReturn(Optional.of(booking));
        when(users.findIdByEmail("other@example.com")).thenReturn(Optional.of(UUID.randomUUID()));

        assertThrows(DomainNotFoundException.class, () -> service(bookings, offerings, users, publisher, now)
                .cancel(new CancelBookingCommand(booking.id(), "other@example.com")));

        verify(bookings, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void shouldRejectMissingBookingWithoutSideEffects() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        UUID bookingId = UUID.randomUUID();
        when(bookings.findById(bookingId)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () -> service(bookings, offerings, users, publisher, Instant.parse("2030-10-10T12:00:00Z"))
                .cancel(new CancelBookingCommand(bookingId, "customer@example.com")));

        verify(bookings, never()).save(any());
        verifyNoInteractions(users, publisher);
    }

    @Test
    void shouldRejectCancellationWithInsufficientNoticeWithoutSideEffects() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        Instant now = Instant.parse("2030-10-10T12:00:00Z");
        UUID customerId = UUID.randomUUID();
        Booking booking = booking(customerId, now.plus(Duration.ofHours(24)).minusSeconds(1), BookingStatus.CREATED);
        when(bookings.findById(booking.id())).thenReturn(Optional.of(booking));
        when(users.findIdByEmail("customer@example.com")).thenReturn(Optional.of(customerId));

        assertThrows(BusinessRuleException.class, () -> service(bookings, offerings, users, publisher, now)
                .cancel(new CancelBookingCommand(booking.id(), "customer@example.com")));

        verify(bookings, never()).save(any());
        verifyNoInteractions(publisher);
    }

    @Test
    void shouldReturnAlreadyCancelledBookingWithoutSavingOrPublishing() {
        BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        UserAccountPort users = mock(UserAccountPort.class);
        BookingEventPublisherPort publisher = mock(BookingEventPublisherPort.class);
        Instant now = Instant.parse("2030-10-10T12:00:00Z");
        UUID customerId = UUID.randomUUID();
        Booking booking = booking(customerId, now.minusSeconds(1), BookingStatus.CANCELLED);
        when(bookings.findById(booking.id())).thenReturn(Optional.of(booking));
        when(users.findIdByEmail("customer@example.com")).thenReturn(Optional.of(customerId));

        Booking result = service(bookings, offerings, users, publisher, now)
                .cancel(new CancelBookingCommand(booking.id(), "customer@example.com"));

        assertSame(booking, result);
        verify(bookings, never()).save(any());
        verifyNoInteractions(publisher);
    }

    private BookingService service(
            BookingRepositoryPort bookings,
            OfferingRepositoryPort offerings,
            UserAccountPort users,
            BookingEventPublisherPort publisher,
            Instant now) {
        return new BookingService(bookings, offerings, users, publisher, cancellationPolicyAt(now));
    }

    private BookingCancellationPolicy cancellationPolicyAt(Instant now) {
        return new BookingCancellationPolicy(Clock.fixed(now, ZoneOffset.UTC), Duration.ofHours(24));
    }

    private Booking booking(UUID customerId, Instant scheduledAt, BookingStatus status) {
        return new Booking(UUID.randomUUID(), UUID.randomUUID(), customerId, scheduledAt, status);
    }
}
