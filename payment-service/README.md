# payment-service

Microservicio de pagos de SkillBridge (HU-104, WP-E): `POST /api/payments/authorize` (Stripe PaymentIntent) y
`POST /api/webhooks/stripe`. Módulo Maven independiente (Java 21, Spring Boot 3.5.6), schema PostgreSQL propio
`payment` con Flyway propio (`src/main/resources/db/migration`) y read models alimentados por el topic
`booking-events` (group-id `payment-service`, DLT `payment-service.booking-events.DLT`).

## Read models (`booking-events`)

| eventType (v1) | Efecto |
|---|---|
| `BookingCreated` | upsert de `booking_view` |
| `BookingStatusChanged`, `BookingStateSnapshot` | upsert completo de `booking_view` con el `status` textual del monolito (snapshot = reservas abiertas al arrancar el monolito) |
| `BookingCancelled` | `booking_view.status = CANCELLED` |
| `OfferingUpserted` | upsert de `offering_view` |
| `UserAccessChanged` | upsert de `user_access` (ver abajo) |

Idempotencia por `eventId`/`occurredAt`: un evento repetido o más antiguo no pisa uno nuevo. Una reserva
`CONFIRMED`/`COMPLETED` en `booking_view` se rechaza en `authorize` como ya pagada (DECLINED, "La reserva ya está
pagada.").

## Revocación de acceso (UserAccessChanged)

- Tabla `user_access (user_id PK, status, role, last_event_id, last_event_at)` (Flyway `V2__user_access.sql`),
  alimentada por `UserAccessChanged` v1 (clave `userId`, payload `{userId, status: ACTIVE|SUSPENDED, role}`) en el
  mismo consumidor de `booking-events`. Upsert solo si `occurredAt` es igual o posterior al guardado y el `eventId` es
  distinto. Al arrancar, el monolito publica un evento por cada usuario no ACTIVE (snapshot).
- `JwtAuthenticationFilter`, tras validar la firma RS256, consulta la fila por PK: `SUSPENDED` → 401; rol distinto del
  claim `role` → 401 (hay que volver a iniciar sesión); sin fila → comportamiento anterior. Si la consulta falla, se
  deniega (fail-closed). Ventana residual: el retraso de entrega del evento en Kafka.

## Tests

```bash
cd payment-service && mvn test   # Testcontainers PostgreSQL + Kafka (requiere Docker)
```
