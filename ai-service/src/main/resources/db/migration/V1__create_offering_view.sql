-- Read model de ofertas para ai-service (schema propio "ai"; Flyway propio, historial en ai.flyway_schema_history).
-- Se alimenta solo con eventos OfferingUpserted v1 del topic booking-events; nunca con la BD del monolito.
CREATE TABLE IF NOT EXISTS ai.offering_view (
    id                 UUID PRIMARY KEY,
    provider_id        UUID          NOT NULL,
    title              VARCHAR(200)  NOT NULL,
    description        TEXT,
    category           VARCHAR(120),
    price              NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    active             BOOLEAN       NOT NULL,
    mode               VARCHAR(20)   NOT NULL DEFAULT 'ASYNCHRONOUS',
    duration_hours     INTEGER       NOT NULL DEFAULT 1 CHECK (duration_hours BETWEEN 1 AND 24),
    last_event_id      UUID,
    source_occurred_at TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_offering_view_active ON ai.offering_view (active) WHERE active;
