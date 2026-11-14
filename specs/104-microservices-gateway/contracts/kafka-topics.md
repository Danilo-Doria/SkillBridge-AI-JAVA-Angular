# Contrato: topics Kafka tras retirar RabbitMQ

Estado: **Draft**. Sustituye los exchanges/colas AMQP de antes (`RabbitConfiguration.java`, retirado en HU-104 W2; verificado 2026-11-13).
Todos los mensajes usan el envelope `BusinessEvent` (ADR-0006 §4) en JSON. Los consumidores **deben** ser idempotentes por `eventId` (entrega at-least-once).

| Topic | Productor | Consumidores (group-id) | Clave | Reemplaza | Particiones iniciales |
|---|---|---|---|---|---|
| `booking-events` | monolito, `ai-service` (recomendaciones) | `audit-service` (existente), analytics, `ai-service` (`OfferingUpserted`) | `aggregateId` (`offeringId` en `OfferingUpserted`) | sin cambio (ADR-0003) | las actuales |
| `payment-events` | `payment-service` (antes monolito) | `booking` (monolito), `mentorship-service` | `bookingId` | exchange `payment.events` (`payment.approved/rejected`) | 3 |
| `notification-requests` | monolito (booking, recordatorios) | `notification-service` | `bookingId` | `notification.exchange` + `notification.queue` (3 routing keys) | 3 |
| `payment-events.DLT` | error handler | operación | igual | DLQ de mentorías/pagos | 1 |
| `notification-requests.DLT` | error handler | operación | igual | `notification.dlq` | 1 |

## Tipos de evento (`eventType`)

| eventType | Topic | Payload mínimo (ECST: estado completo, sin re-consultas) |
|---|---|---|
| `PaymentApproved` v2 | `payment-events` | los campos ya definidos en `docs/contracts/mentorships/events.md` (incluye `mode`, `durationHours` de la oferta) |
| `PaymentRejected` | `payment-events` | `bookingId`, `reason`, `occurredAt` |
| `BookingCreatedNotification` | `notification-requests` | destinatario, plantilla, datos de la reserva |
| `BookingCancelledNotification` | `notification-requests` | ídem |
| `PaymentReminderNotification` | `notification-requests` | ídem |
| `OfferingUpserted` v1 | `booking-events` | Productor: monolito (`OfferingCommandService`, en cada alta/cambio de oferta). Consumidor: `ai-service` (group-id `ai-service`). Clave: `offeringId`. Payload: `id`, `providerId`, `title`, `description`, `category`, `price`, `active`, `mode`, `durationHours`. Snapshot al arrancar: si `app.catalog.snapshot-on-startup=true` (`APP_CATALOG_SNAPSHOT_ON_STARTUP`), `OfferingSnapshotOnStartup` publica uno por cada oferta (activas e inactivas, para que una desactivación más vieja que la retención llegue igual) |
| `BookingStatusChanged` v1 | `booking-events` | Productor: monolito (`BookingService`: pago aprobado → CONFIRMED; rechazo con plazo vencido → CANCELLED). Consumidor: `payment-service` (`payment.booking_view`). Clave `bookingId`. Payload: `bookingId`, `offeringId`, `customerId`, `scheduledAt`, `status`, `customerEmail` |
| `BookingStateSnapshot` v1 | `booking-events` | Mismo payload que `BookingStatusChanged`. Snapshot de reservas CREATED/CONFIRMED al arrancar el monolito (`APP_BOOKINGS_SNAPSHOT_ON_STARTUP=true`). audit-service lo ignora (no es un hecho auditable) |
| `UserAccessChanged` v1 | `booking-events` | Productor: monolito (`UserAdminService`, cambio de estado o rol; snapshot de usuarios SUSPENDED al arrancar con `APP_USERS_ACCESS_SNAPSHOT_ON_STARTUP=true`). Consumidores: `ai-service`, `payment-service` (rechazan tokens de suspendidos o con rol cambiado). Clave `userId`. Payload: `userId`, `status`, `role` (sin PII) |

## Cabeceras Kafka
`X-Correlation-Id`, `eventId`, `eventType`, `schemaVersion`. El consumidor restaura `correlationId` en MDC antes de procesar.

## Política de consumo
- `DefaultErrorHandler` con `ExponentialBackOff` (3 reintentos; valor inicial 1 s, multiplicador 2) y `DeadLetterPublishingRecoverer` hacia `<topic>.DLT`.
- Errores de deserialización: `ErrorHandlingDeserializer`; **no** bloquear la partición.
- Un `group-id` por servicio consumidor; sin compartir.
- Idempotencia: tabla/índice único de `eventId` procesados por consumidor (o upsert natural).
- Orden: garantizado por clave `bookingId` dentro de una partición.

## Compatibilidad con mentorías
`docs/contracts/mentorships/events.md` y la HU-MNT-02 (#84) describen todavía el RabbitMQ retirado (antes de HU-104). **No se editan en esta iteración** (pertenecen a otro equipo): se abre issue de coordinación (T004) con este contrato como propuesta.


## Analítica
`RecommendationGenerated` se publica en `booking-events` (auditoría) **y** en `recommendation-events` (analytics/trending), con el mismo `eventId` (idempotente).
