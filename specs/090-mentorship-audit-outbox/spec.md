# Feature Specification: Publicación confiable de auditoría a Apache Kafka (Transactional Outbox) (HU-MNT-08)

**Feature Branch**: `feature/090-mentorship-audit-outbox`
**Created**: 2026-11-16
**Status**: Draft (pendiente de aprobación humana)
**Issue**: [#90](https://github.com/Danilo-Doria/SkillBridge-AI-JAVA-Angular/issues/90)
**Input**: Como responsable de auditoría y operación, quiero que todos los eventos de mentoría se publiquen de forma confiable en Apache Kafka después de confirmarse el cambio de estado en PostgreSQL, para que `audit-service` reciba el historial completo aunque el broker esté caído, el servicio se reinicie o existan múltiples instancias.

---

## Clasificación SDD

**Grande / alto riesgo** según [SDD-HYBRID-GUIDE.md](../../docs/agents/SDD-HYBRID-GUIDE.md): introduce integración con un broker externo, semántica de entrega de eventos, cambios de esquema en `outbox_events`, garantías de concurrencia y un contrato público consumido por otro microservicio. Exige `plan.md`, `checklist`, `tasks.md` y `analyze` antes de implementar.

---

## Contexto y alcance

### Hechos verificados

| Hecho | Evidencia |
|---|---|
| El servicio NestJS ya posee la tabla `outbox_events` con `eventId` único, `publishedAt`, `payload` JSONB y un índice sobre `publishedAt`. | `[VERIFIED]` `BackendNest.js/src/database/migrations/1700000000000-InitialSchema.ts:64-82` y `BackendNest.js/src/mentorship/entities/outbox-event.entity.ts:8-41`. |
| HU-MNT-07 ya escribe la sala y el evento `MentorshipLiveRoomCreated` en la misma transacción. | `[VERIFIED]` `BackendNest.js/src/mentorship/live-room/live-room.service.ts:80-98` y `BackendNest.js/src/mentorship/live-room/adapters/typeorm-live-room.repository.ts:52-70`. |
| El `OutboxRecord` actual no incluye reintentos, bloqueo de publicación ni fecha de próximo intento. | `[VERIFIED]` `BackendNest.js/src/mentorship/live-room/domain/live-room.types.ts:103-112`. |
| No existe cliente Kafka ni configuración de Kafka en el servicio NestJS; `package.json` no declara `kafkajs` ni un transporte Kafka. | `[VERIFIED]` `BackendNest.js/package.json:25-47` y `BackendNest.js/src/config/configuration.ts:47-55`. |
| El contrato de eventos exige el envelope `BusinessEvent` en el topic `booking-events` y define los `eventType` de mentoría. | `[VERIFIED]` [`events.md`](../../docs/contracts/mentorships/events.md#L1-L86) y [`ADR-0003`](../../docs/adr/0003-kafka-event-topic-unification.md). |
| El monolito publica el mismo envelope usando `aggregateId` como clave de partición. | `[VERIFIED]` `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaBookingEventPublisher.java:25-30`. |
| `audit-service` tolera `eventType` desconocidos, ignora campos nuevos y pliega actor en el primer nivel. | `[VERIFIED]` `audit-service/backend/src/main/java/com/riwi/audit/model/BusinessEvent.java:17-77` y `docs/contracts/mentorships/gap-matrix.md:18,43`. |
| Las transiciones de mentoría distintas de la sala en vivo aún no están implementadas (`MentorshipService` y `PaymentEventsConsumer` son stubs). | `[VERIFIED]` `BackendNest.js/src/mentorship/mentorship/mentorship.service.ts:1-4` y `BackendNest.js/src/payment-events/payment-events.consumer/payment-events.consumer.ts:1`. |
| Kafka existe en el entorno local como broker KRaft en Docker Compose. | `[VERIFIED]` `docker-compose.yml:182-199`. |
| El servicio NestJS es el dueño de la publicación de eventos de mentoría; el monolito no recibe features nuevas de mentorías. | `[VERIFIED]` [`ADR-0006`](../../docs/adr/0006-hybrid-monolith-and-services.md#L25-L45) y [`gap-matrix.md`](../../docs/contracts/mentorships/gap-matrix.md#L17). |

### Dentro del alcance

- Un publicador de outbox reutilizable que lea filas pendientes de `outbox_events` y las publique en Kafka `booking-events` con el envelope `BusinessEvent` del contrato.
- Garantía de **at-least-once**: un evento confirmado en PostgreSQL se publica al menos una vez, incluso si el broker falla o el proceso se reinicia.
- Marcar `publishedAt` solo después del acknowledgement del broker.
- Reintentos con backoff exponencial y estados de fallo observables; ninguna fila se descarta silenciosamente.
- Seguridad ante múltiples instancias: dos publicadores no publican la misma fila de forma concurrente.
- Orden de eventos por agregado (`assignmentId`) en el orden en que ocurrieron.
- Un API de aplicación para que cualquier transición de mentoría (MNT-02, MNT-03, MNT-04, MNT-05, MNT-07, MNT-09) anexe su evento al outbox dentro de la misma transacción de base de datos.
- Validación del payload contra el catálogo de `eventType` de `events.md`, prohibiendo secretos y datos de tarjeta.
- Observabilidad: logs con `correlationId`, métricas de pendientes/publicados/fallidos/reintentos y estado de salud del publicador.
- Pruebas de contrato JSON y pruebas de integración con Kafka.

### Fuera del alcance

- Implementar las transiciones de negocio y sus productores (pago aprobado, inicio, progreso, finalización, cancelación, expiración, no-show, reprogramación): corresponden a MNT-02/MNT-03/MNT-04/MNT-09. Esta HU provee la infraestructura para publicarlas.
- Cambiar la lógica de creación de salas de MNT-07; solo se generaliza el append del outbox sin alterar su comportamiento.
- Modificar `audit-service` para aceptar tipos nuevos; ya es tolerante `[VERIFIED]`. Solo se verifica la integración de extremo a extremo.
- Garantía exactly-once, transacciones distribuidas o Kafka Transactions.
- Replicación multi-región, recuperación ante desastres y políticas de retención de Kafka.
- Panel de administración, endpoint de replay manual o UI de operación.
- Publicar eventos de reservas o pagos del monolito; esos flujos ya tienen su propio publicador.
- Reemplazar RabbitMQ en el aprovisionamiento (`PaymentApproved`).

---

## Decisiones de contrato

| Tema | Contrato canónico | Decisión de esta spec |
|---|---|---|
| Topic | `booking-events` ([ADR-0003](../../docs/adr/0003-kafka-event-topic-unification.md)) | Publicar únicamente en `booking-events`; no crear un topic `mentorship-events` en esta HU. |
| Envelope | `BusinessEvent` ([events.md §1](../../docs/contracts/mentorships/events.md#L5-L28)) | Serializar exactamente los campos de primer nivel; **no** duplicar `actor*` dentro de `payload`. |
| Clave de partición | `aggregateId` | Usar `aggregateId` (`assignmentId`) para preservar orden por agregado. |
| `aggregateType` | `Mentorship` | Fijo para todos los eventos de mentoría. |
| `version` | Entero de esquema del payload | Empezar en `1`; cualquier cambio incompatible abre una versión nueva. |
| Tipos de evento | Catálogo de `events.md` §3 | Solo se publican tipos del catálogo; un tipo desconocido en el outbox es un error de programación, no un evento de negocio. |
| Entrega | At-least-once | Duplicados posibles; los consumidores deduplican por `eventId`. |

**Forma JSON mínima esperada:**

```json
{
  "eventId": "uuid",
  "eventType": "MentorshipLiveRoomCreated",
  "aggregateId": "assignment-uuid",
  "aggregateType": "Mentorship",
  "occurredAt": "2026-11-16T14:00:00.000Z",
  "correlationId": "corr-123",
  "version": 1,
  "payload": { "assignmentId": "assignment-uuid", "provider": "ZOOM", "fallbackUsed": false }
}
```

`occurredAt` DEBE ser ISO-8601 UTC con sufijo `Z`. Los campos opcionales de actor se omiten cuando no existen; no se envían como `null` salvo que el contrato lo permita.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Publicar eventos pendientes tras un cambio confirmado (Priority: P1)

Como consumidor de auditoría, quiero que cualquier evento escrito en `outbox_events` dentro de una transacción confirmada termine publicado en Kafka, incluso si el broker no estaba disponible en ese momento.

**Why this priority**: Es la promesa central de la HU. Sin publicación confiable no hay historial de auditoría íntegro y el patrón outbox no aporta valor.

**Independent Test**: Con un broker Kafka simulado que primero rechaza la conexión y luego acepta, se confirma una transacción que escribe un evento; el publicador reintenta y publica el mismo `eventId` sin perder ni alterar el payload, y solo entonces marca `publishedAt`.

**Acceptance Scenarios**:

1. **Given** una transacción confirmada con una fila de `outbox_events` y `publishedAt = null`
   **When** el publicador ejecuta su ciclo
   **Then** publica el evento en `booking-events` con el envelope completo y actualiza `publishedAt` con la hora del acknowledgement.
2. **Given** el broker Kafka no responde al momento del primer intento
   **When** el publicador falla al publicar
   **Then** la fila permanece pendiente, se registra el error y se programa un reintento con backoff; no se marca `publishedAt` ni se pierde el evento.
3. **Given** un evento ya publicado
   **When** el publicador vuelve a ejecutarse
   **Then** no lo publica de nuevo y no modifica su `publishedAt`.
4. **Given** que la transacción de negocio falla antes del commit
   **When** se revierte la transacción
   **Then** no existe fila en `outbox_events` ni se publica nada en Kafka.

---

### User Story 2 - Consumir un envelope compatible con auditoría (Priority: P1)

Como servicio de auditoría, quiero recibir eventos de mentoría con el mismo contrato JSON que ya consume, para persistirlos sin cambios y sin descartar tipos nuevos.

**Why this priority**: Un evento publicado con un formato incorrecto es indistinguible de un evento perdido para el consumidor. El contrato es parte del MVP.

**Independent Test**: Se publica cada `eventType` del catálogo con el publicador real y se deserializa con el modelo `BusinessEvent` de `audit-service`; todos se persisten con `aggregateType = Mentorship` y el `payload` esperado.

**Acceptance Scenarios**:

1. **Given** un evento `MentorshipProvisioned` en el outbox
   **When** se publica y `audit-service` lo consume
   **Then** se persiste con `eventType`, `aggregateId`, `occurredAt` y `payload` correctos.
2. **Given** un `eventType` de mentoría que `audit-service` no conoce explícitamente
   **When** lo consume
   **Then** lo almacena sin error y sin bloquear el consumidor.
3. **Given** cualquier evento publicado
   **When** se inspecciona el JSON
   **Then** no contiene `passcode`, firmas SDK, tokens OAuth, URLs de anfitrión, invitaciones de Discord, datos de tarjeta ni campos de actor duplicados en `payload`.
4. **Given** el mismo `eventId` entregado dos veces por Kafka
   **When** `audit-service` lo consume
   **Then** la deduplicación por `eventId` evita un registro duplicado.

---

### User Story 3 - Publicación segura con múltiples instancias y orden por agregado (Priority: P2)

Como operador, quiero escalar el servicio a varias réplicas sin que dos instancias publiquen la misma fila ni se altere el orden de los eventos de una misma mentoría.

**Why this priority**: El servicio se despliega en contenedores y puede escalar. Sin control de concurrencia, el outbox puede duplicar publicaciones o desordenar transiciones de estado.

**Independent Test**: Con dos publicadores apuntando a la misma base de datos, se insertan 100 eventos; cada fila se publica exactamente una vez y, para un mismo `aggregateId`, los mensajes llegan en el orden de `occurredAt`.

**Acceptance Scenarios**:

1. **Given** dos instancias del publicador activas
   **When** ambas consultan filas pendientes al mismo tiempo
   **Then** cada fila es reclamada por una sola instancia y publicada una vez.
2. **Given** tres eventos del mismo `assignmentId` con `occurredAt` creciente
   **When** se publican
   **Then** llegan a la misma partición y en el orden de ocurrencia.
3. **Given** una instancia que cae después de publicar pero antes de marcar `publishedAt`
   **When** la instancia se reinicia o la fila es reclamada por otra
   **Then** el evento puede republicarse con el mismo `eventId` (at-least-once), sin pérdida.
4. **Given** una fila bloqueada por una instancia que dejó de responder
   **When** expira el bloqueo de publicación
   **Then** otra instancia puede reclamarla sin intervención manual.

---

### User Story 4 - Operar y diagnosticar el outbox (Priority: P3)

Como operador, quiero ver el estado del outbox, los reintentos y los errores, para detectar acumulación de eventos y actuar antes de perder la auditoría.

**Why this priority**: La confiabilidad sin observabilidad se convierte en una caja negra. Aporta valor operativo, pero la HU es demostrable sin panel dedicado.

**Independent Test**: Con el broker caído, se verifica que aumentan los pendientes, que los reintentos crecen con backoff y que el log incluye `eventId`, `eventType`, `aggregateId`, `correlationId` y el motivo del error, sin secretos.

**Acceptance Scenarios**:

1. **Given** el broker caído durante un minuto
   **When** se consultan las métricas
   **Then** el número de pendientes es mayor que cero y los fallos de publicación son visibles.
2. **Given** un evento que supera el máximo de intentos
   **When** el publicador lo procesa
   **Then** queda en un estado de fallo visible, no se elimina y se emite una alerta o log de nivel error.
3. **Given** un evento publicado correctamente
   **When** se revisan los logs
   **Then** la publicación registra `eventId`, `eventType`, `aggregateId` y `correlationId` para trazabilidad.

---

### Edge Cases

- Broker no configurado en desarrollo: el servicio arranca y el outbox acumula filas pendientes; no se simula una publicación exitosa.
- Broker no configurado en producción: el arranque DEBE fallar de forma explícita o quedar en estado degradado visible; no DEBE aceptar tráfico como si la auditoría funcionara `[DECIDIR]`.
- Broker disponible pero topic inexistente: el publicador DEBE reportar el error y reintentar; no DEBE marcar el evento como publicado.
- Timeout ambiguo del productor: el mensaje pudo haberse escrito; la fila no se marca publicada y el reintento produce un duplicado con el mismo `eventId` (at-least-once aceptado).
- Reinicio del proceso después del envío y antes del commit de `publishedAt`: el evento se republica con el mismo `eventId`; el consumidor deduplica.
- Dos instancias reclaman la misma fila: el bloqueo de reclamo DEBE impedirlo; una espera y la otra publica.
- Evento con `payload` inválido o con un campo prohibido: DEBE rechazarse al anexarlo al outbox; si ya existe, DEBE quedar en fallo visible y no publicarse.
- Evento con `eventType` fuera del catálogo: DEBE rechazarse en el append y, si llega a la base por error, no DEBE publicarse.
- `correlationId` ausente: el evento se publica con `correlationId = null` u omitido; no se inventa un identificador.
- `occurredAt` en el futuro o con zona horaria distinta: se normaliza a UTC; no se altera el orden lógico sin decisión.
- Backlog grande tras una caída prolongada: el publicador DEBE procesar por lotes acotados, sin bloquear el arranque ni consumir toda la memoria.
- Cambio de esquema de un `eventType`: se incrementa `version`; los consumidores deben tolerar el campo nuevo.
- Fila publicada que se intenta modificar: los eventos son inmutables después del append; solo se actualizan metadatos de publicación.
- Eliminación o compactación de filas publicadas: no DEBE afectar a Kafka ni a la auditoría ya persistida.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El servicio DEBE proveer un publicador de outbox que lea filas de `outbox_events` con `publishedAt IS NULL` y las publique en el topic configurado `booking-events`.
- **FR-002**: El publicador DEBE ejecutarse fuera de la transacción de negocio, después del commit, sin bloquear la respuesta HTTP que originó el cambio de estado.
- **FR-003**: Cada evento DEBE publicarse con el envelope `BusinessEvent`: `eventId`, `eventType`, `aggregateId`, `aggregateType`, `occurredAt`, `correlationId`, `version` y `payload`, conforme a [`events.md`](../../docs/contracts/mentorships/events.md).
- **FR-004**: `aggregateType` DEBE ser `Mentorship` y `eventType` DEBE pertenecer al catálogo de eventos de mentoría de [`events.md`](../../docs/contracts/mentorships/events.md).
- **FR-005**: El publicador DEBE usar `aggregateId` como clave del mensaje Kafka y enviar `eventId` como identificador estable e idempotente del evento.
- **FR-006**: `publishedAt` DEBE actualizarse únicamente después de que el productor reciba acknowledgement del broker con `acks=all`.
- **FR-007**: Un fallo de conexión, timeout o rechazo del broker NO DEBE marcar la fila como publicada, NO DEBE eliminar la fila y NO DEBE propagarse como error al flujo de negocio.
- **FR-008**: Los reintentos DEBEN usar backoff exponencial con jitter, un máximo configurable de intentos y un estado de fallo persistido y visible.
- **FR-009**: Debe existir un API de aplicación para anexar eventos al outbox usando el mismo `EntityManager` de la transacción de negocio; el append DEBE ser atómico con el cambio de estado.
- **FR-010**: El append DEBE validar el `eventType`, los campos obligatorios de `payload` y la ausencia de campos prohibidos antes de persistir.
- **FR-011**: El append DEBE aceptar un `eventId` generado una sola vez y un `occurredAt` correspondiente al hecho de negocio, no al momento de publicación.
- **FR-012**: El publicador DEBE reclamar filas con un bloqueo que impida que dos instancias publiquen la misma fila simultáneamente; el bloqueo DEBE expirar si la instancia muere.
- **FR-013**: El procesamiento DEBE preservar el orden de eventos por `aggregateId` según `occurredAt` y, en empate, según el orden de inserción.
- **FR-014**: El publicador DEBE procesar por lotes configurables y permitir varias filas en vuelo sin romper el orden por agregado.
- **FR-015**: Si el broker no está disponible, el publicador DEBE reintentar indefinidamente con backoff acotado; no DEBE descartar eventos por fallos transitorios.
- **FR-016**: Si un evento supera el máximo de intentos, DEBE quedar en estado `FAILED` visible, conservarse para inspección y no publicarse automáticamente hasta una intervención o reintento explícito `[DECIDIR]`.
- **FR-017**: El publicador DEBE exponer métricas de eventos pendientes, publicados, fallidos, reintentos y latencia de publicación, sin etiquetas con datos personales o secretos.
- **FR-018**: Los logs DEBEN incluir `eventId`, `eventType`, `aggregateId` y `correlationId` en cada intento y en cada fallo, sin volcar el `payload` completo si contiene datos sensibles.
- **FR-019**: El servicio DEBE arrancar aunque Kafka no esté configurado en desarrollo; en producción la ausencia de configuración DEBE ser un fallo visible, no una degradación silenciosa `[DECIDIR]`.
- **FR-020**: La configuración DEBE declararse por variables de entorno: bootstrap servers, topic, client ID, tamaño de lote, intervalo de polling, timeout de productor, máximo de intentos y base/máximo de backoff.
- **FR-021**: El productor DEBE habilitar idempotencia (`enable.idempotence=true`) y `acks=all` para evitar duplicados por reintentos internos del cliente dentro de una sesión.
- **FR-022**: El publicador DEBE ser tolerante a reinicios: al arrancar, retoma filas pendientes y reclama bloqueos expirados sin intervención manual.
- **FR-023**: La publicación DEBE ignorar eventos ya publicados y no modificar filas publicadas salvo metadatos de publicación.
- **FR-024**: El diseño DEBE permitir que futuras transiciones de MNT-02/MNT-03/MNT-04/MNT-05/MNT-09 anexen sus eventos sin duplicar la lógica del publicador.
- **FR-025**: Las pruebas DEBEN cubrir el contrato JSON de cada `eventType`, la atomicidad del append, el reclamo concurrente y la publicación con un broker real o Testcontainers Kafka.
- **FR-026**: La suite DEBE poder ejecutarse sin credenciales reales ni servicios externos de pago; Kafka puede levantarse con Testcontainers o Docker Compose.
- **FR-027**: El publicador NO DEBE duplicar campos de actor dentro de `payload` cuando no forman parte del esquema del evento.
- **FR-028**: Las filas publicadas DEBEN conservarse el tiempo necesario para inspección y replay; la política de purga queda pendiente de decisión `[DECIDIR]`.

### Non-Functional Requirements

- **NFR-001**: La publicación DEBE ser asíncrona respecto a la petición HTTP; una caída de Kafka no DEBE incrementar la latencia percibida por el usuario.
- **NFR-002**: El publicador DEBE respetar la prohibición de mocks en producción (Constitución, Artículo V): un fallo de Kafka se reporta como fallo, nunca como publicación simulada.
- **NFR-003**: El throughput DEBE ser configurable y suficiente para vaciar un backlog de al menos 10 000 eventos sin degradar la base de datos ni la API.
- **NFR-004**: El acceso a filas pendientes DEBE usar un índice adecuado y evitar escaneos completos de `outbox_events`.
- **NFR-005**: La latencia de publicación en estado normal DEBE ser menor o igual al intervalo de polling configurado más el tiempo de envío; el valor objetivo se fijará en `plan.md`.
- **NFR-006**: El publicador DEBE ser compatible con las versiones de Node.js, NestJS y TypeORM del servicio y no introducir dependencias incompatibles con ESM.
- **NFR-007**: Los errores y métricas NO DEBEN exponer `payload`, secretos, credenciales ni datos personales.

### Key Entities

- **OutboxEvent**: fila inmutable del outbox. Atributos: `id`, `eventId` único, `eventType`, `aggregateType`, `aggregateId`, `version`, `correlationId`, `payload`, `occurredAt`, `publishedAt`, `createdAt` y metadatos de publicación (`attempts`, `nextAttemptAt`, `lastError`, `lockedUntil` o equivalentes).
- **BusinessEventEnvelope**: representación JSON publicada en Kafka; contrato vinculante descrito en `events.md`. No es una entidad persistida.
- **MentorshipEventCatalog**: catálogo de `eventType` y campos obligatorios/prohibidos por evento, derivado de `events.md`.
- **OutboxPublisher**: componente de infraestructura que reclama, publica y actualiza filas; no contiene reglas de negocio de mentorías.
- **OutboxAppendPort**: contrato de aplicación para que las transacciones de negocio anexen eventos sin conocer Kafka.
- **PublicationMetadata**: estado operativo de una fila (`PENDING`, `PUBLISHED`, `FAILED`) con intentos, próximo intento y último error.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los eventos confirmados en `outbox_events` se publica al menos una vez en Kafka, incluso tras una caída del broker de 5 minutos y un reinicio del servicio.
- **SC-002**: El 100 % de los eventos publicados valida contra el contrato JSON de `events.md` y es aceptado por el `BusinessEvent` de `audit-service`.
- **SC-003**: Cero eventos se marcan como publicados sin acknowledgement del broker en las pruebas de fallo.
- **SC-004**: Con dos instancias del publicador y 100 eventos, cada fila se publica exactamente una vez y ningún `eventId` se pierde.
- **SC-005**: El 100 % de los eventos de un mismo `aggregateId` llega en orden de `occurredAt` en las pruebas de orden.
- **SC-006**: El 100 % de los payloads publicados está libre de secretos y campos prohibidos, verificado por pruebas automatizadas.
- **SC-007**: Una caída de Kafka no produce errores 5xx ni aumenta la latencia de las peticiones de mentoría.
- **SC-008**: El estado del outbox es observable: pendientes, publicados, fallidos, reintentos y último error están disponibles en métricas o logs estructurados.
- **SC-009**: La suite de pruebas se ejecuta sin credenciales reales y cubre append atómico, publicación, reintento, concurrencia y contrato.

## Assumptions

### Hechos adicionales y dependencias

- `outbox_events` ya existe y es la fuente de verdad de los eventos pendientes `[VERIFIED]` (`InitialSchema1700000000000`).
- MNT-07 ya anexa `MentorshipLiveRoomCreated` en la misma transacción; esta HU no cambia su semántica `[VERIFIED]` (`live-room.service.ts:93-95`).
- `audit-service` ya tolera tipos desconocidos y campos nuevos `[VERIFIED]` (`BusinessEvent.java` y `gap-matrix.md:18`).
- Kafka `booking-events` es el topic canónico para eventos de negocio y auditoría `[VERIFIED]` (ADR-0003).
- Las transiciones de MNT-02/MNT-03/MNT-04/MNT-05/MNT-09 aún no existen; sus productores consumirán el API de append de esta HU `[INFERRED]` a partir de `events.md` y de los stubs actuales.
- El monolito usa `aggregateId` como clave de partición; el servicio Nest debe hacer lo mismo para mantener la coherencia `[VERIFIED]` (`KafkaBookingEventPublisher.java:28`).

### Supuestos de configuración

- Kafka está disponible en `kafka:29092` dentro de Docker Compose y en `localhost:9092` fuera de Docker `[VERIFIED]` (`docker-compose.yml:187`).
- El topic `booking-events` se crea fuera del servicio o por la infraestructura de despliegue; esta HU no asume auto-creación en producción `[UNKNOWN]`.
- El servicio puede ejecutarse con una o varias réplicas; el outbox debe soportarlo sin coordinación externa.
- Las credenciales y direcciones de Kafka provienen de variables de entorno; no se versionan valores reales.

### Incógnitas

- `[UNKNOWN]` ¿Debe el servicio crear el topic si no existe, o la infraestructura lo garantiza? Afecta a la estrategia de arranque.
- `[UNKNOWN]` ¿Cuál es la política de retención de filas publicadas (purga, archivado, replay)? Afecta al crecimiento de `outbox_events`.
- `[UNKNOWN]` ¿Qué Broker/Schema Registry usa el entorno de despliegue? El contrato actual es JSON sin Schema Registry, pero no se verificó el entorno real.
- `[UNKNOWN]` ¿Se requiere un topic de dead-letter para eventos `FAILED` o basta el estado persistido y una alerta?
- `[UNKNOWN]` ¿El servicio Nest corre con `@nestjs/outbox` o con un publicador propio? La guía interna recomienda revisar el paquete first-party antes de añadir una librería.

## Decisiones pendientes

Las siguientes decisiones requieren aprobación humana antes de `plan.md`. Se incluye una recomendación.

1. **[DECIDIR] Estrategia de publicación:** polling periódico vs. CDC (Debezium). **Recomendado:** polling con `FOR UPDATE SKIP LOCKED`, sin añadir infraestructura de CDC.
2. **[DECIDIR] Cliente Kafka:** `kafkajs` directo vs. `@nestjs/microservices` con transporte Kafka vs. `@nestjs/outbox`. **Recomendado:** evaluar `@nestjs/outbox` primero, por ser first-party y ESM; si no cubre el contrato, usar `kafkajs` con un adaptador aislado.
3. **[DECIDIR] Eventos `FAILED`:** dead-letter topic, tabla de estacionamiento o reintento manual. **Recomendado:** conservar la fila con `FAILED`, alertar y permitir reintento explícito; no introducir un DLQ en esta HU.
4. **[DECIDIR] Purga de publicados:** retención indefinida vs. N días. **Recomendado:** conservar 30 días y purgar por job programado, con métricas de antigüedad.
5. **[DECIDIR] Arranque sin Kafka:** degradado visible vs. fallo de arranque en producción. **Recomendado:** fallo de arranque en `production` y modo deshabilitado con advertencia en desarrollo/pruebas.
6. **[DECIDIR] Orden por agregado:** garantía estricta con bloqueo por `aggregateId` vs. best-effort con clave de partición. **Recomendado:** garantía estricta en el reclamo por agregado; si el costo es alto, documentar la relajación y exigir tolerancia al consumidor.
7. **[DECIDIR] Catálogo de eventos:** validación cerrada por `eventType` vs. validación solo de campos comunes. **Recomendado:** catálogo cerrado para los tipos de mentoría, derivado de `events.md`, con pruebas de contrato por tipo.

## Dependencies & Out of Scope

### Dependencies

- **HU-MNT-01**: servicio NestJS, TypeORM, PostgreSQL propio y configuración estricta.
- **HU-MNT-02 / HU-MNT-03**: asignaciones y transiciones que producirán `MentorshipProvisioned`, `MentorshipStarted`, `MentorshipProgressUpdated`, `MentorshipCompleted`, `MentorshipCancelled`, `MentorshipExpired`, `MentorshipNoShow` y `MentorshipRescheduled`.
- **HU-MNT-07**: ya produce `MentorshipLiveRoomCreated` en el outbox; esta HU lo publica.
- **HU-MNT-09**: podrá producir eventos adicionales usando el mismo API de append.
- **`audit-service`**: consumidor tolerante que persiste los eventos en MongoDB.
- **Contratos**: [`events.md`](../../docs/contracts/mentorships/events.md), [`gap-matrix.md`](../../docs/contracts/mentorships/gap-matrix.md), [`ADR-0003`](../../docs/adr/0003-kafka-event-topic-unification.md) y [`ADR-0006`](../../docs/adr/0006-hybrid-monolith-and-services.md).
- **Infraestructura**: Kafka KRaft en Docker Compose; bootstrap por variables de entorno.

### Out of Scope

- Implementación de las transiciones de negocio y de sus productores (MNT-02/MNT-03/MNT-04/MNT-09).
- Cambios en `audit-service` o en su frontend.
- Exactly-once, Kafka Transactions o transacciones XA.
- Replay manual, panel de operación o API pública de outbox.
- Migración de eventos históricos del monolito a Kafka.
- Publicación de eventos de reservas, pagos o seguridad del monolito.
- Política de retención o archivado de Kafka.

## Trazabilidad resumida

| Necesidad | Historias | Requisitos |
|---|---|---|
| Publicación confiable y at-least-once | US1 | FR-001 a FR-008, FR-015, FR-022, NFR-001 |
| Contrato compatible con auditoría | US2 | FR-003 a FR-005, FR-010, FR-011, FR-027, NFR-007 |
| Concurrencia y orden | US3 | FR-012 a FR-014, FR-021 |
| Operación y diagnóstico | US4 | FR-016 a FR-020, FR-028, NFR-002 a NFR-006 |
| Integración con futuras HUs | US1–US4 | FR-009, FR-024 a FR-026 |
