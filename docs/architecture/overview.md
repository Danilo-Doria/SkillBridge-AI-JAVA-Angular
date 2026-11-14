# Architecture Overview — SkillBridge AI

This document establishes the verified architectural foundation of the SkillBridge AI platform, detailing subsystem boundaries, dependency directions, event-driven topologies, and persistence mechanisms.

---

## 1. High-Level Architectural Paradigm

The system implements a **hybrid architecture**:
1. **Core Backend (`backend/`)**: Structured as a **Modular Monolith** applying **Hexagonal Architecture (Ports and Adapters)**.
2. **Audit Subsystem (`audit-service/`)**: Structured as an **Independent Event-Driven Microservice** consuming from Apache Kafka and persisting to MongoDB.
3. **Frontend Applications (`frontend/` & `audit-service/frontend/`)**: Single-Page Applications (SPAs) built with Angular 20 and Tailwind CSS.

```mermaid
flowchart TD
    Gateway["Traefik v3 edge gateway<br/>(optional, profile gateway)"]

    subgraph Clients["Presentation Layer"]
        MainFE["SkillBridge Web (Angular 20)<br/>Port 8088"]
        AuditFE["Audit Admin Web (Angular 20)<br/>Port 8090"]
    end

    subgraph CoreBackend["Core Backend (Spring Boot 3.5.6) — Port 8080"]
        InboundAdapters["Inbound Adapters<br/>(REST Controllers, Kafka Consumers)"]
        InboundPorts["Inbound Ports<br/>(Use Case Interfaces)"]
        AppServices["Application Services<br/>(BookingService, OfferingService, etc.)"]
        DomainCore["Domain Models & Policies<br/>(Booking, Offering, Policies)"]
        OutboundPorts["Outbound Ports<br/>(Repository, Messaging & AI Ports)"]
        OutboundAdapters["Outbound Adapters<br/>(JPA, Redis, Kafka, Stripe, Gemini)"]
    end

    subgraph AuditSubsystem["Audit Microservice (Spring Boot 3.5.6) — Port 8081"]
        KafkaConsumer["KafkaAuditConsumer"]
        AuditRepo["AuditEventRepository"]
        AuditAPI["AuditController"]
    end

    subgraph StorageAndBrokers["Persistence & Middleware"]
        Postgres[(PostgreSQL 17)]
        Redis[(Redis 8 Cache)]
        Mongo[(MongoDB 7)]
        KafkaBroker[["Apache Kafka 7.8<br/>(only broker)"]]
    end

    Gateway -.->|/| MainFE
    Gateway -.->|/api/**| InboundAdapters
    MainFE -->|REST API + Bearer / Cookie| InboundAdapters
    AuditFE -->|REST API| AuditAPI

    InboundAdapters --> InboundPorts
    InboundPorts --> AppServices
    AppServices --> DomainCore
    AppServices --> OutboundPorts
    OutboundPorts --> OutboundAdapters

    OutboundAdapters -->|JPA/SQL| Postgres
    OutboundAdapters -->|Cache-Aside| Redis
    OutboundAdapters -->|Kafka Pub| KafkaBroker

    KafkaBroker -->|payment-events, notification-requests| InboundAdapters
    KafkaBroker -->|Topic: booking-events| KafkaConsumer
    KafkaConsumer --> AuditRepo
    AuditRepo -->|Document Store| Mongo
    AuditAPI --> AuditRepo
```

---

## 2. Hexagonal Architecture in Core Backend (`backend/`)

The core backend strictly enforces the dependency inversion rule. Outer infrastructure details depend on inner application abstractions; the domain depends on nothing.

