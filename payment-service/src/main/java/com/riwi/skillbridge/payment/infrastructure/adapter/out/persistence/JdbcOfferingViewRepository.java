package com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.payment.application.port.out.OfferingViewRepositoryPort;
import com.riwi.skillbridge.payment.domain.model.MentorshipMode;
import com.riwi.skillbridge.payment.domain.model.OfferingView;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence.JdbcTime.utc;
import static com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence.JdbcTime.uuid;

/** {@code payment.offering_view}, upsert condicionado a eventos más recientes (idempotente). */
@Repository
public class JdbcOfferingViewRepository implements OfferingViewRepositoryPort {

    private static final String UPSERT = """
        INSERT INTO offering_view (offering_id, provider_id, title, price, active, mode, duration_hours,
                                   last_event_id, last_event_at, updated_at)
        VALUES (:id, :providerId, :title, :price, :active, :mode, :durationHours, :eventId, :occurredAt, now())
        ON CONFLICT (offering_id) DO UPDATE SET
            provider_id    = EXCLUDED.provider_id,
            title          = EXCLUDED.title,
            price          = EXCLUDED.price,
            active         = EXCLUDED.active,
            mode           = EXCLUDED.mode,
            duration_hours = EXCLUDED.duration_hours,
            last_event_id  = EXCLUDED.last_event_id,
            last_event_at  = EXCLUDED.last_event_at,
            updated_at     = now()
        WHERE offering_view.last_event_at < EXCLUDED.last_event_at
          AND offering_view.last_event_id <> EXCLUDED.last_event_id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcOfferingViewRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OfferingView> findById(UUID offeringId) {
        return jdbc.query("""
                SELECT offering_id, provider_id, title, price, active, mode, duration_hours
                FROM offering_view WHERE offering_id = :id
                """, new MapSqlParameterSource("id", offeringId),
            (rs, n) -> new OfferingView(uuid(rs, "offering_id"), uuid(rs, "provider_id"), rs.getString("title"),
                rs.getBigDecimal("price"), rs.getBoolean("active"),
                rs.getString("mode") == null ? null : MentorshipMode.valueOf(rs.getString("mode")),
                rs.getInt("duration_hours")))
            .stream().findFirst();
    }

    @Override
    public boolean upsert(OfferingView o, UUID eventId, Instant occurredAt) {
        return jdbc.update(UPSERT, new MapSqlParameterSource()
            .addValue("id", o.id())
            .addValue("providerId", o.providerId())
            .addValue("title", o.title())
            .addValue("price", o.price())
            .addValue("active", o.active())
            .addValue("mode", o.mode() == null ? null : o.mode().name())
            .addValue("durationHours", o.durationHours())
            .addValue("eventId", eventId)
            .addValue("occurredAt", utc(occurredAt))) > 0;
    }
}
