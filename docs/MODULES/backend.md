# Module Documentation: Core Backend (`backend/`)

The Core Backend is a Spring Boot application structured following Hexagonal Architecture (Ports and Adapters). It serves as the primary system of record, hosting authentication, catalog, booking lifecycle, payments, and multimodal AI services.

---

## 1. Module Overview & Entry Points

- **Root Directory**: `backend/`
- **Main Class Entry Point**: [SkillBridgeApplication.java](../../backend/src/main/java/com/riwi/skillbridge/SkillBridgeApplication.java)
- **Primary Configuration**: [application.yml](../../backend/src/main/resources/application.yml)
- **Runtime Environment**: Java 21 (Temurin), Spring Boot 3.5.6, Maven 3.9+.

---

## 2. Directory Layout & Layer Responsibilities

```text
backend/src/main/java/com/riwi/skillbridge/
├── application/
│   ├── port/
│   │   ├── in/           # Inbound Use Case interfaces (CreateBookingUseCase, etc.)
│   │   ├── out/          # Outbound Port interfaces (BookingRepositoryPort, AiRecommendationPort, etc.)
│   │   └── out/event/    # Event publisher ports and event envelopes (BusinessEvent)
│   ├── service/          # Application orchestrators implementing Inbound Ports
│   └── common/           # Shared application value objects
├── domain/               # Pure Java domain model (Zero framework dependencies)
│   ├── model/            # Core business aggregates (Booking, Offering, Category, UserAccount)
│   ├── policy/           # Business policies (OfferingAccessPolicy, UserManagementPolicy)
│   ├── service/          # Domain services (BookingCancellationPolicy)
│   ├── event/            # Pure domain events (PaymentApproved, PaymentRejected)
│   └── exception/        # Domain exceptions (BusinessRuleException, DomainNotFoundException)
└── infrastructure/       # Framework adapters and configurations
    ├── adapter/
    │   ├── in/
    │   │   ├── rest/     # Controllers, DTOs, Webhooks, Exception Handlers
    │   │   ├── messaging/# Kafka listeners (KafkaAuditConsumer, KafkaAnalyticsConsumer, KafkaPaymentResultConsumer,
    │   │   │             #   KafkaNotificationConsumer, KafkaCorrelationScope, idempotency/)
    │   │   └── startup/  # OfferingSnapshotOnStartup (app.catalog.snapshot-on-startup)
    │   └── out/
    │       ├── persistence/ # Spring Data JPA entities, repositories, and persistence adapters
    │       ├── cache/       # Redis caching adapters (RedisOfferingCacheAdapter)
    │       ├── messaging/   # Kafka publishers (only broker; RabbitMQ retired in HU-104) + KafkaEventHeaders
    │       ├── ai/          # Gemini, OpenAI, Groq STT, ElevenLabs TTS adapters
    │       └── payment/     # Stripe Feign client and simulated payment adapters
    ├── config/           # Spring @Configuration classes (Security, KafkaMessaging, AI, Domain)
    └── security/         # JWT filters, password hasher, cookie handlers, UserDetailsService
```

---

## 3. Important Classes & Components

### 3.1. Inbound Adapters (REST Controllers)
- [BookingController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/BookingController.java): Manages booking creation (`POST /api/bookings`) with idempotency and cancellation (`PATCH /api/bookings/{id}/cancel`).
- [OfferingController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/OfferingController.java): Public catalog query (`GET /api/offerings`), provider offering mutations (`POST`, `PUT`, `activate`, `deactivate`).
- [CategoryController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/CategoryController.java): Category listing and management.
- [AiController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AiController.java): Recommendations via text, voice (`/voice`), image (`/image`), and speech synthesis (`/tts`).
- [PaymentController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/PaymentController.java): Authorizes payments using idempotent payment commands.
- [StripeWebhookController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/webhook/StripeWebhookController.java): Verifies cryptographic Stripe signatures and handles webhook events.
- [AdminUserController.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/AdminUserController.java): Admin-only user role and status modifications (`ACTIVE` / `SUSPENDED`).

### 3.2. Application Services
- [BookingService.java](../../backend/src/main/java/com/riwi/skillbridge/application/service/BookingService.java): Coordinates booking creation, cancellation rules, status updates, and event emission.
- [OfferingCommandService.java](../../backend/src/main/java/com/riwi/skillbridge/application/service/OfferingCommandService.java) & [OfferingQueryService.java](../../backend/src/main/java/com/riwi/skillbridge/application/service/OfferingQueryService.java): Manage offerings and invalidate Redis catalog caches.
- [AiRecommendationService.java](../../backend/src/main/java/com/riwi/skillbridge/application/service/AiRecommendationService.java): Coordinates speech transcription, vision parsing, and catalog-grounded LLM inference.

