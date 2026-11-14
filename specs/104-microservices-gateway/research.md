# Research: HU-104 (gateway, Kafka único, native, Oracle Free)

Estado: **plantilla de investigación**. Rellenar durante la Fase 0. Marcar cada hallazgo `[VERIFIED]`, `[INFERRED]` o `[UNKNOWN]` (AGENTS.md §2).

## Hechos ya verificados en el repo (2026-11-13)
- [VERIFIED] `backend/pom.xml` ya incluye springdoc 2.8.13 y Resilience4j 2.2.0 (+micrometer).
- [VERIFIED] `frontend/nginx.conf.template` hace `proxy_pass ${BACKEND_URL}/api/` a un único backend.
- [VERIFIED] RabbitMQ se usa en `RabbitConfiguration`, `NotificationConsumer`, `PaymentResultConsumer`, `RabbitPaymentEventPublisher`, `RabbitNotificationPublisher`.
- [VERIFIED] Prefijos REST: `/api/auth`, `/api/offerings`, `/api/categories`, `/api/bookings`, `/api/payments`, `/api/webhooks`, `/api/ai`, `/api/ai/knowledge`, `/api/admin/ai/providers`, `/api/admin/*`, `/api/provider-requests`, `/api/mentorships`, `/api/mentorship-resources`.
- [VERIFIED] `CorrelationIdFilter` existe en `adapter/in/rest/filter/`.
- [VERIFIED] Issue #103 abierto: deuda de arquitectura (controladores con JPA directo, `application` → `infrastructure`, `target/` versionado en audit-service).
- [VERIFIED] La VM de desarrollo actual tiene 15 GB; ese dato **no** es el de Oracle.

## Afirmaciones sobre tecnología a **verificar** (no asumir)
| # | Afirmación | Estado | Cómo verificar |
|---|---|---|---|
| R1 | Traefik OSS `rateLimit` es en memoria por instancia (sin Redis) | [INFERRED] | Docs oficiales de Traefik v3, middleware RateLimit |
| R2 | Traefik OSS `circuitBreaker` soporta `NetworkErrorRatio`/`ResponseCodeRatio`/`LatencyAtQuantileMS` | [INFERRED] | Docs oficiales |
| R3 | Plugins **locales** Yaegi se cargan desde `plugins-local/src/<modulePath>/` sin red | [INFERRED] | Docs "Local Plugins"; probar en T013 |
| R4 | Traefik no agrega OpenAPI; basta `swagger-ui` con `URLS` | [INFERRED] | Probar en T014 |
| R5 | Oracle Always Free A1: hasta 4 OCPU/24 GB; riesgo de reclamación por inactividad y de falta de capacidad al crear la VM | [INFERRED] | Docs de Oracle vigentes a la fecha; el usuario confirma su cuenta |
| R6 | Runners `ubuntu-24.04-arm` gratis solo en repos públicos | [INFERRED] | Docs de GitHub Actions; ver Q2 |
| R7 | Spring Boot 3.5 soporta native con `native-maven-plugin`/buildpacks en arm64 | [INFERRED] | Spike T009 |
| R8 | `confluentinc/cp-kafka:7.8.0` es multi-arch | **[VERIFIED] sí** (T003, 2026-11-13) | `docker buildx imagetools inspect` |
| R9 | Stripe Java SDK, Resilience4j (AOP) y springdoc necesitan hints en native | [UNKNOWN] | Spike T009 |

## Decisiones de diseño con alternativa
- **Auth en el borde**: no se usa ForwardAuth; cada servicio valida JWT (ADR-0006 regla 6). Reabrir solo si un servicio no puede validar.
- **IP real**: si hay Cloudflare/proxy delante, configurar `trustedIPs`; si se expone directo a Internet con DNS-only, `ipStrategy` por defecto sirve.
- **Redpanda**: solo plan B si Kafka no cabe; no es la decisión actual.

