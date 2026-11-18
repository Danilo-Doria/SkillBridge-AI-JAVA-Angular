# Implementation Plan: Publicación confiable de auditoría a Apache Kafka (HU-MNT-08)

**Feature Branch**: `feature/090-mentorship-audit-outbox`
**Spec**: [`spec.md`](./spec.md)
**Created**: 2026-11-16
**Status**: Approved (decisiones resueltas por recomendación de la spec)

---

## 1. Resumen técnico

Implementar un publicador de **Transactional Outbox** en `BackendNest.js` que:

1. Reclama filas pendientes de `outbox_events` con `FOR UPDATE SKIP LOCKED`.
2. Publica el envelope `BusinessEvent` en Kafka `booking-events` usando `aggregateId` como clave.
3. Marca `publishedAt` solo después de `acks=all`.
4. Reintenta con backoff exponencial y persiste estado `FAILED` visible.
5. Preserva el orden por `aggregateId` y tolera múltiples instancias.
6. Expone un API de append transaccional reutilizable por futuras HUs.

El servicio ya escribe el outbox en la misma transacción que el cambio de estado (MNT-07). Esta HU
no redefine las transiciones de negocio: agrega la infraestructura de publicación y validación.

---

## 2. Decisiones resueltas

| # | Decisión | Elección | Justificación |
|---|---|---|---|
| 1 | Estrategia de publicación | Polling con `FOR UPDATE SKIP LOCKED` | Evita añadir Debezium/CDC y usa la infraestructura existente de PostgreSQL. |
| 2 | Cliente Kafka | `kafkajs` en un adaptador aislado | Se evaluó `@nestjs/outbox` (first-party, v0.1.4); usa su propio esquema `nest_outbox` y su propio ciclo de vida, lo que obligaría a reescribir `outbox_events` y la escritura atómica de MNT-07. No cubre el contrato sin refactor destructivo. `kafkajs` es maduro, ESM-compatible y se encapsula tras un puerto. |
| 3 | Eventos `FAILED` | Conservar fila con `publishState = FAILED`, alertar y permitir reintento explícito | No se introduce un DLQ en esta HU. |
| 4 | Purga | No implementar purga en esta HU; dejar índice y campo para una HU operativa | Evita borrar evidencia antes de definir retención. |
| 5 | Arranque sin Kafka | `production`: fallo de arranque si `KAFKA_ENABLED` está activo sin brokers; desarrollo/test: publicador deshabilitado con advertencia | No se simula éxito; el outbox sigue acumulando. |
| 6 | Orden por agregado | Garantía estricta en estado normal; un evento `FAILED` deja de bloquear a los siguientes | Evita head-of-line blocking permanente por un evento ponzoña. |
| 7 | Catálogo de eventos | Catálogo cerrado derivado de `events.md` | Detecta errores de programación en el append. |

---

## 3. Constitution Check

| Artículo | Cumplimiento |
|---|---|
| **I. Hexagonal** | El dominio del outbox (`domain/`) no importa Nest, Kafka ni TypeORM. Puertos en `ports/`; adaptadores TypeORM/Kafka en `adapters/`. |
| **II. Puertos IA** | No aplica; no se tocan capacidades de IA. |
| **III. Seguridad** | Los eventos no contienen PAN/CVV, tokens, passcodes, firmas ni URLs de anfitrión. El catálogo rechaza campos prohibidos. No se añaden endpoints ni se altera autenticación. |
| **IV. Observabilidad** | Se propaga `correlationId`, se conserva `eventId`, `eventType` y `aggregateId` en logs y métricas. El envelope mantiene el contrato. |
| **V. Verificación veraz** | Se ejecutarán `npm test` y `npm run lint`. Las pruebas que requieran Docker se reportarán como no ejecutadas si el daemon no está disponible. No hay mocks en producción. |
| **VI. No destructivo** | No se tocan migraciones existentes; se añade una migración nueva. No se modifican rutas protegidas ni secretos. |
| **VII. Aislamiento audit** | Solo se publica en Kafka; no hay dependencia de compilación con `audit-service`. |
| **VIII. Documentación** | Se actualizan `docs/architecture/kafka.md`, `docs/INTEGRATIONS.md`, `BackendNest.js/.env.example` y se crea ADR-0009 para la decisión de publicador. |
| **IX. Testing** | Unit tests sin Docker para catálogo, envelope y publisher; prueba de persistencia Testcontainers para reclamo/orden (se ejecuta solo con Docker). |