### 3.3. Outbound Adapters
- [BookingPersistenceAdapter.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/BookingPersistenceAdapter.java): Translates between domain `Booking` and JPA `BookingEntity`.
- [RedisOfferingCacheAdapter.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/cache/RedisOfferingCacheAdapter.java): Serializes/deserializes offering lists in Redis.
- [StripePaymentAdapter.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/payment/stripe/StripePaymentAdapter.java): Calls Stripe API via OpenFeign to create PaymentIntents.
- [KafkaBookingEventPublisher.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaBookingEventPublisher.java): Publishes business events to Kafka topic `booking-events`.
- [KafkaPaymentEventPublisher.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaPaymentEventPublisher.java) / [KafkaNotificationPublisher.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaNotificationPublisher.java): Publish to `payment-events` / `notification-requests` (key `bookingId`, `BusinessEvent` envelope, headers from `KafkaEventHeaders`).
- [ProcessedEventJdbcStore.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/ProcessedEventJdbcStore.java): Idempotency store (`processed_events`, migration `V19__processed_events.sql`).

### 3.4. Messaging (Kafka only, HU-104)
- [KafkaMessagingConfiguration.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/config/KafkaMessagingConfiguration.java): Declares `payment-events`, `notification-requests` and their `.DLT` topics; `DefaultErrorHandler` with 3 retries (1 s, x2) + `DeadLetterPublishingRecoverer`.
- [KafkaPaymentResultConsumer.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/KafkaPaymentResultConsumer.java) and [KafkaNotificationConsumer.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/KafkaNotificationConsumer.java): Idempotent consumers by `eventId`; restore `X-Correlation-Id` into MDC via `KafkaCorrelationScope`.
- `OfferingUpserted` v1 on `booking-events` (key `offeringId`, full offering state) is emitted by `OfferingCommandService`; [OfferingSnapshotOnStartup.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/startup/OfferingSnapshotOnStartup.java) republishes all offerings (active and inactive) when `app.catalog.snapshot-on-startup=true`.
- Contract: [`specs/104-microservices-gateway/contracts/kafka-topics.md`](../../specs/104-microservices-gateway/contracts/kafka-topics.md); operations: [Kafka DLT runbook](../guides/kafka-dlt-runbook.md).

---

## 4. Configuration Keys & Environment Variables

Key settings configured in [backend/src/main/resources/application.yml](../../backend/src/main/resources/application.yml):

| Configuration Key | Env Variable Override | Default Value | Description |
|---|---|---|---|
| `server.port` | `PORT` | `8080` | HTTP port |
| `spring.datasource.url` | `DATABASE_URL` | `jdbc:postgresql://localhost:5432/skillbridge` | PostgreSQL JDBC connection URL |
| `spring.data.redis.url` | `REDIS_URL` | `redis://localhost:6379` | Redis connection URL |
| `spring.kafka.bootstrap-servers` | `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker address (only broker) |
| `app.kafka.topic.payment-events` | `KAFKA_TOPIC_PAYMENT_EVENTS` | `payment-events` | Payment results topic (DLT: `<topic>.DLT`) |
| `app.kafka.topic.notification-requests` | `KAFKA_TOPIC_NOTIFICATION_REQUESTS` | `notification-requests` | Notification requests topic (DLT: `<topic>.DLT`) |
| `app.messaging.kafka.consumer-groups.payments` / `.notifications` | `MESSAGING_KAFKA_GROUP_PAYMENTS` / `MESSAGING_KAFKA_GROUP_NOTIFICATIONS` | `skillbridge-booking-payments` / `skillbridge-notifications` | Consumer groups |
| `app.messaging.kafka.retry.*` | `MESSAGING_KAFKA_RETRY_MAX`, `_INITIAL`, `_MULTIPLIER`, `_MAX_INTERVAL` | `3`, `1000`, `2.0`, `10000` | Retry/backoff before the DLT |
| `app.messaging.kafka.publish-timeout` | `MESSAGING_KAFKA_PUBLISH_TIMEOUT` | `10s` | Max wait for broker ack when publishing |
| `app.catalog.snapshot-on-startup` | `APP_CATALOG_SNAPSHOT_ON_STARTUP` | `false` | Publish one `OfferingUpserted` per offering (active and inactive) at startup |
| `app.jwt.private-key` | `JWT_PRIVATE_KEY` | *(none: required)* | RSA private key, PKCS#8 PEM. JWTs are signed with RS256. The backend does not start without it unless ephemeral keys are enabled |
| `app.jwt.public-key` | `JWT_PUBLIC_KEY` | *(derived from the private key)* | Optional X.509 PEM; must match the private key |
| `app.jwt.key-id` | `JWT_KEY_ID` | `skillbridge-1` | `kid` header of issued tokens |
| `app.jwt.legacy-secret` | `JWT_LEGACY_SECRET` | *(empty)* | Migration only: still **validates** (never issues) old HS256 tokens |
| `app.jwt.generate-ephemeral-keys` | `JWT_GENERATE_EPHEMERAL_KEYS` | `false` | Local development only: in-memory key pair, sessions lost on restart |
| `app.ai.provider` | `AI_PROVIDER` | `gemini` | Primary AI provider (`gemini` or `openai`) |
| `app.payment.provider` | `PAYMENT_PROVIDER` | `stripe` | Payment adapter (`stripe` or `simulated`) |
| `stripe.api-url` | `STRIPE_API_URL` | `https://api.stripe.com` | Base URL for Stripe REST API |

