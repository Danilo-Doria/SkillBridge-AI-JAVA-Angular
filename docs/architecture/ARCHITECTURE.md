# Architecture

## Architectural style

The backend uses **Ports & Adapters / Hexagonal Architecture**.

```text
HTTP / Kafka Consumers
        |
        v
  inbound adapters
        |
        v
  application ports
        |
        v
   use-case services
        |
        v
      domain
        |
        v
  outbound ports
   /   |   |   \
 JPA Redis Kafka AI
```

The dependency rule is intentional: domain and application code do not depend on PostgreSQL, Redis, Kafka, Gemini or Angular.

## Real reasons for each technology

- **PostgreSQL:** system of record for users, services and bookings.
- **Redis:** Cache-Aside implementation for the public service catalog.
- **Kafka (only broker since HU-104; RabbitMQ retired, ADR-0009):** business/audit events on `booking-events`, payment results on `payment-events`, notification requests on `notification-requests`, with retries, `*.DLT` topics and consumers idempotent by `eventId`.
- **Gemini:** implemented behind `AiRecommendationPort`; replaceable without changing the use case.
- **Nginx:** serves the Angular production build and reverse-proxies `/api/*` to Spring Boot in the containerized topology.
- **Traefik v3 (optional, Compose profile `gateway`):** edge gateway with prefix routing, rate limiting, circuit breaker, `X-Correlation-Id` and aggregated Swagger at `/docs` (`infra/traefik`).
- **Prometheus + Grafana:** operational visibility through Spring Boot Actuator and Micrometer.

## Deliberate extension points

Students should extend this starter rather than collapse the layers. Suggested additions:

1. Provider role and offering CRUD.
2. Availability/calendar aggregate.
3. Booking confirmation/cancellation state machine.
4. Payment port and mock payment adapter.
5. Notification port with email/WhatsApp adapter.
6. Dead-letter queue and idempotent consumers.
7. Outbox pattern for guaranteed event publishing.
8. AI response cache with a short TTL and normalized prompt key.
9. Rate limiting at Nginx or application level.
10. Testcontainers integration tests.
