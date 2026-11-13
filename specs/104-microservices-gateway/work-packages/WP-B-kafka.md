# WP-B — Kafka como único broker en el monolito (tareas T030-T036, T039 repo-side)
**Léelo junto con**: `CLAUDE.md`, `AGENTS.md`, `specs/104-microservices-gateway/{spec.md,plan.md,tasks.md}` y `contracts/`. Reglas: no commit/stage, no cambiar de rama, no secretos/`.env`, no editar `docker-compose*.yml` (devuelve el fragmento de servicio en tu informe), no acciones externas. Reporta solo lo que ejecutaste; si no, "No ejecutado en esta sesión". Informe final ≤ 30 líneas: archivos tocados, comandos y resultado real, pendientes.

## Scope
`backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/`, `.../adapter/out/messaging/`, `.../infrastructure/config/` (solo clases de mensajería), `backend/src/test/**` (mensajería), `backend/pom.xml` (solo dependencias de mensajería), `backend/src/main/resources/application*.yml` (solo `app.messaging`, `app.kafka`, `spring.kafka`).
**Forbidden**: migraciones Flyway existentes, `docs/audit/**`, `audit-service/**`, `frontend/**`, `docker-compose*.yml`.

## Entregables
1. Topics `payment-events`, `notification-requests` y sus `*.DLT` según `contracts/kafka-topics.md`.
2. Publicadores y consumidores Kafka detrás de los **mismos puertos** de `application/port/out`; el dominio no importa frameworks. Flags `app.messaging.payments` y `app.messaging.notifications` = `rabbit|kafka` (defecto `kafka`) para una transición reversible.
3. Consumidores idempotentes por `eventId`; `DefaultErrorHandler` con backoff exponencial + `DeadLetterPublishingRecoverer`; `ErrorHandlingDeserializer`.
4. `correlationId` en envelope y cabeceras Kafka; MDC restaurado al consumir.
5. Tests: duplicado, orden por clave, DLT tras reintentos, correlación, contrato del envelope y de `PaymentApproved` v2.
6. Runbook de reprocesado de DLT en `docs/` (nuevo archivo, p. ej. `docs/guides/kafka-dlt-runbook.md`).
7. **No eliminar** aún clases `Rabbit*` ni la dependencia AMQP (lo hace el orquestador en W2 tras verificar). Sí dejar listo el inventario exacto de qué borrar.

## Acceptance criteria
- `mvn -o validate` y `mvn test` en `backend/` ejecutados y reportados (los que requieran Docker, marcados como tal).
- Con ambos flags en `kafka`, el flujo reserva → pago aprobado → notificación funciona en un test de integración con Testcontainers Kafka, o queda marcado "No ejecutado" con la causa.
- Evento duplicado produce un solo efecto (test).
- Cero imports de Spring/JPA en `domain/`.
- Sin cambios fuera del scope.
