# Progress: HU-104

Registro de evidencia. **Solo marcar tareas hechas con salida real de comandos.** Si algo no se ejecutó: "No ejecutado en esta sesión".

| Fecha | Tarea | Resultado | Evidencia (comando/salida resumida) |
|---|---|---|---|
| 2026-11-13 | Spec, plan, tasks, ADR-0009 redactados | Hecho | Archivos creados; **no** se ejecutaron tests ni se levantó infraestructura |
| 2026-11-13 | Rama + commit de docs SDD (autorizado por el usuario) | Hecho | `git switch -c feature/104-microservices-gateway`; commit `c02be4d` |
| 2026-11-13 | T001 constitución | Hecho | Leída `.specify/memory/constitution.md` v1.1.0; spec/plan no la violan. Nota: art. III pide `SameSite=Strict` y hoy el defecto es `None` (preexistente, se corrige por variable en prod) |
| 2026-11-13 | T002/T003/T005/T006 | Hecho | Ver `research.md` §Q1-Q7. T003: `docker buildx imagetools inspect` → 13/13 imágenes con `linux/arm64` |
| 2026-11-13 | T009 spike native | **No ejecutado en esta sesión** | Sin GraalVM local; pasa a W5/WP-F |
| 2026-11-13 | T007 baseline | Hecho (portátil) | Ver sección Baseline |
| 2026-11-13 | Incidencia: worktrees de subagentes creados desde `0ad4a88` (main viejo), no desde la rama de feature | Corregido | WP-B alineó con `git restore --source=feature/104...`; WP-C/WP-D avisados. Patches revisados contra `c02be4d` antes de integrar |
| 2026-11-13 | Incidencia: `/usr/lib/jvm/java-21-openjdk-amd64` no trae `javac` | Rodeado | Maven con `JAVA_HOME=~/.jdks/azul-21.0.12.1` o contenedor `maven:3.9-eclipse-temurin-21` |
| 2026-11-13 | **W1 WP-A** gateway (T010-T015, T018, T021) integrado | Hecho | Re-ejecutado por el orquestador: `go vet/gofmt/go test` del plugin (golang:1.23-alpine) ok; `shellcheck` 0 errores en 6 scripts |
| 2026-11-13 | **W1 WP-B** Kafka (T030-T036) integrado + `OfferingUpserted` (orquestador) | Hecho | `mvn test`: 684 testcases, 0 fallos/errores, 1 omitido; `mvn verify -Dit.test=KafkaMessagingFlowIT,BookingCancellationIT`: 5/5 y 8/8 |
| 2026-11-13 | **W2 T038** retirar RabbitMQ (código, `pom.xml`, `application.yml`, Compose) | Hecho | Sin `spring-boot-starter-amqp`/`rabbitmq`; `BookingCancellationIT` migrado a Kafka. `mvn verify`: 678 unit (−6 tests de Rabbit borrados), 0 fallos; IT 13/13 sin RabbitMQ |
| 2026-11-13 | T039 Kafka RAM (heap 512 MB, retención 72 h, `mem_limit` 768m) | Hecho en Compose | Medición en sección Mediciones |
| 2026-11-13 | **T019 / SC-001** humo gateway vs directo (stack real, sin RabbitMQ) | **Verde** | `smoke.sh` vía `https://localhost:8443`: 9/9 PASS (incluye login cookie, reserva con `X-XSRF-TOKEN`, auditoría); directo `:18080`: 7/7 PASS + 1 SKIP (audit no aplica). `/api/ai` 503 controlado (sin clave real de IA) |
| 2026-11-13 | **SC-004** borde | **Verde** | `edge-checks.sh` contra el stack real: 11/11 PASS (correlación generada/conservada/regenerada, HSTS, spoof `X-User-*`, 404 problem+json, 301→HTTPS, webhook sin basicAuth, 429 `auth` y `ai` con `Retry-After`, breaker 503 al detener el backend) |
| 2026-11-13 | **SC-005** correlación | **Verde** | `X-Correlation-Id` enviado en `POST /api/bookings` vía gateway: aparece en access log de Traefik, en MDC de `KafkaNotificationConsumer`/`KafkaAuditConsumer` del backend y en `audit-backend` |
| 2026-11-13 | SC-002 flujo completo sin RabbitMQ | **Parcial** | Reserva → `notification-requests` → consumo verificado en stack real; pago aprobado → reserva CONFIRMED verificado en `KafkaMessagingFlowIT` (Testcontainers). Webhook Stripe real y aprovisionamiento de mentorías (NestJS, otro repo) **no ejecutados en esta sesión** |

