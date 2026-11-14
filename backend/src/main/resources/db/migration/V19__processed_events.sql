-- HU-104 (US4): idempotencia de consumidores Kafka por eventId (entrega at-least-once).
-- Una fila por (grupo consumidor, evento) ya procesado. Ver docs/guides/kafka-dlt-runbook.md.
CREATE TABLE processed_events (
    consumer_group VARCHAR(100) NOT NULL,
    event_id       UUID         NOT NULL,
    event_type     VARCHAR(100),
    processed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_events PRIMARY KEY (consumer_group, event_id)
);

CREATE INDEX idx_processed_events_processed_at ON processed_events (processed_at);
