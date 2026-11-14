-- HU-104 WP-D: idempotencia por eventId del consumidor de notification-requests.
-- Schema propio "notification" (FR-010): el servicio no lee tablas de otros servicios.
CREATE TABLE IF NOT EXISTS notification.processed_events (
    consumer_group VARCHAR(100) NOT NULL,
    event_id       UUID         NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_events PRIMARY KEY (consumer_group, event_id)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at
    ON notification.processed_events (processed_at);