## Baseline (T007) — [VERIFIED] portátil x86_64, 12 CPU, 15 GiB, 2026-11-13
Stack actual (`docker compose up --build -d`, proyecto `skillbridge-baseline`, override de puertos en el scratchpad porque otro proyecto ocupa 3000/5433/8080/8081/9090; `GEMINI_API_KEY` ficticia porque el backend no arranca sin ella, ver nota). 2 min de espera + 10 muestras cada 30 s con tráfico ligero. Prometheus/Grafana no forman parte del arranque por defecto.

| Contenedor | RAM última (MiB) | Pico (MiB) |
|---|---|---|
| backend (JVM) | 654.8 | 700.0 |
| kafka | 398.2 | 403.5 |
| audit-backend (JVM) | 205.4 | 225.4 |
| audit-mongo | 167.1 | 167.2 |
| rabbitmq | 88.5 | 111.1 |
| postgres | 40.6 | 54.9 |
| frontend, audit-frontend, mailpit, redis | 11.3 / 10.6 / 11.2 / 7.2 | — |
| **TOTAL** | **1594.8** | **1704.6** |

Latencia (300 peticiones, concurrencia 5, curl):

| Endpoint | p50 | p95 | p99 | Códigos |
|---|---|---|---|---|
| `GET /api/offerings` directo (:8080) | 5.6 | 12.1 | 23.3 ms | 300×200 |
| `GET /api/offerings` vía nginx del frontend | 3.8 | 5.1 | 5.7 ms | 300×200 |
| `GET /actuator/health` directo | 4.7 | 6.5 | 7.1 ms | 300×200 |
| `GET /api/categories` directo | 1.2 | 1.9 | 2.3 ms | 300×**401** (requiere auth; no es comparable) |

Hallazgos:
- RabbitMQ en reposo ocupa ~90-110 MiB, **no** los 300-500 MB estimados en la spec (US4). El ahorro real de retirarlo es menor; la justificación principal pasa a ser tener menos piezas.
- **El backend no arranca sin `GEMINI_API_KEY`** (`GoogleGenAiChatAutoConfiguration`: "Incomplete Google GenAI configuration"). No existe perfil `mock-ai`. Es un riesgo para el humo/CI y para `ai-service`.

## Decisiones aprobadas por el humano
_pendiente_ (ADR-0009 sigue en **Proposed**)

## Mediciones (rellenar con `measure/measure.sh`; ver `measure/README.md`)
| Fecha | Entorno | Qué | Comando | Resultado | Meta | CSV |
|---|---|---|---|---|---|---|
| _pendiente_ | portátil | RAM baseline (stack actual) | `ram baseline 10` | | informativo | |
| 2026-11-13 | portátil | RAM sin RabbitMQ (F2) + Traefik + swagger-ui | 10 muestras/30 s tras 2 min | **1551.7 MiB** (pico 1617.1); baseline 1594.8 | menor que baseline ✓ | scratchpad |
| 2026-11-13 | portátil | Sobrecarga gateway p95 (TLS incl.), `GET /api/offerings`, 3×300 req c=5, RL relajado solo para medir | curl | directo p95 5.5/4.4/4.0; gateway 8.3/8.0/11.4 → overhead mediana **3.6 ms** | < 20 ms ✓ (Oracle pendiente) | scratchpad |
| _pendiente_ | Oracle | RAM total final | `ram final 10` | | ≤ 8192 MiB | |
| _pendiente_ | Oracle | RAM por servicio native | `ram final 10` | | ≤ 200 MiB c/u | |
| _pendiente_ | Oracle | Sobrecarga del gateway p95 | `latency` + `compare` | | < 20 ms | |
| _pendiente_ | Oracle | Arranque native | `startup` | | < 3 s | |

