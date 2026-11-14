# ai-service

Microservicio de IA de SkillBridge (HU-104 / US5, tareas T040-T044). Atiende recomendaciones por texto, voz e imagen,
texto a voz y la vista administrativa de las cadenas de fallback de proveedores. Funciona separado del monolito para
poder escalar o apagar la IA (costo externo, carga distinta) sin afectar al resto.

Módulo Maven independiente (Java 21, Spring Boot 3.5.6), sin dependencia de compilación con `backend/` (mismo patrón
que `audit-service/backend`). Código copiado y adaptado de `feature/104-microservices-gateway` @ `c02be4d`; el monolito
no se modifica en esta ola.

## Alcance (decisión Q4 / T005)

| Endpoint | Dónde se atiende |
|---|---|
| `POST /api/ai/recommendations`, `/recommendations/voice`, `/recommendations/image` | **ai-service** |
| `POST /api/ai/tts` | **ai-service** |
| `GET /api/admin/ai/providers` (`ROLE_ADMIN`) | **ai-service** |
| `/api/ai/knowledge/**` (RAG, pgvector) | monolito (no se mueve) |
| `GET /api/ai/trending` (analítica de demanda) | monolito (no se mueve) |

Motivo: recomendación, visión, STT y TTS necesitan **una** tabla del monolito (`offerings`, vía
`OfferingRepositoryPort.findAllActive()`); está por debajo del umbral de 2 tablas, así que se sustituye por un
**read model** propio. RAG/knowledge depende de pgvector, del corpus y de los embeddings; trending, de la analítica del
monolito. Ambos se quedan donde están.

- **Preselección RAG desactivada**: sin `KnowledgeSearchPort`, el LLM recibe el catálogo activo completo (igual que el
  constructor de 3 argumentos del monolito). No es una respuesta inventada: es el comportamiento anterior.
- **Actor desde el JWT**: claims `uid` (UUID), `role` y `sub` (email). No hay tabla `users`.
- **Eventos**: `RecommendationRequested` (v1) y `RecommendationGenerated` (v2) se publican en `booking-events` igual que
  en el monolito: clave `aggregateId`, envelope `BusinessEvent` y cabecera `__TypeId__` con la clase del monolito
  (`com.riwi.skillbridge.application.port.out.event.BusinessEvent`), para que `KafkaAnalyticsConsumer` y
  `KafkaAuditConsumer` sigan deserializando. También lleva las cabeceras del contrato: `X-Correlation-Id`, `eventId`,
  `eventType` y `schemaVersion`.
- **`RecommendationGenerated` también en `recommendation-events`** (`KAFKA_TOPIC_RECOMMENDATION_EVENTS`, por defecto
  `recommendation-events`), el topic del analytics/trending del monolito: mismo envelope, mismo `eventId` y mismas
  cabeceras. `RecommendationRequested` solo va a `booking-events`.

## Read model `ai.offering_view`

- Schema propio `ai` y Flyway propio (`src/main/resources/db/migration`, historial en `ai.flyway_schema_history`).
- Se alimenta **solo** con `eventType = "OfferingUpserted"` v1 del topic `booking-events` (clave `offeringId`, envelope
  `BusinessEvent`). Payload: `{id, providerId, title, description, category, price, active, mode, durationHours}`.
  Los demás `eventType` se ignoran.
- Grupo de consumo `ai-service`. Upsert idempotente por `id`: un `eventId` repetido o un `occurredAt` más antiguo no
  cambian la fila. `occurredAt` se acepta en ISO-8601 o como época decimal (lo que escribe por defecto el
  `JsonSerializer` del monolito).
- Errores: `DefaultErrorHandler` + `ExponentialBackOff` (3 reintentos, 1 s, x2) + `DeadLetterPublishingRecoverer` hacia
  **`ai-service.booking-events.DLT`**. Los mensajes mal formados no se reintentan.
- **Dependencia del monolito (pendiente, fuera de WP-C)**: el monolito todavía no publica `OfferingUpserted`. Hasta que
  lo haga (con un backfill de las ofertas existentes), la vista está vacía y las recomendaciones dirán que no hay
  servicios.

## Proveedores y fallback (ADR-0007)

- La cadena de fallback se copia tal cual (`app.ai.fallback.*`, circuit breaker de Resilience4j por `proveedor:modelo`,
  métricas `ai.fallback.*`).
- **Arranca sin claves de proveedor**, a diferencia del monolito, donde `GoogleGenAiChatAutoConfiguration` impide
  arrancar sin `GEMINI_API_KEY`. No se usan los starters ni la autoconfiguración de Spring AI, solo
  `spring-ai-google-genai`, `spring-ai-openai` y `spring-ai-client-chat`. `AiProviderClientsConfiguration` crea el
  `ChatModel` y los adaptadores de un proveedor solo si existe su clave. Un proveedor sin clave no tiene bean y queda
  fuera de la cadena; se registra un aviso, nunca la clave.
- Sin candidatos, o con la cadena agotada → `AiProviderException(UNAVAILABLE)` → **503 `application/problem+json`**
  con `code` y `correlationId`. No hay mocks en producción (constitución, art. V).
