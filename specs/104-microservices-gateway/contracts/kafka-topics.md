# Contrato: topics Kafka tras retirar RabbitMQ

Estado: **Draft**. Sustituye los exchanges/colas AMQP actuales (`RabbitConfiguration.java`, verificado 2026-11-13).
Todos los mensajes usan el envelope `BusinessEvent` (ADR-0006 §4) en JSON. Los consumidores **deben** ser idempotentes por `eventId` (entrega at-least-once).

| Topic | Productor | Consumidores (group-id) | Clave | Reemplaza | Particiones iniciales |
|---|---|---|---|---|---|
| `booking-events` | monolito | `audit-service` (existente), analytics | `aggregateId` | sin cambio (ADR-0003) | las actuales |
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

## Cabeceras Kafka
`X-Correlation-Id`, `eventId`, `eventType`, `schemaVersion`. El consumidor restaura `correlationId` en MDC antes de procesar.

## Política de consumo
- `DefaultErrorHandler` con `ExponentialBackOff` (3 reintentos; valor inicial 1 s, multiplicador 2) y `DeadLetterPublishingRecoverer` hacia `<topic>.DLT`.
- Errores de deserialización: `ErrorHandlingDeserializer`; **no** bloquear la partición.
- Un `group-id` por servicio consumidor; sin compartir.
- Idempotencia: tabla/índice único de `eventId` procesados por consumidor (o upsert natural).
- Orden: garantizado por clave `bookingId` dentro de una partición.

## Compatibilidad con mentorías
`docs/contracts/mentorships/events.md` y la HU-MNT-02 (#84) describen RabbitMQ. **No se editan en esta iteración** (pertenecen a otro equipo): se abre issue de coordinación (T004) con este contrato como propuesta.