```text
[ Infrastructure Layer ]
   ├── adapter/in/rest/            <- Controllers (OfferingController, BookingController, etc.)
   ├── adapter/in/messaging/       <- Inbound consumers (KafkaAuditConsumer, KafkaAnalyticsConsumer, KafkaPaymentResultConsumer, KafkaNotificationConsumer)
   ├── adapter/in/startup/         <- OfferingSnapshotOnStartup
   ├── adapter/out/persistence/    <- JPA Adapters (BookingPersistenceAdapter, UserPersistenceAdapter)
   ├── adapter/out/cache/          <- RedisOfferingCacheAdapter
   ├── adapter/out/messaging/      <- KafkaBookingEventPublisher, KafkaPaymentEventPublisher, KafkaNotificationPublisher
   ├── adapter/out/ai/             <- GeminiAiAdapter, OpenAiAiAdapter, ElevenLabsTextToSpeechAdapter
   ├── adapter/out/payment/        <- StripePaymentAdapter, SimulatedPaymentAdapter
   ├── security/                   <- JwtAuthenticationFilter, RestAccessDeniedHandler, JwtService
   └── config/                     <- SecurityConfiguration, KafkaMessagingConfiguration, DomainConfig
           │
           │ implements / invokes
           ▼
[ Application Layer ]
   ├── port/in/                    <- Inbound Use Case interfaces (CreateBookingUseCase, etc.)
   ├── port/out/                   <- Outbound Port interfaces (BookingRepositoryPort, AiRecommendationPort)
   ├── port/out/event/             <- AuditEventPublisherPort, BusinessEvent envelope
   └── service/                    <- Application orchestration (BookingService, AiRecommendationService)
           │
           │ enforces rules / manipulates
           ▼
[ Domain Layer ] (Pure Java)
   ├── model/                      <- Booking, Offering, Category, UserAccount, Actor, BookingStatus
   ├── policy/                     <- OfferingAccessPolicy, UserManagementPolicy
   ├── service/                    <- BookingCancellationPolicy
   ├── event/                      <- PaymentApproved, PaymentRejected
   └── exception/                  <- BusinessRuleException, DomainNotFoundException, etc.
```

### Verified Dependency Invariants:
- `com.riwi.skillbridge.domain.*`: Has zero imports of `org.springframework.*`, `jakarta.persistence.*`, or external libraries. Pure domain logic.
- `com.riwi.skillbridge.application.port.*`: Interfaces defining input and output boundaries. Uses domain models and Java standard types.
- `com.riwi.skillbridge.infrastructure.*`: Contains all framework annotations (`@RestController`, `@Service`, `@Repository`, `@Configuration`).

---

## 3. Communication & Event Flows

### 3.1. Synchronous Request Flow (Booking Example)
```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Controller as BookingController
    participant UseCase as BookingService
    participant RepoPort as BookingRepositoryPort
    participant JPA as BookingPersistenceAdapter
    participant DB as PostgreSQL
    participant KafkaPort as BookingEventPublisherPort
    participant NotifPort as NotificationPublisherPort

    Client->>Controller: POST /api/bookings (Header: Idempotency-Key)
    Controller->>UseCase: create(CreateBookingCommand, actor)
    UseCase->>RepoPort: findByIdempotencyKey(key)
    RepoPort->>JPA: query
    JPA->>DB: SELECT ...
    DB-->>JPA: null
    UseCase->>UseCase: enforce business rules (dates, capacity)
    UseCase->>RepoPort: save(booking)
    RepoPort->>JPA: save(entity)
    JPA->>DB: INSERT INTO bookings ...
    UseCase->>NotifPort: publish(NotificationMessage) → topic notification-requests
    UseCase->>KafkaPort: publishBookingCreated(BookingCreatedPayload)
    UseCase-->>Controller: Booking (domain model)
    Controller-->>Client: 201 Created (BookingResponse)
```

### 3.2. Asynchronous Payments & Notifications (Kafka, HU-104)
Kafka is the only broker; RabbitMQ was retired (ADR-0009). Contract: [`kafka-topics.md`](../../specs/104-microservices-gateway/contracts/kafka-topics.md).
1. **Producers**: [KafkaNotificationPublisher.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaNotificationPublisher.java) → topic `notification-requests`; [KafkaPaymentEventPublisher.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/messaging/KafkaPaymentEventPublisher.java) → topic `payment-events`. Key `bookingId`, envelope `BusinessEvent`, headers `X-Correlation-Id`, `eventId`, `eventType`, `schemaVersion`.
2. **Broker Configuration**: [KafkaMessagingConfiguration.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/config/KafkaMessagingConfiguration.java):
   - Topics `payment-events`, `notification-requests` and `<topic>.DLT`.
   - `DefaultErrorHandler`: 3 retries with exponential backoff (1 s, multiplier 2, max 10 s), then `DeadLetterPublishingRecoverer`; `ErrorHandlingDeserializer` sends unreadable records to the DLT without blocking the partition.
3. **Consumers**: [KafkaPaymentResultConsumer.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/KafkaPaymentResultConsumer.java) (`PaymentResultUseCase`) and [KafkaNotificationConsumer.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/messaging/KafkaNotificationConsumer.java) (`ProcessNotificationUseCase`, emails via [EmailNotificationSender.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/out/notification/EmailNotificationSender.java) to Mailpit). Both are idempotent by `eventId` (`processed_events`, `V19`) and restore MDC via `KafkaCorrelationScope`.

