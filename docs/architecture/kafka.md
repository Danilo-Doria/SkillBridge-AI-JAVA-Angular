# Integración con Apache Kafka

## Propósito

El proyecto utiliza Apache Kafka como mecanismo para publicar **eventos de negocio persistentes** relacionados con las reservas y pagos (ej. `BookingCreated`, `BookingCancelled`), así como **eventos de auditoría** (ej. `UserLoggedIn`, `UserRegistered`, `OfferingCreated`). Estos eventos están orientados a:
- Auditoría
- Analytics
- Futuros consumidores (sistemas externos o microservicios)

## Kafka como único broker (HU-104)

Antes el proyecto usaba dos brokers: RabbitMQ para tareas y notificaciones transitorias y Kafka para eventos persistentes. Con la HU-104 (ADR-0009, en estado *Proposed*) **RabbitMQ se retiró**: Kafka es el único broker. Lo que antes iba por exchanges/colas ahora usa topics con reintentos y Dead Letter Topics:

| Topic | Uso | Clave | DLT |
|---|---|---|---|
| `booking-events` | Eventos de negocio y auditoría (incluye `OfferingUpserted`) | `aggregateId` | — |
| `recommendation-events` | `KafkaAnalyticsConsumer` espera aquí `RecommendationGenerated`, pero `AiRecommendationService` lo publica por `AuditEventPublisherPort` en `booking-events` (riesgo abierto, ver [RISK-REGISTER](../RISK-REGISTER.md)) | `aggregateId` | — |
| `payment-events` | `PaymentApproved` / `PaymentRejected` → `KafkaPaymentResultConsumer` | `bookingId` | `payment-events.DLT` |
| `notification-requests` | Peticiones de notificación → `KafkaNotificationConsumer` | `bookingId` | `notification-requests.DLT` |

Contrato completo: [`specs/104-microservices-gateway/contracts/kafka-topics.md`](../../specs/104-microservices-gateway/contracts/kafka-topics.md). Operación de las DLT: [runbook](../guides/kafka-dlt-runbook.md).

Kafka no tiene *requeue* ni colas por consumidor: el consumo es *at-least-once*, por lo que los consumidores de `payment-events` y `notification-requests` son **idempotentes por `eventId`** (tabla `processed_events`, migración `V19__processed_events.sql`).

## Arquitectura

La integración sigue la arquitectura hexagonal del proyecto, garantizando que el dominio y los casos de uso no dependan de la infraestructura de Kafka.

```mermaid
flowchart TD
    UC[Use Case / BookingService] --> |publishes| Port[BookingEventPublisherPort]
    Port --> |implemented by| Adapter[KafkaBookingEventPublisher]
    Adapter --> |KafkaTemplate| Topic[(Kafka Topic: booking-events)]
    
    Auth[AuthService] --> |publishes| AuditPort[AuditEventPublisherPort]
    AuditPort --> |implemented by| AuditAdapter[KafkaAuditEventPublisher]
    AuditAdapter --> |KafkaTemplate| Topic
    
    Topic --> Consumer[KafkaAuditConsumer]

    BS[BookingService] --> |publishes| NPort[NotificationPublisherPort]
    NPort --> |implemented by| NAdapter[KafkaNotificationPublisher]
    NAdapter --> NTopic[(notification-requests)]
    NTopic --> NCons[KafkaNotificationConsumer]
    PAS[PaymentAuthorizationService] --> |publishes| PPort[PaymentEventPublisherPort]
    PPort --> |implemented by| PAdapter[KafkaPaymentEventPublisher]
    PAdapter --> PTopic[(payment-events)]
    PTopic --> PCons[KafkaPaymentResultConsumer]
    NCons -.-> |3 reintentos fallidos| DLT[(*.DLT)]
    PCons -.-> DLT
```

## Nota sobre Topics: booking-events vs audit-events
Inicialmente se recomendó separar los eventos en `booking-events` y `audit-events`. Sin embargo, **se ha determinado que esta separación rompe la implementación existente** del `audit-service`, ya que el consumidor (`KafkaAuditConsumer`) actualmente está hardcodeado para escuchar únicamente al topic `booking-events` y existen restricciones que prohíben modificar su código fuente o su despliegue en Docker.

Como alternativa, se ha unificado la emisión de todos los eventos (tanto de negocio como de auditoría) hacia el topic `booking-events` garantizando que lleguen íntegramente al `audit-service` y sean visibles en el frontend.

## Eventos de Negocio y Auditoría

Todos los eventos publicados comparten un mismo envelope o contenedor común (`BusinessEvent`), el cual cuenta con los siguientes atributos:
- `eventId`: Identificador único del evento.
- `eventType`: Tipo de evento (ej. "BookingCreated").
- `aggregateId`: Identificador de la entidad o agregado (ej. bookingId).
- `aggregateType`: Tipo de agregado (ej. "Booking").
- `occurredAt`: Fecha y hora de ocurrencia.
- `correlationId`: Identificador de correlación para trazar la solicitud desde el controlador.
- `version`: Versión del esquema del evento.
- `payload`: Detalles específicos del evento.

