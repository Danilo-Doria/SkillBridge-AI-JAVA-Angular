package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.*;
import com.riwi.skillbridge.application.port.out.event.BookingStatusChangedPayload;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import com.riwi.skillbridge.domain.service.BookingCancellationPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** HU-104 (E1): transiciones de reserva visibles para payment-service. */
class BookingStatusEventsTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private final BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
    private final UserRepositoryPort users = mock(UserRepositoryPort.class);
    private final BookingEventPublisherPort events = mock(BookingEventPublisherPort.class);
    private final BookingService service = new BookingService(bookings, mock(OfferingRepositoryPort.class), users,
        new BookingCancellationPolicy(Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(24)),
        mock(NotificationPublisherPort.class), mock(BookingStatusHistoryPort.class),
        Clock.fixed(NOW, ZoneOffset.UTC), events);
    private final UUID customerId = UUID.randomUUID();

    private Booking booking(Instant scheduledAt, BookingStatus status) {
        return new Booking(UUID.randomUUID(), UUID.randomUUID(), customerId, scheduledAt, status, 0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<BusinessEvent<?>> published() {
        ArgumentCaptor<BusinessEvent> c = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(events, atLeastOnce()).publish(c.capture());
        return (List) c.getAllValues();
    }

    @Test
    void pago_aprobado_publica_status_changed_confirmed_con_email() {
        Booking b = booking(NOW.plus(Duration.ofDays(3)), BookingStatus.CREATED);
        when(bookings.findById(b.id())).thenReturn(Optional.of(b));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));
        when(users.findById(customerId)).thenReturn(Optional.of(new UserAccount(customerId, "Ana", "ana@t.co", "h", Role.CUSTOMER)));

        service.processPaymentApproved(b.id());

        BusinessEvent<?> e = published().get(0);
        assertEquals("BookingStatusChanged", e.eventType());
        assertEquals(b.id().toString(), e.aggregateId());
        BookingStatusChangedPayload p = (BookingStatusChangedPayload) e.payload();
        assertEquals("CONFIRMED", p.status());
        assertEquals("ana@t.co", p.customerEmail());
    }

    @Test
    void rechazo_con_plazo_vencido_cancela_y_publica() {
        Booking b = booking(NOW.plus(Duration.ofHours(5)), BookingStatus.CREATED); // < 12 h: plazo vencido
        when(bookings.findById(b.id())).thenReturn(Optional.of(b));
        when(bookings.save(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        service.processPaymentRejected(b.id(), "expirado");

        verify(bookings).save(argThat((Booking x) -> x.status() == BookingStatus.CANCELLED));
        assertEquals("CANCELLED", ((BookingStatusChangedPayload) published().get(0).payload()).status());
    }

    @Test
    void rechazo_normal_deja_la_reserva_en_created_sin_eventos() {
        Booking b = booking(NOW.plus(Duration.ofDays(3)), BookingStatus.CREATED);
        when(bookings.findById(b.id())).thenReturn(Optional.of(b));

        service.processPaymentRejected(b.id(), "tarjeta rechazada");

        verify(bookings, never()).save(any(Booking.class));
        verifyNoInteractions(events);
    }

    @Test
    void snapshot_publica_reservas_abiertas() {
        when(bookings.findByStatus(BookingStatus.CREATED)).thenReturn(List.of(booking(NOW.plus(Duration.ofDays(2)), BookingStatus.CREATED)));
        when(bookings.findByStatus(BookingStatus.CONFIRMED)).thenReturn(List.of(booking(NOW.plus(Duration.ofDays(4)), BookingStatus.CONFIRMED)));

        assertEquals(2, service.publishOpenBookingsSnapshot());
        assertTrue(published().stream().allMatch(e -> "BookingStateSnapshot".equals(e.eventType())));
    }
}