## Continuación (2026-11-13)
| Fecha | Tarea | Resultado | Evidencia |
|---|---|---|---|
| 2026-11-13 | Disco lleno (0 MB) durante builds | Resuelto con permiso del usuario | `docker builder prune` (4.2 GB), stack baseline eliminado (`--rmi local`), 5 worktrees de agentes integrados eliminados, `target/` borrados → 13 GB libres. WP-E detenido y relanzado |
| 2026-11-13 | **T045 cutover IA en vivo** (perfiles `gateway`+`services`, `routers.cutover.yml` activado, `CUTOVER_PAYMENTS=false`, par RS256 de desarrollo compartido) | **Verde** | read model `ai.offering_view` 20/20 desde el snapshot; `POST /api/ai/recommendations` vía gateway atendido por `ai-service` (cookie RS256 + XSRF) → 503 ProblemDetail controlado (sin claves de proveedor, sin éxito simulado); `/api/ai/trending` → monolito (200); 401 sin token; 403 admin como CUSTOMER; 403 POST sin XSRF; correlación igual en cabecera, cuerpo y MDC (3/3) |
| 2026-11-13 | **T050/T051 notification-service en vivo** (consumidor local del monolito apagado) | **Verde** | reserva vía gateway → `notification-requests` → `notification-service` → Mailpit recibe "Reserva creada" para `user@gmail.com`; 1 fila en `notification.processed_events`; 0 consumos del monolito |
| 2026-11-13 | RAM stack completo JVM (13 contenedores, incl. ai/notification) | Medido (portátil, 3 muestras) | **2081 MiB** total; ai-service 323.8 (límite 384m, justo), notification-service 162.6, backend 671.9, kafka 428.3 |
| 2026-11-13 | Incidencia: borré `audit-service/backend/target/` y está **versionado** (#103) | Restaurado | `git restore --worktree -- audit-service/backend/target`; `git status audit-service` vacío |
| 2026-11-13 | SC-007 audit-service | **Verde** | `cd audit-service/backend && mvn test`: 7 testcases, 0 fallos |
| 2026-11-13 | T022 `docker-compose.prod.yml` | Hecho (sin desplegar) | `config` validado: solo Traefik publica 80/443; 13/13 servicios con `mem_limit` y healthcheck; secretos obligatorios con `:?` |
| 2026-11-13 | **W4 WP-E payment-service** integrado | Hecho | `mvn test`: 36 testcases verdes (re-ejecutados); dominio puro; grep de datos de tarjeta en `src/main` vacío. Monolito: `BookingCreatedPayload.customerEmail` (aditivo); backend 681 verdes |
| 2026-11-13 | **SC-002 / T063 pagos en vivo** (cutover `CUTOVER_PAYMENTS=true`, Stripe con claves de marcador y API inalcanzable, sin Stripe real) | **Verde** | Autorización (red interna): no dueño 404, reserva sin sincronizar 409, importe distinto 422, pasarela caída 503. Webhook `payment_intent.succeeded` firmado vía gateway → 200; duplicado → 200 sin efecto (1 mensaje en `payment-events`, 1 fila `processed_stripe_event`); firma mala → 400. **Reserva CREATED → CONFIRMED en el monolito** con correlación propagada, sin RabbitMQ |
| 2026-11-13 | Hallazgo: el breaker contaba 503 intencionales y tapaba respuestas 4xx | Corregido | Expresión `NetworkErrorRatio() > 0.30 \|\| ResponseCodeRatio(500, 503, 0, 600) > 0.30 \|\| ResponseCodeRatio(504, 600, 0, 600) > 0.30`. Re-verificado: 12 peticiones con 503/409 pasan (sin breaker); con el backend detenido el breaker abre (PASS); `edge-checks.sh` 10/10 |
| 2026-11-13 | SC-007 frontend | **Verde** | `npm ci && npm test -- --watch=false --browsers=ChromeHeadless`: 60/60 SUCCESS (frontend sin cambios en esta HU) |
| 2026-11-13 | **W5 WP-F** perfiles native (5 módulos) + `build-arm64.yml` + `deploy-oracle.yml` | Hecho (sin ejecutar en GitHub) | Re-verificado por el orquestador: `actionlint` exit 0; `mvn -o validate` exit 0 en los 5 módulos; sin secretos en claro. Rollback de deploy probado por WP-F con `docker` simulado |
| 2026-11-13 | **T009/T070 spike native notification-service** | **Viable** | GraalVM CE 21.0.2 (contenedor), build 3:45 min (pico 7.19 GB RAM), binario 134 MB; arranque **0.237 s**, RAM en reposo **~165 MB** (JVM: 2.658 s, 234 MB). Sin hints. Resto de módulos: perfil presente, **no ejecutado en esta sesión** |
| 2026-11-13 | `speckit.analyze` | Hecho | 1 CRITICAL (C1: `SameSite` vs constitución art. III), 4 HIGH (ADR Proposed; reversión F2; precondiciones de cutover de pagos sin tareas; usuario deshabilitado en servicios extraídos). Ver informe en la sesión |
| 2026-11-13 | `code-review` (medium) del diff: 10 hallazgos, verificados uno a uno | 8 corregidos, 2 documentados | #1 pago aprobado se cacheaba antes de publicar → ahora publica antes de cachear (Stripe recibe Idempotency-Key, el reintento no cobra doble) · #2 eventId aleatorio → determinista (`PaymentEventIds`: id de evento Stripe, o reserva + Idempotency-Key; también en payment-service) · #3 `OfferingUpserted` iba por `audit-events` → siempre por `booking-events` (`BookingEventPublisherPort`) · #4 audit-service guardaba eventos de read model → ignora `OfferingUpserted` · #5 snapshot solo activas → todas · #6b notificación antes del commit → `afterCommit` · #8 `ProcessedEventStore` en `adapter/in` → `application/port/out/ProcessedEventStorePort` · #9 lectura extra de `users` → destinatario pasado por el llamador · #10 condición obsoleta e import duplicado. **Documentados**: #6a no reintentables (ARC-104-06, deliberado) y #7 PII (ARC-104-05, preexistente vía `actorUsername`) |
| 2026-11-13 | Re-ejecución tras correcciones | **Verde** | backend `mvn verify`: 684 unit + IT 13/13; payment-service 36/36; audit-service `KafkaAuditConsumerTest` 2/2 (`target/` versionado restaurado) |
| 2026-11-13 | Correcciones de `speckit.analyze` (C1, E1, S1, R1, E2, F1-F4, U1, A1) — T080-T086 | Hecho | C1 `SameSite=Strict` en prod (JWT + CSRF configurable); E1 `BookingStatusChanged`/`BookingStateSnapshot` + cancelación por plazo vencido; S1 `UserAccessChanged` + 401 en ai/payment para suspendidos o rol cambiado (fail-closed); R1 `RecommendationGenerated` también en `recommendation-events`; E2 Prometheus (traefik, ai, payment, notification; `promtool check config` OK); spec/plan/contratos corregidos |
| 2026-11-13 | Re-ejecución final antes del commit | **Verde** | backend `mvn verify`: 693 unit + 13 IT; ai-service 46; payment-service 47; notification-service 22; audit-service 10 (target versionado restaurado) |

## Estado al cortar la sesión (2026-11-13, límite de uso)
- **W3 integrado**: `ai-service/` (37 tests verdes, re-ejecutados) y `notification-service/` (22 tests verdes, re-ejecutados), ambos en Compose bajo perfil `services`. El cutover de rutas sigue **desactivado**.
- Monolito: `notification-requests` lleva `recipientEmail`/`recipientName` (ECST, aditivo) y existe el flag `app.messaging.kafka.local-notification-consumer` (defecto true). Backend: 681 unitarios + IT 13/13 verdes.
- **Pendiente**: prueba en vivo del perfil `services` + cutover de IA (par RS256 de desarrollo generado solo en el scratchpad); W4 WP-E `payment-service`; W5 WP-F (native, CI/CD); `docker-compose.prod.yml`; tests de audit-service y frontend (**no ejecutados en esta sesión**); `speckit.analyze` y `code-review`; marcar `[x]` en `tasks.md`.
- Riesgos nuevos: `ai-service` no comprueba en cada petición que el usuario siga activo (un token de un usuario deshabilitado vale hasta que expira); `__TypeId__` del publicador de `ai-service` usa el nombre de clase del monolito (acoplamiento semántico, configurable).