## Registro de resultados del spike native (T009)
| Servicio | Resultado | Problemas | Hints añadidos | Decisión (native / JVM afinada) |
|---|---|---|---|---|
| backend | No ejecutado en esta sesión (perfil `native` presente en pom.xml) | — | Ninguno | JVM afinada hasta probar (Stripe, Resilience4j AOP, springdoc pendientes: R9) |
| audit-backend | No ejecutado en esta sesión (perfil `native` presente en pom.xml) | — | Ninguno | JVM afinada hasta probar |
| ai-service | No ejecutado en esta sesión (perfil `native` presente en pom.xml) | — | Ninguno | JVM afinada hasta probar |
| payment-service | No ejecutado en esta sesión (perfil `native` presente en pom.xml) | — | Ninguno | JVM afinada hasta probar (Stripe SDK: R9) |
| notification-service | **OK** (2026-11-13, WP-F): binario construido y arrancado contra Postgres 16 + Kafka 7.8 efímeros; `Started ... in 0.237 s`, `/actuator/health` UP, Flyway V1 aplicada, consumidor unido al grupo. RSS 165 MB (+15 s y +45 s) vs JVM 234 MB / 2.66 s | WARN benigno de Flyway `Unable to scan location: /db/migration (unsupported protocol: resource)` (la migración se aplica igual); WARN Micrometer sin GC MXBeans en native. Build en x86_64, no arm64 | Ninguno (bastan AOT + metadata del repositorio GraalVM) | native viable; imagen por defecto sigue JVM hasta construir arm64 en CI (`build-arm64.yml`, input `native`) y probar humo |

## Q1-Q7 (Fase 0, 2026-11-13, orquestador; defectos de `spec.md` aplicados sin preguntar)

- **Q1 dominio** [UNKNOWN] → defecto: variable `DOMAIN`, ACME HTTP-01. Pendiente en `HUMAN-TODO.md`.
- **Q2 repo público** [VERIFIED] → runners `ubuntu-24.04-arm` para native en CI.
- **Q3 NestJS/Kafka** [UNKNOWN] → defecto: se retira RabbitMQ en este repo; borrador de issue ya en `HUMAN-TODO.md` (T004).
- **Q5 arm64** [VERIFIED] (T003) → todas las imágenes del stack publican `linux/arm64`; se mantiene `confluentinc/cp-kafka:7.8.0`:

| Imagen | amd64 | arm64 |
|---|---|---|
| pgvector/pgvector:pg17, redis:8-alpine, confluentinc/cp-kafka:7.8.0, apache/kafka:3.9.1, mongo:7 | ✓ | ✓ |
| prom/prometheus:v3.5.0, grafana/grafana:12.1.1, axllent/mailpit, traefik:v3.5, swaggerapi/swagger-ui:v5.29.0 | ✓ | ✓ |
| nginx:alpine, eclipse-temurin:21-jre-alpine, ghcr.io/graalvm/native-image-community:21 | ✓ | ✓ |

- **Q6 #103** pausado → fuera de alcance.

### Q4 / T005 — acoplamiento de IA [VERIFIED por grep, 2026-11-13]

| Endpoint | Clase | Dependencias de datos del monolito |
|---|---|---|
| `POST /api/ai/recommendations{,/voice,/image}` | `AiRecommendationService` | `OfferingRepositoryPort.findAllActive()` (tabla `offerings`); `KnowledgeSearchPort` **opcional** (`@Autowired(required=false)`, solo preselección RAG); `AuditEventPublisherPort` (Kafka) |
| `POST /api/ai/tts` | `TextToSpeechService` | ninguna |
| `GET /api/ai/trending` | `DemandAnalyticsService` | `OfferingRepositoryPort` + `RecommendationAnalyticsRepositoryPort` (tablas de analítica, V17) |
| `/api/ai/knowledge/**` | `AskKnowledgeService` | pgvector (`PgVectorKnowledgeAdapter`) |
| `/api/admin/ai/providers/**` | `AiAdminController` | configuración de proveedores (sin tablas de negocio) [INFERRED] |
| Todos | `CurrentActorResolver` | tabla `users` (`findIdByEmail`) — **evitable**: el JWT ya trae el claim `uid` (`JwtService.generate`) |