---

## 4. Arquitectura de la solución

```text
BackendNest.js/src/mentorship/outbox/
├── domain/
│   ├── outbox.types.ts          # OutboxRecord, estados, envelope
│   └── event-catalog.ts         # validación por eventType y campos prohibidos
├── ports/
│   ├── outbox.repository.ts     # claimBatch, markPublished, markRetry
│   └── event-publisher.port.ts  # contrato Kafka
├── adapters/
│   ├── typeorm-outbox.repository.ts
│   └── kafka-event-publisher.ts
├── outbox-append.service.ts     # append transaccional reutilizable
├── outbox-publisher.service.ts  # loop de polling, backoff, métricas
├── outbox.metrics.ts
└── outbox.module.ts
```

### 4.1 Cambios de esquema

Nueva migración `1800000000000-OutboxPublicationMetadata.ts`:

```sql
ALTER TABLE "outbox_events"
  ADD COLUMN "sequence" bigserial,
  ADD COLUMN "publishState" varchar(16) NOT NULL DEFAULT 'PENDING',
  ADD COLUMN "attempts" int NOT NULL DEFAULT 0,
  ADD COLUMN "nextAttemptAt" timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN "lastError" text,
  ADD COLUMN "lockedUntil" timestamptz,
  ADD COLUMN "lockedBy" varchar(100);

UPDATE "outbox_events"
SET "publishState" = 'PUBLISHED'
WHERE "publishedAt" IS NOT NULL;

CREATE INDEX "idx_outbox_events_publication_queue"
  ON "outbox_events" ("publishState", "nextAttemptAt")
  WHERE "publishedAt" IS NULL;

CREATE INDEX "idx_outbox_events_aggregate_order"
  ON "outbox_events" ("aggregateId", "occurredAt", "sequence");
```

No se edita la migración inicial. `down()` revierte columnas e índices.

### 4.2 Reclamo y orden

`TypeOrmOutboxRepository.claimBatch(limit, now, lockMs, instanceId)`:

- CTE con `SELECT ... WHERE publishedAt IS NULL AND publishState='PENDING' AND nextAttemptAt <= now
  AND (lockedUntil IS NULL OR lockedUntil <= now)`.
- `NOT EXISTS` de una fila anterior del mismo `aggregateId` que siga `PENDING`, para preservar orden.
- `ORDER BY occurredAt, sequence`.
- `LIMIT n FOR UPDATE OF o SKIP LOCKED`.
- `UPDATE` fija `lockedUntil = now + lockMs` y `lockedBy = instanceId`.
- Devuelve las filas reclamadas ordenadas en JS para publicarlas secuencialmente.

### 4.3 Publicación

`OutboxPublisherService`:

- `onModuleInit`: valida configuración; arranca `setInterval` con `unref()` solo si está habilitado.
- `runOnce()` público para pruebas: evita ejecuciones solapadas con flag `running`.
- Por cada fila: construye envelope, publica, marca `PUBLISHED`.
- En fallo: incrementa `attempts`, calcula `nextAttemptAt` con backoff exponencial + jitter y
  actualiza `lastError`; si supera `maxAttempts`, marca `FAILED`.
- Continúa con las demás filas del lote; un fallo no revierte publicaciones previas.
- `onModuleDestroy`: detiene el intervalo y desconecta el productor.

`KafkaEventPublisher`:

- `Kafka({ brokers, clientId, requestTimeout })`.
- `producer({ idempotent: true, maxInFlightRequests: 1 })`.
- `send({ topic, acks: -1, timeout, messages: [{ key: aggregateId, value: JSON.stringify(envelope) }] })`.
- Conexión lazy y reintentable; un fallo de conexión se propaga para que el publisher programe retry.