```mermaid
flowchart LR
    BS[BookingService / PaymentReminderService] -->|NotificationPublisherPort| NR[[notification-requests]]
    PA[PaymentAuthorizationService] -->|PaymentEventPublisherPort| PE[[payment-events]]
    NR --> NC[KafkaNotificationConsumer]
    PE --> PC[KafkaPaymentResultConsumer]
    NC -. 3 retries failed .-> NRD[[notification-requests.DLT]]
    PC -. 3 retries failed .-> PED[[payment-events.DLT]]
    NC --> IDEM[(processed_events)]
    PC --> IDEM
```

### 3.3. Persistent Audit Event Streaming (Kafka)
1. **Envelope Structure**: All Kafka messages encapsulate payloads in `BusinessEvent<T>` containing:
   - `eventId` (UUID), `eventType` (String), `aggregateId` (String), `aggregateType` (String), `occurredAt` (Instant), `correlationId` (String), `version` (int), `payload` (T).
2. **Topic Unification Trade-off**:
   - `application.yml` configures:
     - `app.kafka.topic.booking-events: booking-events`
     - `app.kafka.topic.audit-events: booking-events`
   - *Rationale*: [audit-service/backend/src/main/java/com/riwi/audit/consumer/KafkaAuditConsumer.java](../../audit-service/backend/src/main/java/com/riwi/audit/consumer/KafkaAuditConsumer.java) is compiled to listen strictly to `${app.kafka.topic.booking-events}`. Both business events (`BookingCreated`, `BookingCancelled`) and audit events (`UserLoggedIn`, `UserRegistered`, `OfferingCreated`) publish to this topic to ensure full capture.
3. **`OfferingUpserted` v1 (HU-104)**: `OfferingCommandService` also publishes `OfferingUpserted` on `booking-events` (key `offeringId`, full offering state: ECST for the future `ai-service` read model). With `app.catalog.snapshot-on-startup=true`, `OfferingSnapshotOnStartup` republishes every offering (active and inactive) at startup.

---

## 4. Persistence Strategies

| Datastore | Scope | Mechanism | Schema & Migrations |
|---|---|---|---|
| **PostgreSQL 17** | Core Backend | Spring Data JPA / Hibernate (`ddl-auto: validate`) | Flyway migrations `V1__init.sql` through `V19__processed_events.sql` in `backend/src/main/resources/db/migration/`. |
| **Redis 8** | Core Backend | Spring Data Redis (`StringRedisTemplate` / Jackson serialization) | Cache-Aside pattern. Key `catalog::offerings` cached with 10-minute TTL. Evicted upon offering mutations. |
| **MongoDB 7** | Audit Subsystem | Spring Data MongoDB (`MongoTemplate` / `AuditEventRepository`) | Programmatic migrations via [AuditMongoInitializer.java](../../audit-service/backend/src/main/java/com/riwi/audit/config/AuditMongoInitializer.java) (`ApplicationRunner` creating collections and ascending/descending indexes). |

---

## 5. Architectural Constraints & Invariants

1. **Correlation ID Traceability**:
   - Inbound HTTP filter [CorrelationIdFilter.java](../../backend/src/main/java/com/riwi/skillbridge/infrastructure/adapter/in/rest/filter/CorrelationIdFilter.java) captures or generates `X-Correlation-Id`, binding it to SLF4J MDC.
   - The correlation ID is propagated into Kafka message headers (`X-Correlation-Id`) and domain event envelopes.
2. **Idempotent State Changes**:
   - Mutating commands (`POST /api/bookings`, `POST /api/payments/authorize`) mandate the `Idempotency-Key` HTTP header.
   - Database unique index `idx_bookings_idempotency_key` guarantees exactly-once processing even under network retries.
3. **Decoupled Audit Microservice**:
   - `audit-service/` has no shared Maven or npm code with `backend/` or `frontend/`. All communication is strictly asynchronous through Kafka or decoupled HTTP REST queries.
4. **Edge Gateway (HU-104, ADR-0009 Proposed)**:
   - Traefik v3 (`infra/traefik`, Compose profile `gateway`, local ports 8000/8443) routes by prefix (`specs/104-microservices-gateway/contracts/gateway-routes.md`), applies rate limit and circuit breaker, generates `X-Correlation-Id` when missing and serves aggregated Swagger at `/docs` behind basicAuth. It does not authenticate: each service validates the JWT.