**Decisión (regla Q4: ≤ 2 tablas → read model por eventos)**:
- `ai-service` atiende `/api/ai/recommendations/**`, `/api/ai/tts` y `/api/admin/ai/providers/**`.
- **Permanecen en el monolito** (rutas de mayor prioridad que `/api/ai/**`): `/api/ai/knowledge/**` (pgvector) y `/api/ai/trending` (analítica). En `ai-service` la preselección RAG queda desactivada (se envía el catálogo completo, comportamiento ya soportado y válido).
- Read model `ai.offering_view` alimentado por un nuevo `eventType` **`OfferingUpserted` v1** (estado completo de `Offering`: id, providerId, title, description, category, price, active, mode, durationHours) publicado en `booking-events` con clave `offeringId` por `OfferingCommandService` en create/update/activate/deactivate. Arranque en frío: el monolito publica un snapshot de las ofertas (activas e inactivas) al iniciar si `app.catalog.snapshot-on-startup=true`.
- El actor se obtiene del JWT (`uid`, `role`), sin consultar `users`.
- Hoy los eventos `Offering*` de auditoría **no** llevan el estado (solo ids) → cambio mínimo en el monolito (orquestador, W2).

### Q7 / T006 — cookies y origen único [VERIFIED por lectura, 2026-11-13]
- `AuthCookieService`: `SameSite` y `Secure` configurables (`JWT_COOKIE_SAME_SITE`, defecto `None`; `JWT_COOKIE_SECURE`, defecto `true`); sin atributo `Domain` → la cookie es host-only y sirve tal cual con origen único.
- `SecurityConfiguration`: cookie CSRF fija en `SameSite=None; Secure`; CORS por `CORS_ALLOWED_ORIGINS`.
- **Cambios mínimos: ninguno en código.** En prod: `CORS_ALLOWED_ORIGINS=https://${DOMAIN}` y `JWT_COOKIE_SAME_SITE=Lax` (o `Strict`, como pide la constitución, art. III). En local el gateway sirve en `http://localhost`, que los navegadores tratan como contexto seguro, así que las cookies `Secure` siguen funcionando [INFERRED].

### T009 — spike native
**No ejecutado en esta sesión** en W0: no hay GraalVM local (`native-image` ausente). Se hace en W5 (WP-F) con `ghcr.io/graalvm/native-image-community:21` (arm64/amd64 verificado) o en CI ARM.

**Ejecutado en W5 (WP-F, 2026-11-13), solo notification-service** — host x86_64, 12 CPU, 15 GB RAM:

```bash
docker run --rm --user $(id -u):$(id -g) -e HOME=/work -v <copia-del-módulo>:/work/app \
  -v <apache-maven-3.9.9>:/opt/maven:ro -v <repo-maven-temporal>:/work/.m2 -w /work/app \
  --entrypoint /opt/maven/bin/mvn ghcr.io/graalvm/native-image-community:21 \
  -B -Pnative -DskipTests package native:compile
```

- GraalVM CE 21.0.2 (native-image 21.0.2), native-maven-plugin 0.10.6 (gestionado por spring-boot-dependencies 3.5.6).
- `Finished generating 'notification-service' in 2m 50s` · Peak RSS del build 7.19 GB · `BUILD SUCCESS` en 03:45 min · binario ELF x86-64 de 134 MB.
- Arranque: `Started NotificationServiceApplication in 0.237 seconds`; RSS en reposo 168 784 kB (+15 s) / 168 260 kB (+45 s).
- Referencia JVM (mismo jar, `-XX:+UseSerialGC -Xss256k -XX:TieredStopAtLevel=1`, sin límite de memoria de contenedor): `Started ... in 2.658 seconds`, RSS 239 720 kB (+15 s). [INFERRED] con `mem_limit` la JVM quedaría algo por debajo.
- `mvn -o test` del módulo (JVM): 22 tests, 0 fallos, BUILD SUCCESS.
- No probado: build arm64 (se hará en `ubuntu-24.04-arm`), envío SMTP real en native, flujo DLT en native. Resto de módulos: No ejecutado en esta sesión.
