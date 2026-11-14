# HUMAN-TODO: lo que solo puede hacer el usuario

Nada de esto bloquea el trabajo en el repo; el orquestador deja todo preparado y solo cambia lo que falta aquí.

## Antes de lanzar a Opus
- [ ] Crear la rama y commitear los docs SDD (ver `ORCHESTRATION.md` §0).
- [ ] (Opcional) Crear el issue real de GitHub y, si su número no es 104, renombrar `specs/104-microservices-gateway/` y la rama.
- [ ] Confirmar que Docker está corriendo (`docker info`) para que se puedan ejecutar los tests con Testcontainers.

## Después de la pasada
- [ ] Aprobar o rechazar **ADR-0009** (`Proposed` → `Accepted`).
- [ ] Revisar y commitear el trabajo (los agentes no lo hacen).
- [ ] Enviar al equipo NestJS el borrador de issue (abajo) y acordar el cambio de RabbitMQ a Kafka.
- [ ] Crear la VM Oracle Always Free (ARM), abrir 22/80/443, instalar Docker (ver `docs/guides/oracle-free-deployment.md`).
- [ ] Definir `DOMAIN` y apuntar el DNS a la IP de la VM.
- [ ] Cargar secretos en GitHub Actions y en la VM (JWT privada RS256, Stripe, DB, SMTP, basicAuth de `/docs`, token ACME si aplica, `SSH_*` de despliegue). **Nunca** en el repo.
- [ ] Decidir si se activa `routers.cutover.yml` (mover `/api/ai` y `/api/payments`/`webhooks` a los servicios nuevos) tras probar en la VM.
- [ ] **Antes de activar `CUTOVER_PAYMENTS`**: las precondiciones técnicas ya están implementadas (eventos de estado de reserva, cancelación por plazo vencido, snapshot de reservas; T081/T082). Queda **revisar los cambios de contrato** de `payment-service` frente al monolito: Idempotency-Key por cliente, 422 si el importe no coincide con la oferta, DECLINED si ya hay pago aprobado, 503 ante 5xx/401/429 de Stripe.
- [ ] Configurar el webhook de Stripe hacia `https://<DOMAIN>/api/webhooks/stripe`.
- [ ] Configurar el respaldo externo (p. ej. bucket gratuito) y probar una restauración.
- [ ] Medir en la VM Oracle con `specs/104-microservices-gateway/measure/measure.sh` (guía en `measure/README.md`) y reemplazar las estimaciones del plan/ADR por los valores medidos.
- [ ] Decidir sobre el issue #103 y la compuerta para extraer identity/catalog/booking.

## Borrador de issue para el equipo NestJS (no enviado)
**Título**: Migrar el aprovisionamiento de mentorías de RabbitMQ a Kafka (HU-MNT-02, #84)
**Contexto**: el monolito retira RabbitMQ (ADR-0009). El evento `PaymentApproved` v2 pasa al topic Kafka `payment-events` (clave `bookingId`, envelope `BusinessEvent`).
**Propuesta**: ver `specs/104-microservices-gateway/contracts/kafka-topics.md` (group-id propio, idempotencia por `eventId`, DLT `payment-events.DLT`).
**Impacto**: `docs/contracts/mentorships/events.md` hoy describe `payment.events` y `mentorship.payment.approved.queue`. Ese contrato lo mantiene el equipo de mentorías.
**Pedido**: confirmar o contraproponer, y fecha para actualizar el contrato.