### 4.4 Append y validación

`OutboxAppendService.append(manager, input)`:

- Genera `id` y `eventId` (`randomUUID`) si no vienen.
- Valida catálogo, campos obligatorios, `aggregateType = Mentorship`, `aggregateId = payload.assignmentId`
  cuando aplique, UUIDs, fechas ISO y campos prohibidos.
- Persiste con el `EntityManager` recibido para compartir la transacción de negocio.

`TypeOrmLiveRoomRepository.saveOutbox` reutiliza la validación pura antes de guardar, sin cambiar la
firma pública ni el comportamiento de MNT-07.

### 4.5 Configuración

Variables nuevas:

| Variable | Default | Uso |
|---|---|---|
| `KAFKA_ENABLED` | `true` en production, `false` en otros | Activa el publicador. |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Brokers separados por coma. |
| `KAFKA_TOPIC_BOOKING_EVENTS` | `booking-events` | Topic destino. |
| `KAFKA_CLIENT_ID` | `skillbridge-mentorship` | Client ID. |
| `KAFKA_OUTBOX_POLL_INTERVAL_MS` | `1000` | Intervalo de polling. |
| `KAFKA_OUTBOX_BATCH_SIZE` | `50` | Filas por lote. |
| `KAFKA_OUTBOX_MAX_ATTEMPTS` | `10` | Intentos antes de `FAILED`. |
| `KAFKA_OUTBOX_BACKOFF_BASE_MS` | `500` | Base del backoff. |
| `KAFKA_OUTBOX_BACKOFF_MAX_MS` | `60000` | Tope del backoff. |
| `KAFKA_OUTBOX_LOCK_MS` | `30000` | Vigencia del lease de reclamo. |
| `KAFKA_PRODUCER_REQUEST_TIMEOUT_MS` | `10000` | Timeout de envío. |

---

## 5. Pruebas

| Archivo | Tipo | Cubre |
|---|---|---|
| `test/outbox-event-catalog.spec.ts` | Unit | Catálogo, campos obligatorios/prohibidos, `aggregateId`, UUID, fechas. |
| `test/outbox-envelope.spec.ts` | Unit | JSON del envelope, ISO UTC, omisión de opcionales. |
| `test/outbox-publisher.service.spec.ts` | Unit | Publica y marca; fallo reintenta con backoff; `FAILED` al agotar intentos; deshabilitado no-op; orden y continuación. |
| `test/outbox-append.service.spec.ts` | Unit | Append transaccional con manager falso y rechazo de payload inválido. |
| `test/persistence.spec.ts` (ampliación) | Testcontainers | Claim concurrente, metadata, orden por agregado. Requiere Docker. |

Comandos:

```bash
cd BackendNest.js
npm test
npm run lint
npm run build
RUN_DB_TESTS=1 npm run test:db   # solo si Docker está disponible
```

---

## 6. Documentación

- `docs/architecture/kafka.md`: sección de outbox de mentorías y variables.
- `docs/INTEGRATIONS.md`: publicador NestJS y flujo PostgreSQL → outbox → Kafka.
- `BackendNest.js/.env.example`: variables de Kafka.
- `BackendNest.js/README.md`: operación del outbox.
- `docs/adr/0009-mentorship-outbox-publisher.md`: decisión polling + kafkajs.

---

## 7. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Duplicados por at-least-once | `eventId` estable; consumidores deduplican; documentado. |
| Head-of-line blocking por evento inválido | Tras `maxAttempts` pasa a `FAILED` y deja de bloquear. |
| Broker caído al arrancar | Conexión lazy + reintentos; no bloquea la API. |
| Migración sobre tabla existente | Migración aditiva con defaults y backfill de `PUBLISHED`; sin editar la inicial. |
| Fuga de secretos en payload | Catálogo con lista de claves prohibidas y pruebas. |
| Falta de Docker en CI local | Pruebas unitarias completas sin Docker; persistencia se reporta no ejecutada. |
