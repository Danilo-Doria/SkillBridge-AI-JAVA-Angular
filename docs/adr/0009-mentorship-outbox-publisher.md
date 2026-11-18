# ADR-0009: Transactional Outbox Publisher for Mentorship Events

## Status
Accepted (HU-MNT-08).

## Date
2026-11-16

## Context
`BackendNest.js` already persisted mentorship events in `outbox_events` atomically with the
business transition (HU-MNT-07), but nothing published them to Kafka. Publishing inline from the
request path would risk losing audit events when the broker is unavailable or the process dies
between the database commit and the broker acknowledgement.

The existing `outbox_events` table already had the critical uniqueness guarantees (`eventId` unique)
and JSONB payloads. MNT-07 use cases already wrote to it inside their transactions. Replacing it
with a library-managed table would have meant rewriting those writes and duplicating the schema.

## Decision
Implement an in-process outbox publisher inside `BackendNest.js`, using `kafkajs` behind an
isolated `EventPublisherPort`:

1. Extend `outbox_events` additively with `sequence` (insertion order), `publishState`, `attempts`,
   `nextAttemptAt`, `lastError`, `lockedUntil` and `lockedBy`.
2. Claim pending rows with `FOR UPDATE SKIP LOCKED` plus a time-bounded lease, so multiple
   replicas do not publish the same row concurrently.
3. Preserve per-aggregate order: only the oldest pending event of each aggregate is claimable,
   using `occurredAt` and the `sequence` column as the tie-breaker.
4. Publish a `BusinessEvent` envelope to `booking-events` with `acks=all`, idempotent producer and
   the aggregate id as the Kafka key. Mark `publishedAt` only after acknowledgement.
5. Retry transient failures with exponential backoff. After the configured maximum attempts the
   row becomes `FAILED` and stops blocking later events of the same aggregate.
6. Keep the publisher disabled by default outside production (`KAFKA_ENABLED=false`); production
   fails fast if enabled without brokers.

We evaluated `@nestjs/outbox@0.1.4`. It was rejected because it manages its own `nest_outbox`
schema and polling contract, which would require rewriting the MNT-07 writes and a data migration
without adding guarantees the existing table did not already provide.

## Consequences
- **Positive**: No event is lost between the business transaction and Kafka; a broker outage only
  delays publication.
- **Positive**: Multiple API replicas can run the publisher safely.
- **Positive**: The publisher is isolated behind a port and can be tested without Kafka.
- **Negative / Trade-off**: At-least-once delivery; consumers must deduplicate by `eventId`.
- **Negative / Trade-off**: Head-of-line blocking per aggregate while the oldest event is retrying.
- **Deferred**: Automatic pruning of `PUBLISHED` rows, dynamic topic creation and a DLQ are out of
  scope for this HU.
