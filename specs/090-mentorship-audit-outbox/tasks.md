# Tasks: Publicación confiable de auditoría a Apache Kafka (HU-MNT-08)

**Spec**: [`spec.md`](./spec.md) · **Plan**: [`plan.md`](./plan.md)
**Branch**: `feature/090-mentorship-audit-outbox` (implementado sobre la rama actual `feature/HU-MNT-07-Mentorías-en-vivo-multi-proveedor`)

## Fase 1 — Contrato y esquema

- [x] **T001** [P] Añadir `kafkajs` como dependencia del servicio NestJS.
- [x] **T002** Crear tipos de dominio del outbox (`OutboxRecord`, `OutboxPublishState`, envelope).
- [x] **T003** Crear catálogo de eventos de mentoría y validación de payload/campos prohibidos.
- [x] **T004** Extender `OutboxEventEntity` con metadata de publicación.
- [x] **T005** Crear migración `1800000000000-OutboxPublicationMetadata` y registrarla en `data-source.ts`.

## Fase 2 — Puertos y adaptadores

- [x] **T006** [P] Definir `OutboxRepository` (claim, markPublished, markRetry).
- [x] **T007** [P] Definir `EventPublisherPort`.
- [x] **T008** Implementar `TypeOrmOutboxRepository` con `FOR UPDATE SKIP LOCKED` y orden por agregado.
- [x] **T009** Implementar `KafkaEventPublisher` con `kafkajs`, `acks=all` e idempotencia.
- [x] **T010** Implementar envelope builder y serialización ISO UTC.

## Fase 3 — Casos de uso y wiring

- [x] **T011** Implementar `OutboxAppendService` transaccional y validación.
- [x] **T012** Reutilizar la validación en `TypeOrmLiveRoomRepository.saveOutbox`.
- [x] **T013** Implementar `OutboxPublisherService` (polling, backoff, FAILED, métricas, lifecycle).
- [x] **T014** Implementar `OutboxMetrics`.
- [x] **T015** Crear `OutboxModule` y registrarlo en `AppModule`.
- [x] **T016** Extender `configuration.ts` y `.env.example` con variables Kafka.

## Fase 4 — Pruebas

- [x] **T017** [P] Tests del catálogo de eventos.
- [x] **T018** [P] Tests del envelope JSON.
- [x] **T019** Tests del publisher (éxito, retry, FAILED, deshabilitado).
- [x] **T020** Tests del append transaccional.
- [x] **T021** Ampliar `test/persistence.spec.ts` para claim concurrente y orden (requiere Docker).
  - **Ejecutado**: 9/9 tests pasan con Testcontainers PostgreSQL sobre Podman (incluye append atómico, orden y desempate).

## Fase 5 — Documentación y verificación

- [x] **T022** Actualizar `docs/architecture/kafka.md` y `docs/INTEGRATIONS.md`.
- [x] **T023** Crear `docs/adr/0009-mentorship-outbox-publisher.md`.
- [x] **T024** Actualizar `BackendNest.js/README.md`.
- [x] **T025** Ejecutar `npm run build`, `npm test` y `npm run lint`; registrar resultados reales.
- [x] **T026** Ejecutar `RUN_DB_TESTS=1 npm run test:db` si Docker está disponible; en caso contrario reportarlo.
  - **Ejecutado**: 9/9 pasan usando `DOCKER_HOST` apuntando al socket de Podman.
  - **Extra**: `RUN_KAFKA_TESTS=1 npm run test:kafka` (Testcontainers Kafka + KRaft) 1/1 pasa; publica y consume el envelope real.
- **T025 resultado**: `npm run build` OK; `npm run lint` OK; `npm test` 89 passed, 10 skipped, 1 suite preexistente fallando (`src/common/validators/is-future-date-time.validator.spec.ts`, módulo ausente, no relacionada con MNT-08). `test:db` 9/9 OK. `test:kafka` 1/1 OK.
