# Suggested Cell Backlog

## Sprint 0 — Foundation
- ADRs, bounded context and ubiquitous language.
- Run full stack using Docker Compose.
- Verify health, Swagger, RabbitMQ UI and database migrations.

## Sprint 1 — Identity and catalog
- Register/login with JWT.
- RBAC: CUSTOMER, PROVIDER, ADMIN.
- Provider can publish/update/deactivate an offering.
- Cache public catalog and invalidate on modifications.

## Sprint 2 — Booking
- Customer books an available service.
- Enforce business rules in domain/application layer.
- Publish `BookingCreated` event.
- Async consumer generates a notification record.

## Sprint 3 — Event-driven hardening
- Retry policy.
- Dead-letter queue.
- Idempotency key for booking commands.
- Implement Transactional Outbox as an advanced challenge.

## Sprint 4 — AI
- Recommendation assistant based only on existing catalog.
- Persist prompt metadata without storing unnecessary sensitive content.
- Cache repeated non-personal AI queries.
- Add timeout/fallback behavior for the LLM provider.

## Sprint 5 — Quality and operation
- Unit tests for use cases.
- Integration tests using Testcontainers.
- Coverage report with JaCoCo.
- Metrics dashboard in Grafana.
- CI on Pull Requests.

## Sprint 6 — Cloud
- Deploy backend, frontend and managed dependencies.
- Configure secrets using provider environment variables.
- Validate CORS/TLS/health checks.
- Produce an architecture diagram and deployment evidence.