**Nuevos campos de Auditoría agregados:**
Para brindar trazabilidad detallada, los eventos ahora viajan enriquecidos con la siguiente información en la raíz del evento y redundante dentro del objeto `payload` (para garantizar compatibilidad con la deserialización estricta del `audit-service` que ignora campos nuevos en la raíz):
- `actorUserId`: El ID del usuario que originó o ejecutó la acción.
- `actorUsername`: El username (o email) del actor.
- `actorRole`: El rol que tenía el actor en ese momento.
- `action`: La operación ejecutada (ej. `CREATE`, `UPDATE`, `LOGIN`, `CANCEL`).
- `resource`: El tipo de recurso afectado (ej. `BOOKING`, `USER`, `OFFERING`).
- `resourceId`: El ID del recurso específico que fue alterado o al cual se accedió.

### Eventos Soportados

1. **BookingCreated** (en `booking-events`)
   - **Propósito**: Notificar la creación de una nueva reserva.
   - **Payload**: `bookingId`, `offeringId`, `customerId`, `scheduledAt`, `status`.
   - **Auditoría**: action=CREATE, resource=BOOKING, resourceId=bookingId

2. **BookingCancelled** (en `booking-events`)
   - **Propósito**: Notificar la cancelación de una reserva.
   - **Payload**: `bookingId`, `customerId`, `status`.
   - **Auditoría**: action=CANCEL, resource=BOOKING, resourceId=bookingId

3. **UserLoggedIn** (en `booking-events`)
   - **Propósito**: Notificar el inicio de sesión exitoso de un usuario.
   - **Auditoría**: action=LOGIN, resource=USER, resourceId=userId

4. **UserRegistered** (en `booking-events`)
   - **Propósito**: Notificar el registro de un nuevo usuario en el sistema.
   - **Auditoría**: action=REGISTER, resource=USER, resourceId=newUserId

5. **Eventos Administrativos (Offerings)** (en `booking-events`)
   - `OfferingCreated`, `OfferingUpdated`, `OfferingActivated`, `OfferingDeactivated`.
   - **Auditoría**: action acorde (CREATE/UPDATE/ACTIVATE/DEACTIVATE), resource=OFFERING, resourceId=offeringId.

6. **OfferingUpserted** v1 (en `booking-events`, HU-104)
   - **Propósito**: estado completo de la oferta (ECST) para read models externos (`ai-service`).
   - **Clave**: `offeringId`. **Payload**: `id`, `providerId`, `title`, `description`, `category`, `price`, `active`, `mode`, `durationHours`.
   - **Productor**: `OfferingCommandService` en cada alta/cambio; snapshot de todas las ofertas (activas e inactivas) al arrancar si `app.catalog.snapshot-on-startup=true` (`APP_CATALOG_SNAPSHOT_ON_STARTUP`, por defecto `false`), vía `OfferingSnapshotOnStartup`.
   - **Auditoría**: action=UPSERT, resource=OFFERING, resourceId=offeringId.

7. **Pagos y notificaciones** (en `payment-events` y `notification-requests`): ver tabla de topics arriba. Cabeceras `X-Correlation-Id`, `eventId`, `eventType`, `schemaVersion`.

## Configuración y Variables de Entorno

- `KAFKA_BOOTSTRAP_SERVERS`: Direcciones de los brokers de Kafka.
- `KAFKA_TOPIC_BOOKING_EVENTS`: Nombre del topic para los eventos de reserva (por defecto `booking-events`).
- `KAFKA_TOPIC_AUDIT_EVENTS`: Por compatibilidad se iguala a `booking-events`.
- `KAFKA_CONSUMER_GROUP`: Identificador del consumer group de auditoría (por defecto `skillbridge-audit-group`).
- `KAFKA_TOPIC_PAYMENT_EVENTS` / `KAFKA_TOPIC_NOTIFICATION_REQUESTS`: nombres de `app.kafka.topic.payment-events` / `notification-requests`.
- `MESSAGING_KAFKA_*`: claves `app.messaging.kafka.*` (grupos `skillbridge-booking-payments` / `skillbridge-notifications`, particiones, timeout de publicación y `retry.*`: 3 reintentos, 1 s, x2, máximo 10 s).
- `APP_CATALOG_SNAPSHOT_ON_STARTUP`: publica el snapshot de `OfferingUpserted` al arrancar.

## Ejecución Local

Para probar Kafka de forma local utilizando Docker Compose, sigue los siguientes pasos:

1. **Levantar la infraestructura**:
   ```bash
   docker-compose up -d
   ```
   Esto levantará Postgres, Redis y **Kafka** (único broker; RabbitMQ ya no existe en `docker-compose.yml`).

2. **Iniciar el backend**:
   Ejecuta la aplicación de Spring Boot (ej. mediante tu IDE o con Maven). Asegúrate de tener las variables de entorno configuradas si ejecutas fuera de Docker.

3. **Crear una reserva o Login**:
   Realiza una petición POST a `/api/bookings` o a través del frontend para crear una reserva, o inicia sesión.

4. **Verificar la emisión del evento**:
   Revisa los logs del backend. Deberías observar los logs del Publisher y del Consumer:
   ```text
   INFO: Publicando evento en Kafka - eventId: ..., eventType: BookingCreated, aggregateId: ..., correlationId: ...
   INFO: Evento publicado exitosamente en el topic booking-events: ...
   ```
