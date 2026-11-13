# WP-D — Módulo `notification-service` (tareas T050-T052)
**Léelo junto con**: `CLAUDE.md`, `AGENTS.md`, `specs/104-microservices-gateway/{spec.md,plan.md,tasks.md}` y `contracts/`. Reglas: no commit/stage, no cambiar de rama, no secretos/`.env`, no editar `docker-compose*.yml` (devuelve el fragmento de servicio en tu informe), no acciones externas. Reporta solo lo que ejecutaste; si no, "No ejecutado en esta sesión". Informe final ≤ 30 líneas: archivos tocados, comandos y resultado real, pendientes.

## Scope
`notification-service/**` (nuevo). **Forbidden**: `backend/**`, `audit-service/**`, `docker-compose*.yml`.

## Entregables
Maven independiente (Java 21, Spring Boot 3.5.6), sin controladores públicos (solo actuator), consumidor Kafka de `notification-requests` (group-id propio), plantillas y envío SMTP por variable (Mailpit en local), idempotencia por `eventId`, reintentos + `notification-requests.DLT`, restauración de `correlationId` en MDC. Reutiliza la lógica de `NotificationService`/plantillas del monolito **copiándola**, sin tocar el original. Dockerfile JVM afinada y fragmento de Compose en el informe.

## Acceptance criteria
- `cd notification-service && mvn test` ejecutado y reportado: duplicado, fallo SMTP → reintento → DLT, correlación.
- Sin puertos HTTP públicos más allá de actuator.
- Dominio sin frameworks; sin dependencia de compilación con `backend/`.
- Sin cambios fuera de `notification-service/`.