---

## 5. Concrete Files to Inspect Before Modifying

1. **Before modifying any Endpoint/DTO**:
   - Controller: `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/`
   - DTOs: `.../in/rest/dto/`
   - Security Config: `backend/src/main/java/com/riwi/skillbridge/infrastructure/config/SecurityConfiguration.java`
2. **Before modifying Domain Logic or Policies**:
   - Target Aggregate: `backend/src/main/java/com/riwi/skillbridge/domain/model/`
   - Target Policy: `backend/src/main/java/com/riwi/skillbridge/domain/policy/` or `domain/service/`
   - Inbound Port: `backend/src/main/java/com/riwi/skillbridge/application/port/in/`
3. **Before modifying DB Schema or Queries**:
   - Flyway Scripts: `backend/src/main/resources/db/migration/`
   - JPA Entities: `backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/persistence/entity/`
   - Repositories: `.../persistence/repository/`
   - Outbound Port: `backend/src/main/java/com/riwi/skillbridge/application/port/out/`

---

## 6. Testing & Build Instructions

```bash
cd backend

# Execute unit and slice tests
mvn test

# Execute full verification (including Testcontainers integration tests)
# Note: Requires Docker daemon running on the host
mvn verify
```

## 7. Asistente de conocimiento RAG (HU-26)

- **Endpoint:** `POST /api/ai/knowledge/ask` (`KnowledgeController`) → `AskKnowledgeUseCase` (`AskKnowledgeService`).
- **Puertos de salida:** `KnowledgeSearchPort`, `KnowledgeIndexPort`, `EmbeddingPort`, `KnowledgeAnswerPort`, `KnowledgeCorpusSourcePort`.
- **Adaptadores:** `PgVectorKnowledgeAdapter` (búsqueda híbrida BM25 + coseno + RRF sobre `knowledge.knowledge_chunks`), `GeminiEmbeddingAdapter` (`GEMINI_EMBEDDING_API_KEY`, 768 dimensiones), `GeminiKnowledgeAnswerAdapter`, `ClasspathKnowledgeCorpusSource` (`classpath:knowledge/*.md`).
- **Dominio puro:** `domain/knowledge/*`, `domain/service/knowledge/` (`Bm25Scorer`, `CosineSimilarity`, `ReciprocalRankFusion`, `HybridRanker`, `ThresholdRule`).
- **Cableado:** `KnowledgeConfiguration`. Solo se activa con `app.rag.min-score-threshold`; el valor 0.5 es **provisional** (T030). La carga inicial del índice corre en un `ApplicationRunner`; si falla, el backend arranca igual y registra un aviso.
- **Sincronización:** `OfferingCommandService` y `CategoryService` reindexan los fragmentos cambiados (por `contentHash`).
- **Esquema:** migración `V14__knowledge_chunks.sql`; unicidad por `(source_id, language)` porque cada documento genera un fragmento ES y otro EN.
- **Claves:** `app.rag.*` (`RAG_TOP_K`, `RAG_MIN_SCORE_THRESHOLD`, `RAG_TIMEOUT_SECONDS`, `RAG_MAX_CONTEXT_CHARS`, `RAG_RRF_K`).
- **Recomendaciones con RAG (FR-020):** `AiRecommendationService` preselecciona con `KnowledgeSearchPort.searchByType(..., OFFERING)` las `app.rag.recommendation-shortlist-size` (15) ofertas más cercanas antes de llamar al LLM, solo cuando el catálogo activo es mayor. Ante fallo del RAG usa el catálogo completo.
- **Índice diferencial (FR-015/FR-018):** al arrancar, `KnowledgeIndexService.loadInitialIndex` reutiliza los vectores con `contentHash` vigente, embebe solo lo cambiado (lotes de 50) y aplica altas y bajas con `KnowledgeIndexPort.applyChanges` en una transacción. Desactivar una oferta o categoría, o eliminar una categoría, la retira con `deleteBySourceId`.
