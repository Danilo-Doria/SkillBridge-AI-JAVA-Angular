# Implementation Plan: Gateway de borde, Kafka único y primeros microservicios (HU-104)

**Branch**: `feature/104-microservices-gateway` | **Date**: 2026-11-13 | **Spec**: [spec.md](spec.md) | **ADR**: [0009](../../docs/adr/0009-edge-gateway-kafka-only-native-images.md)

## Summary
Poner Traefik delante de todo (sin cambiar comportamiento), unificar mensajería en Kafka, extraer `ai`, `notification` y `payment` en ese orden, empaquetar en native cuando sea viable y desplegar en una VM Oracle Always Free con Compose. Cada fase es desplegable y reversible por separado.

## Technical Context
- **Backend**: Java 21, Spring Boot 3.5.6, Spring Kafka, Resilience4j 2.2.0 y springdoc 2.8.13 (ya en `backend/pom.xml`; verificado).
- **Gateway**: Traefik v3 (file provider), plugin local Yaegi para correlación.
- **Plataforma**: Oracle Always Free ARM (`aarch64`), Docker Compose, GHCR, GitHub Actions.
- **Datos**: Postgres (pgvector) con un schema+usuario por servicio; Mongo (audit); Redis (cache; ya no es necesario para rate limit).
- **Testing**: JUnit 5/Mockito (unit), Testcontainers (Postgres, Kafka) con Docker, contract tests, k6/hey para borde.
- **Restricciones**: arquitectura hexagonal del dominio puro; sin dependencias de compilación entre `backend/` y `audit-service/`; sin PAN/CVV; truthful verification.

## Constitution Check (`.specify/memory/constitution.md`)
Verificar al inicio de T001 que se cumplen: pureza hexagonal, aislamiento de puertos de IA (`AiRecommendationPort` etc. se mueven con `ai-service` sin que el dominio importe frameworks), cero PAN/CVV, trazabilidad por correlación, verificación veraz. **[UNKNOWN]** el texto exacto de la constitución no se releyó al escribir este plan: Opus debe leerla primero.

## Arquitectura objetivo

```
Internet ─▶ Traefik :80/:443 (TLS ACME, rateLimit, circuitBreaker, correlation plugin, strip X-User-*)
              │
   /            ─▶ frontend (nginx estático)
   /docs        ─▶ swagger-ui (basicAuth)
   /api/ai/**   ─▶ ai-service        ──┐
   /api/payments,/api/webhooks ─▶ payment-service ─┤
   /api/** (resto) ─▶ backend (monolito: identity+catalog+booking+mentorías)
   /audit       ─▶ audit-frontend/backend          │
                                                    ▼
                    Kafka (KRaft, 1 nodo): booking-events · payment-events · notification-requests (+DLT)
                         ▲            ▲                    ▲
                  notification-service            audit-service ─▶ Mongo
        Postgres: schemas backend / ai / payment  ·  (mentorship-service NestJS: su propia BD, ADR-0006)
```

## Presupuesto de RAM objetivo (estimación **[INFERRED]**, a medir en T075)

| Componente | Objetivo |
|---|---|
| Traefik | ~100 MB |
| Backend monolito (native, o JVM afinada ≤ 384 MB) | 150-384 MB |
| `ai-service`, `payment-service`, `notification-service` (native) | 3 × 100-200 MB |
| `audit-backend` (native/JVM afinada) | 150-300 MB |
| Kafka (heap 512 MB) | ~800 MB |
| Postgres + Redis + Mongo | ~1,2 GB |
| Frontends (nginx) + swagger-ui | ~80 MB |
| Prometheus + Grafana (opcional, limitar) | ~500 MB |
| **Total** | **~3,5-5 GB** (límite de la feature: 8 GB) |

## Fases

Cada fase termina con: colección de humo verde, `mvn test`/`npm test` reportados, evidencia en `progress.md`, despliegue reversible.