- Diferencias con el monolito: cada proveedor tiene su propio `ChatClient`, y OpenAI entra en la cadena si existe
  `OPENAI_API_KEY` (en el monolito quedaba fuera por `@ConditionalOnProperty` y por la exclusión de la
  autoconfiguración). Groq (`AI_AUDIO_PROVIDER=groq` + `GROQ_API_KEY`) y ElevenLabs (`TTS_ENABLED=true` +
  `ELEVENLABS_API_KEY`) conservan las condiciones del monolito.

## Seguridad

- Sin estado. JWT en la cookie `access_token`, con `Authorization: Bearer` como respaldo.
- **Solo valida** tokens, con la clave pública RS256 `JWT_PUBLIC_KEY` (PEM X.509; acepta `\n` literales). ai-service
  nunca emite tokens y no acepta HS256 heredado. Comprueba el emisor `skillbridge`, la expiración y que el `kid`
  coincida con `JWT_KEY_ID` cuando viene. **Sin `JWT_PUBLIC_KEY` el servicio no arranca.**
- CSRF igual que en el monolito: `CookieCsrfTokenRepository` (`XSRF-TOKEN` / `X-XSRF-TOKEN`), exigido en las
  peticiones mutantes autenticadas por cookie; las que llevan Bearer quedan exentas.
- 401 sin credenciales, 403 `ProblemDetail` sin permisos. `/api/admin/ai/providers` exige `ROLE_ADMIN`.
- Revocación: ver la sección siguiente. Un usuario suspendido o con el rol cambiado en el monolito deja de autenticar
  aquí en cuanto llega su `UserAccessChanged`, sin esperar a que expire el token.

## Revocación de acceso (UserAccessChanged)

- Tabla `ai.user_access (user_id PK, status, role, last_event_id, last_event_at)` (Flyway `V2__user_access.sql`),
  alimentada por `eventType = "UserAccessChanged"` v1 del topic `booking-events` (clave `userId`, payload
  `{userId, status: ACTIVE|SUSPENDED, role}`), con el mismo consumidor, group-id `ai-service` y DLT que el read model de
  ofertas. Upsert idempotente: solo aplica si `occurredAt` es igual o posterior al guardado y el `eventId` es distinto.
  Al arrancar, el monolito publica un snapshot con un evento por cada usuario no ACTIVE.
- `JwtAuthenticationFilter`, tras validar la firma, consulta la fila por PK: `SUSPENDED` → 401; rol distinto del claim
  `role` → 401 (hay que volver a iniciar sesión); sin fila → comportamiento anterior (activo, rol del token). Si la
  consulta falla, se deniega (fail-closed, 401).
- Ventana residual: el retraso de entrega del evento en Kafka. Sin caché.

## Variables

| Variable | Obligatoria | Por defecto |
|---|---|---|
| `JWT_PUBLIC_KEY` | **sí** | — |
| `JWT_KEY_ID` | no | `skillbridge-1` |
| `AI_DATABASE_URL` | sí en despliegue | `jdbc:postgresql://localhost:5432/skillbridge?currentSchema=ai` |
| `AI_DATABASE_USER` / `AI_DATABASE_PASSWORD` | sí | `ai_service` / — |
| `KAFKA_BOOTSTRAP_SERVERS` | sí | `localhost:9092` |
| `KAFKA_TOPIC_RECOMMENDATION_EVENTS` | no | `recommendation-events` |
| `GEMINI_API_KEY`, `OPENAI_API_KEY`, `GROQ_API_KEY`, `ELEVENLABS_API_KEY` | no | vacío → proveedor desactivado |
| `AI_PROVIDER`, `AI_AUDIO_PROVIDER`, `TTS_ENABLED`, `AI_*_FALLBACK_CHAIN` | no | igual que en el monolito |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:8088,http://localhost:4200` |
| `PORT` | no | `8082` |

## Cómo correrlo

```bash
# Tests (unitarios + Testcontainers PostgreSQL/Kafka; requiere Docker)
cd ai-service && mvn test

# Sin un JDK local con javac: Maven en contenedor
docker run --rm -u $(id -u):$(id -g) -e HOME=/tmp/h -v $HOME/.m2:/tmp/h/.m2 \
  -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  --add-host=host.docker.internal:host-gateway --group-add $(stat -c %g /var/run/docker.sock) \
  -v "$PWD":/w -w /w maven:3.9-eclipse-temurin-21 mvn -Duser.home=/tmp/h test

# Imagen (JVM afinada: SerialGC, Xss256k, MaxRAMPercentage=70, usuario no root)
docker build -t skillbridge/ai-service ./ai-service
```

Base de datos: el operador crea el schema `ai` y el usuario `ai_service` (ver el SQL en el informe de WP-C o en el
checklist de despliegue). Flyway no crea el schema por defecto (`AI_FLYWAY_CREATE_SCHEMA=false`).

## Reversión

El gateway enruta `/api/ai/**` y `/api/admin/ai/providers/**` hacia `ai-service`, salvo `/api/ai/knowledge/**` y
`/api/ai/trending`. Para revertir basta con devolver la ruta al monolito (T045): el monolito conserva intacto su código
de IA.
