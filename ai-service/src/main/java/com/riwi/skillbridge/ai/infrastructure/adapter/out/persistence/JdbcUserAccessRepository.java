package com.riwi.skillbridge.ai.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.ai.application.port.out.UserAccessRepositoryPort;
import com.riwi.skillbridge.ai.domain.model.AccessStatus;
import com.riwi.skillbridge.ai.domain.model.UserAccess;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/** {@code ai.user_access} con JDBC (sin entidad JPA): upsert condicionado y lectura por PK. */
@Repository
public class JdbcUserAccessRepository implements UserAccessRepositoryPort {

    private static final String UPSERT = """
        INSERT INTO ai.user_access (user_id, status, role, last_event_id, last_event_at)
        VALUES (:userId, :status, :role, :eventId, :occurredAt)
        ON CONFLICT (user_id) DO UPDATE SET
            status        = EXCLUDED.status,
            role          = EXCLUDED.role,
            last_event_id = EXCLUDED.last_event_id,
            last_event_at = EXCLUDED.last_event_at
        WHERE (ai.user_access.last_event_at IS NULL OR ai.user_access.last_event_at <= EXCLUDED.last_event_at)
          AND ai.user_access.last_event_id IS DISTINCT FROM EXCLUDED.last_event_id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcUserAccessRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccess> findById(UUID userId) {
        return jdbc.query("SELECT user_id, status, role FROM ai.user_access WHERE user_id = :id",
                new MapSqlParameterSource("id", userId),
                (rs, n) -> new UserAccess(rs.getObject("user_id", UUID.class),
                        AccessStatus.valueOf(rs.getString("status")), rs.getString("role")))
            .stream().findFirst();
    }

    @Override
    public boolean upsert(UserAccess access, UUID eventId, Instant occurredAt) {
        return jdbc.update(UPSERT, new MapSqlParameterSource()
            .addValue("userId", access.userId())
            .addValue("status", access.status().name())
            .addValue("role", access.role())
            .addValue("eventId", eventId)
            .addValue("occurredAt", OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))) > 0;
    }
}
