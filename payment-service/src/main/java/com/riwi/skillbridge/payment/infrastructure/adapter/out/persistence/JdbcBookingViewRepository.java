package com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.payment.application.port.out.BookingViewRepositoryPort;
import com.riwi.skillbridge.payment.domain.model.BookingView;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence.JdbcTime.instant;
import static com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence.JdbcTime.utc;
import static com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence.JdbcTime.uuid;

/**
 * {@code payment.booking_view}. Los upserts solo se aplican si el evento es más reciente que el último aplicado
 * ({@code last_event_at}): reentregas (at-least-once) y eventos viejos no cambian nada.
 */
@Repository
public class JdbcBookingViewRepository implements BookingViewRepositoryPort {

    private static final String UPSERT_CREATED = """
        INSERT INTO booking_view (booking_id, offering_id, customer_id, customer_email, scheduled_at, status,
                                  last_event_id, last_event_at, updated_at)
        VALUES (:bookingId, :offeringId, :customerId, :customerEmail, :scheduledAt, :status, :eventId, :occurredAt, now())
        ON CONFLICT (booking_id) DO UPDATE SET
            offering_id    = COALESCE(EXCLUDED.offering_id, booking_view.offering_id),
            customer_id    = EXCLUDED.customer_id,
            customer_email = COALESCE(EXCLUDED.customer_email, booking_view.customer_email),
            scheduled_at   = COALESCE(EXCLUDED.scheduled_at, booking_view.scheduled_at),
            status         = EXCLUDED.status,
            last_event_id  = EXCLUDED.last_event_id,
            last_event_at  = EXCLUDED.last_event_at,
            updated_at     = now()
        WHERE booking_view.last_event_at < EXCLUDED.last_event_at
          AND booking_view.last_event_id <> EXCLUDED.last_event_id
        """;

    private static final String APPLY_CANCELLED = """
        INSERT INTO booking_view (booking_id, offering_id, customer_id, status, last_event_id, last_event_at, updated_at)
        VALUES (:bookingId, :offeringId, :customerId, 'CANCELLED', :eventId, :occurredAt, now())
        ON CONFLICT (booking_id) DO UPDATE SET
            offering_id   = COALESCE(EXCLUDED.offering_id, booking_view.offering_id),
            status        = 'CANCELLED',
            last_event_id = EXCLUDED.last_event_id,
            last_event_at = EXCLUDED.last_event_at,
            updated_at    = now()
        WHERE booking_view.last_event_at < EXCLUDED.last_event_at
          AND booking_view.last_event_id <> EXCLUDED.last_event_id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcBookingViewRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BookingView> findById(UUID bookingId) {
        return jdbc.query("""
                SELECT booking_id, offering_id, customer_id, customer_email, scheduled_at, status
                FROM booking_view WHERE booking_id = :id
                """, new MapSqlParameterSource("id", bookingId),
            (rs, n) -> new BookingView(uuid(rs, "booking_id"), uuid(rs, "offering_id"), uuid(rs, "customer_id"),
                rs.getString("customer_email"), instant(rs, "scheduled_at"), rs.getString("status")))
            .stream().findFirst();
    }

    @Override
    public boolean upsertCreated(BookingView booking, UUID eventId, Instant occurredAt) {
        return jdbc.update(UPSERT_CREATED, new MapSqlParameterSource()
            .addValue("bookingId", booking.bookingId())
            .addValue("offeringId", booking.offeringId())
            .addValue("customerId", booking.customerId())
            .addValue("customerEmail", booking.customerEmail())
            .addValue("scheduledAt", utc(booking.scheduledAt()))
            .addValue("status", booking.status() == null ? BookingView.CREATED : booking.status())
            .addValue("eventId", eventId)
            .addValue("occurredAt", utc(occurredAt))) > 0;
    }

    @Override
    public boolean applyCancelled(UUID bookingId, UUID offeringId, UUID customerId, UUID eventId, Instant occurredAt) {
        return jdbc.update(APPLY_CANCELLED, new MapSqlParameterSource()
            .addValue("bookingId", bookingId)
            .addValue("offeringId", offeringId)
            .addValue("customerId", customerId)
            .addValue("eventId", eventId)
            .addValue("occurredAt", utc(occurredAt))) > 0;
    }
}