| Fase | Contenido | Riesgo | Reversión |
|---|---|---|---|
| **0 Preparación** | Resolver Q1-Q7, leer constitución, contract tests de humo, baseline de RAM/latencia, *spike* native, issue de coordinación con el equipo NestJS | Bajo | n/a |
| **1 Gateway** | Traefik delante del monolito + audit + frontend; TLS; rate limit; breaker; correlación; Swagger agregado | Bajo | Reapuntar DNS/puertos al frontend previo |
| **2 Kafka único** | Migrar `payment` y `notification` de RabbitMQ a Kafka dentro del monolito; idempotencia + DLT; quitar RabbitMQ | **Medio-alto** (mensajería de pagos) | **Ejecutado distinto (defecto Q3)**: RabbitMQ se retiró en esta misma iteración (T038) tras verificar el flujo con Kafka; la reversión de F2 es `git revert` del commit de retirada, no un flag. Contrapartida aceptada de FR-017 para esta fase |
| **3 `ai-service`** | Extraer puertos IA a `ai-service`; read model por eventos; ruta en el gateway | Medio (Q4) | Cambiar la ruta de `/api/ai/**` de vuelta al monolito |
| **4 `notification-service`** | Mover consumidor y plantillas; sin HTTP | Bajo | Reactivar consumidor del monolito |
| **5 `payment-service`** | Mover Stripe, webhook y autorización de pagos; propiedad de reserva por evento/API | **Alto** (dinero) | Ruta de `/api/payments`,`/api/webhooks` al monolito |
| **6 Native + CI/CD + operación** | Perfiles native por servicio, CI ARM, guía Oracle, backups, keep-alive, medición | Medio | Imagen JVM del mismo servicio |
| **Compuerta** | Decidir si se extraen identity/catalog/booking (requiere #103, ADR nuevo) | — | — |

Orden estricto: 0 → 1 → 2 → {3, 4} → 5 → 6. Las fases 3 y 4 son independientes entre sí y pueden ir en paralelo en worktrees separados **solo** si ambas tocan archivos distintos (ver tasks `[P]`).

## Estructura propuesta de archivos

```text
infra/
├── traefik/
│   ├── traefik.yml                 # estáticos: entrypoints, ACME, providers, métricas
│   ├── dynamic/
│   │   ├── routers.yml             # routers + services
│   │   ├── middlewares.yml         # rateLimit, circuitBreaker, retry, headers, basicAuth
│   │   └── tls.yml
│   └── plugins-local/src/github.com/skillbridge/correlationid/   # Yaegi: .traefik.yml, correlationid.go, go.mod
├── swagger-ui/                     # config de URLS
├── oracle/                         # cloud-init, firewall (OCI security list + iptables), scripts de keep-alive y backup
docker-compose.yml                  # dev: sin ACME, Mailpit, puertos locales
docker-compose.prod.yml             # prod: imágenes GHCR arm64, límites de memoria, sin puertos de BD
ai-service/ · payment-service/ · notification-service/   # nuevos módulos Maven independientes (patrón de audit-service/)
.github/workflows/                  # build-native-arm64.yml, deploy-oracle.yml
docs/guides/oracle-free-deployment.md
docs/adr/0009-...md
specs/104-microservices-gateway/
```

Decisión de estructura **[INFERRED]**: cada servicio nuevo es un módulo Maven independiente (como `audit-service/`), no un submódulo del backend, para respetar "sin dependencias de compilación entre módulos". El código compartido (envelope `BusinessEvent`, filtro de correlación) **no** se comparte por librería interna al inicio: se copia el contrato y se cubre con contract tests; una librería compartida versionada se decide en la compuerta.

## Estrategia de pruebas
- **Contract tests**: respuestas del monolito vs. servicio extraído (mismo JSON/estado) antes de mover la ruta.
- **Mensajería**: Testcontainers Kafka; casos de duplicado, orden, DLT y reintento.
- **Borde**: script k6/hey reproducible (429, circuito, correlación, spoof de `X-User-*`).
- **Native**: la suite de contrato/humo contra la imagen native, no solo `mvn test` en JVM.
- **Regresión**: `mvn test`, `mvn verify`, `npm test -- --watch=false`, `mvn -o validate`; reportar "No ejecutado en esta sesión" si no se corre.

## Riesgos principales
Ver spec (Edge Cases/Q1-Q7) y ADR-0009. Los de mayor impacto: contrato de mentorías con RabbitMQ (Q3), extracción de IA sin su base (Q4), reflexión en native (Stripe SDK/Jackson/Resilience4j), IP real detrás de proxy (rate limit), una sola VM.

## Fuera de plan
No commitear ni abrir PR sin petición del usuario. No tocar `specs/0*` ajenos ni `docs/audit/findings.md`. No tocar secretos ni `.env`.
