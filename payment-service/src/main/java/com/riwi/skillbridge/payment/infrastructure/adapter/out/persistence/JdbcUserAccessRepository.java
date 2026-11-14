package com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence;

import com.riwi.skillbridge.payment.application.port.out.UserAccessRepositoryPort;
import com.riwi.skillbridge.payment.domain.model.AccessStatus;
import com.riwi.skillbridge.payment.domain.model.UserAccess;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence.JdbcTime.utc;
import static com.riwi.skillbridge.payment.infrastructure.adapter.out.persistence.JdbcTime.uuid;

/** {@code payment.user_access}: upsert condicionado a eventos iguales o más recientes y lectura por PK. */
@Repository
public class JdbcUserAccessRepository implements UserAccessRepositoryPort {

    private static final String UPSERT = """
        INSERT INTO user_access (user_id, status, role, last_event_id, last_event_at)
        VALUES (:userId, :status, :role, :eventId, :occurredAt)
        ON CONFLICT (user_id) DO UPDATE SET
            status        = EXCLUDED.status,
            role          = EXCLUDED.role,
            last_event_id = EXCLUDED.last_event_id,
            last_event_at = EXCLUDED.last_event_at
        WHERE (user_access.last_event_at IS NULL OR user_access.last_event_at <= EXCLUDED.last_event_at)
          AND user_access.last_event_id IS DISTINCT FROM EXCLUDED.last_event_id
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcUserAccessRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccess> findById(UUID userId) {
        return jdbc.query("SELECT user_id, status, role FROM user_access WHERE user_id = :id",
                new MapSqlParameterSource("id", userId),
                (rs, n) -> new UserAccess(uuid(rs, "user_id"), AccessStatus.valueOf(rs.getString("status")),
                        rs.getString("role")))
            .stream().findFirst();
    }

    @Override
    public boolean upsert(UserAccess access, UUID eventId, Instant occurredAt) {
        return jdbc.update(UPSERT, new MapSqlParameterSource()
            .addValue("userId", access.userId())
            .addValue("status", access.status().name())
            .addValue("role", access.role())
            .addValue("eventId", eventId)
            .addValue("occurredAt", utc(occurredAt))) > 0;
    }
}
