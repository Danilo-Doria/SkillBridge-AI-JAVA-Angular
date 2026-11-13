# WP-E — Módulo `payment-service` (tareas T060-T062) — ALTO RIESGO
**Léelo junto con**: `CLAUDE.md`, `AGENTS.md`, `specs/104-microservices-gateway/{spec.md,plan.md,tasks.md}` y `contracts/`. Reglas: no commit/stage, no cambiar de rama, no secretos/`.env`, no editar `docker-compose*.yml` (devuelve el fragmento de servicio en tu informe), no acciones externas. Reporta solo lo que ejecutaste; si no, "No ejecutado en esta sesión". Informe final ≤ 30 líneas: archivos tocados, comandos y resultado real, pendientes.

## Scope
`payment-service/**` (nuevo). **Forbidden**: `backend/**`, `audit-service/**`, `docker-compose*.yml`. Nunca llamar a Stripe real; usar modo test/mocks.

## Reglas de seguridad (constitución Art. III)
Sin PAN/CVV/expiración en código, logs, BD ni trazas. Solo payment intent ID y tokens de cliente de Stripe. Validar propiedad de la reserva con el contexto autenticado. Webhook con cuerpo crudo y verificación de firma antes de cualquier efecto.

## Entregables
Maven independiente, hexagonal; `PaymentController` (`/api/payments`), `StripeWebhookController` (`/api/webhooks/stripe`), publicador de `PaymentApproved` v2 y `PaymentRejected` a `payment-events` (contrato Kafka), idempotencia del webhook por id de evento de Stripe, JWT RS256 + CSRF, schema propio `payment`.
Propiedad de la reserva **sin leer la BD del monolito**: elegir y documentar (a) read model con `ownerId` desde `booking-events` o (b) API de lectura interna autenticada; si el contrato actual no transporta `ownerId`, proponer el cambio aditivo en el informe, sin editar el monolito.
Fragmento de Compose en el informe.

## Acceptance criteria
- `cd payment-service && mvn test` ejecutado y reportado, incluyendo: acceso denegado a quien no es dueño de la reserva, firma de webhook inválida rechazada, webhook duplicado idempotente.
- `grep -rniE "cvv|card.?number|pan\b" payment-service/src` no muestra almacenamiento ni logging de datos de tarjeta (adjuntar salida).
- Sin dependencia de compilación con `backend/`.
- Sin cambios fuera de `payment-service/`.
