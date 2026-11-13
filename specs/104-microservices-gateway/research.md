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
| R8 | `confluentinc/cp-kafka:7.8.0` es multi-arch | [UNKNOWN] | `docker buildx imagetools inspect` (T003) |
| R9 | Stripe Java SDK, Resilience4j (AOP) y springdoc necesitan hints en native | [UNKNOWN] | Spike T009 |

## Decisiones de diseño con alternativa
- **Auth en el borde**: no se usa ForwardAuth; cada servicio valida JWT (ADR-0006 regla 6). Reabrir solo si un servicio no puede validar.
- **IP real**: si hay Cloudflare/proxy delante, configurar `trustedIPs`; si se expone directo a Internet con DNS-only, `ipStrategy` por defecto sirve.
- **Redpanda**: solo plan B si Kafka no cabe; no es la decisión actual.

## Registro de resultados del spike native (T009)
| Servicio | Resultado | Problemas | Hints añadidos | Decisión (native / JVM afinada) |
|---|---|---|---|---|
| backend | | | | |
| audit-backend | | | | |
| ai-service | | | | |
| payment-service | | | | |
| notification-service | | | | |

## Q1-Q7 (resolver en Fase 0)
Ver tabla en `spec.md`. Registrar aquí la respuesta, fecha y quién la dio.
