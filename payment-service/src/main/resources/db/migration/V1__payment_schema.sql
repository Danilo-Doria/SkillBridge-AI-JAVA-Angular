-- HU-104 WP-E: schema propio "payment" (FR-010). Ninguna tabla guarda datos de tarjeta:
-- solo ids de Stripe (PaymentIntent, evento) y el client_secret que Stripe entrega para Elements.

-- Read model de reservas, alimentado por booking-events (BookingCreated / BookingCancelled).
CREATE TABLE booking_view (
    booking_id     UUID PRIMARY KEY,
    offering_id    UUID,
    customer_id    UUID         NOT NULL,
    -- actorUsername de BookingCreated cuando actorUserId == customerId (ver BookingEventsConsumer).
    customer_email VARCHAR(320),
    scheduled_at   TIMESTAMPTZ,
    status         VARCHAR(32)  NOT NULL,
    last_event_id  UUID         NOT NULL,
    last_event_at  TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Read model de ofertas, alimentado por booking-events (OfferingUpserted v1).
CREATE TABLE offering_view (
    offering_id    UUID PRIMARY KEY,
    provider_id    UUID,
    title          VARCHAR(255),
    price          NUMERIC(12, 2),
    active         BOOLEAN      NOT NULL,
    mode           VARCHAR(32),
    duration_hours INTEGER      NOT NULL DEFAULT 0,
    last_event_id  UUID         NOT NULL,
    last_event_at  TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Resultado de POST /api/payments/authorize por (cliente, Idempotency-Key).
CREATE TABLE payment_result (
    payment_id        UUID PRIMARY KEY,
    customer_id       UUID         NOT NULL,
    idempotency_key   VARCHAR(255) NOT NULL,
    booking_id        UUID         NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    message           VARCHAR(1000),
    client_secret     VARCHAR(255),
    payment_intent_id VARCHAR(255),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_result_idempotency UNIQUE (customer_id, idempotency_key)
);
CREATE INDEX ix_payment_result_intent ON payment_result (payment_intent_id);

-- Idempotencia del webhook: un evento de Stripe se procesa una sola vez.
CREATE TABLE processed_stripe_event (
    stripe_event_id   VARCHAR(255) PRIMARY KEY,
    event_type        VARCHAR(100) NOT NULL,
    booking_id        UUID,
    payment_intent_id VARCHAR(255),
    processed_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_processed_stripe_event_booking ON processed_stripe_event (booking_id, event_type);
