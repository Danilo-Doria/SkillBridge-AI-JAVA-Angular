-- HU-104 (hallazgo S1): estado de acceso de los usuarios del monolito, para revocar tokens antes de que expiren.
-- Se alimenta solo con eventos UserAccessChanged v1 del topic booking-events (clave userId). Sin fila = usuario
-- activo con el rol del token (comportamiento anterior). Consulta por PK en cada petición autenticada.
CREATE TABLE IF NOT EXISTS user_access (
    user_id       UUID PRIMARY KEY,
    status        TEXT NOT NULL,
    role          TEXT NOT NULL,
    last_event_id UUID,
    last_event_at TIMESTAMPTZ
);
