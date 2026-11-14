# Tasks: Gateway de borde, Kafka único y primeros microservicios (HU-104)

**Input**: `specs/104-microservices-gateway/` (spec.md, plan.md, contracts/)
**Convención**: `[P]` = paralelizable (archivos distintos). `[USn]` = historia de la spec. Cada tarea indica archivos y criterio verificable. Marcar `[x]` solo con evidencia registrada en `progress.md`.
**Regla**: no commitear ni abrir PR sin petición del usuario. Reportar tests solo si se ejecutaron.

---

## Fase 0: Preparación y decisiones (bloquea todo)

- [x] T001 Leer `.specify/memory/constitution.md`, `docs/agents/SDD-HYBRID-GUIDE.md`, `docs/adr/0006-*.md`, `0009-*.md`; confirmar que spec/plan no la violan. Salida: nota en `progress.md`.
- [x] T002 Resolver Q1 (dominio/DNS) y Q2 (¿repo público?; define dónde se compila native). Q2 ya verificada: repo público. Aplicar los defectos de la tabla de `spec.md`; **no preguntar**. Salida: `research.md` §Q.
- [x] T003 Resolver Q5: verificar `arm64` de las imágenes de Kafka, Postgres(pgvector), Mongo, Redis, Prometheus, Grafana, Traefik, swagger-ui (`docker buildx imagetools inspect <img>`). Elegir imagen de Kafka. Salida: tabla en `research.md`.
- [x] T004 Redactar el **borrador del issue de coordinación** para el equipo NestJS (HU-MNT-02 #84: RabbitMQ → Kafka con `contracts/kafka-topics.md`) en `HUMAN-TODO.md`. **No crear el issue** (acción externa). No editar `docs/contracts/mentorships/*`.
- [x] T005 Resolver Q4: mapear qué tablas/servicios usan `AiRecommendationService`, `KnowledgeSearchPort` y los adaptadores `adapter/out/ai/` (grep de repositorios/JPA). Decidir: (a) read model por eventos, (b) `ai-service` con su propio pgvector, (c) aplazar la extracción. Salida: sección en `research.md` + actualización de la Fase 3.
- [x] T006 Resolver Q7: revisar `AuthCookieService`, `SecurityConfiguration`, CORS y `frontend/src/app/core/api.ts`/`public/env.js` para el origen único. Salida: lista de cambios mínimos.
- [x] T007 Baseline: levantar el stack actual, medir RAM (`docker stats`) y latencia p95 de 3 endpoints; guardar en `progress.md`. (NFR-002/003).
- [x] T007b Revisar `measure/measure.sh` y `measure/README.md` (ya creados, **sin ejecutar**); correr `ram baseline` junto con T007 y registrar en `progress.md` (sección Mediciones).
- [x] T008 Colección de humo reutilizable en `specs/104-microservices-gateway/smoke/` (script bash + `curl`, o k6): login, listar ofertas, crear reserva, pago simulado, consultar auditoría, `/api/ai/...`. Ejecutable contra cualquier base URL. Criterio: verde contra el monolito directo.
- [ ] T009 *Spike native* del monolito en una rama aparte: `mvn -Pnative native:compile` (o buildpacks), listar fallos de reflexión/recursos (Jackson `BusinessEvent`, Stripe SDK, Resilience4j, springdoc, Flyway, pgvector). Salida: lista priorizada en `research.md` (no se mergea). — *No ejecutado en esta sesión (sin GraalVM local); pasa a WP-F.*

**Checkpoint F0**: Q1-Q7 resueltas o aceptadas por el usuario; baseline y humo listos.

---

## Fase 1: Gateway (US1, US2, US3, US8) 🎯 MVP

- [x] T010 [US1] `infra/traefik/traefik.yml`: entrypoints `web`(redirige a https) y `websecure`, ACME (HTTP-01 o DNS-01 según Q1), `forwardedHeaders.trustedIPs`, access log JSON con `X-Correlation-Id`, métricas Prometheus, file provider.
- [x] T011 [US1] `infra/traefik/dynamic/routers.yml` según `contracts/gateway-routes.md` (fase 1: todo `/api/**` → `backend`; `/` → `frontend`; `/audit` → audit). Prioridades explícitas.
- [x] T012 [P] [US2] `infra/traefik/dynamic/middlewares.yml`: `rateLimit` (perfiles auth/ai/default/webhook con valores por variable), `circuitBreaker`, `retry` solo GET, `headers` (HSTS; borrar `X-User-Id`/`X-User-Roles` entrantes), `basicAuth` para `/docs`.
- [x] T013 [P] [US2] Plugin local Yaegi `correlationid` en `infra/traefik/plugins-local/...`: conserva `X-Correlation-Id` válido (UUID) o genera uno, lo añade a la petición y a la respuesta. Test: petición con/sin cabecera y con valor inválido. **Plan B** si Yaegi falla: generación en `CorrelationIdFilter` del servicio (ya existente) y documentar que el borde no la crea.
- [x] T014 [P] [US3] `infra/swagger-ui/`: contenedor `swaggerapi/swagger-ui` con `URLS` apuntando a `/docs/<svc>/v3/api-docs`; routers con `stripPrefix`. Habilitar `springdoc.api-docs` en los servicios (hoy solo `swagger-ui.path` en `backend/src/main/resources/application.yml`). Verificar que `v3/api-docs` está permitido por `SecurityConfiguration`.
- [x] T015 [US1] `docker-compose.yml`: añadir `traefik` y `swagger-ui`; quitar la publicación directa de puertos del backend; el frontend deja de hacer proxy (`frontend/nginx.conf.template` `location /api/` queda sin uso en prod, no borrar sin comprobar el uso local).
- [x] T016 [US1] Ajustes de auth/CORS/cookies del origen único según T006 (`AuthCookieService`, CORS en `SecurityConfiguration`). Test: login + POST con `X-XSRF-TOKEN` a través del gateway. — *Sin cambios de código (T006); solo variables en prod.*
- [x] T017 [US2] Propagación en el monolito: confirmar que `CorrelationIdFilter` (en `infrastructure/adapter/in/rest/filter/`) lee la cabecera, rellena MDC y la devuelve; añadir propagación en clientes HTTP salientes si faltara. Test unitario.
- [x] T018 [P] [US2] Script `specs/104-microservices-gateway/smoke/edge-checks.sh`: 429 en `auth`, 429 en `ai`, circuito abierto al parar el backend, spoof de `X-User-*`, cabecera de correlación presente.
- [x] T019 [US1] Ejecutar T008 contra el gateway y contra el monolito directo; comparar. Criterio SC-001. — *SC-001 verde (progress.md).*
- [ ] T020 [US2] Medir sobrecarga del gateway (p95) y calibrar rate limits/breaker (NFR-003). Actualizar los valores en `contracts/gateway-routes.md`. — *Parcial: sobrecarga medida (3.6 ms p95, portátil); calibración de rate limits pendiente de carga real en Oracle.*
- [x] T021 [P] [US8] `docs/guides/oracle-free-deployment.md` (borrador de la fase 1): crear VM A1, security list + iptables (80/443/22), Docker, Compose, DNS, ACME. — *Guía completa redactada; despliegue real pendiente (HUMAN-TODO).*
- [ ] T022 [US8] `docker-compose.prod.yml` mínimo: límites de memoria, healthchecks, sin puertos de BD, volúmenes. Desplegar en la VM y repetir T019 sobre HTTPS real. — *Parcial: `docker-compose.prod.yml` creado y validado con `config`; despliegue en la VM pendiente (HUMAN-TODO).*

**Checkpoint F1**: SC-001 y SC-004 parciales verdes; despliegue en Oracle con HTTPS.

---

## Fase 2: Kafka como único broker (US4)

- [x] T030 [US4] Definir topics y propiedades (`payment-events`, `notification-requests`, DLT) en la configuración de Kafka (`backend/src/main/java/com/riwi/skillbridge/infrastructure/config/`) y en `application.yml`; crear con `NewTopic` o script de arranque.
- [x] T031 [US4] Test de contrato del envelope y de los payloads (`PaymentApproved` v2, notificaciones) en `backend/src/test/`. Incluir `correlationId`.
- [x] T032 [US4] Productor de pagos en Kafka: sustituir `RabbitPaymentEventPublisher` (`adapter/out/messaging/`) por un publicador Kafka tras el mismo puerto de salida. Flag `app.messaging.payments=kafka|rabbit` para transición.
- [x] T033 [US4] Consumidor de pagos en Kafka con idempotencia por `eventId`: sustituir `PaymentResultConsumer` (`adapter/in/messaging/`). Tests: duplicado, orden, DLT tras reintentos.
- [x] T034 [P] [US4] Productor/consumidor de notificaciones: sustituir `RabbitNotificationPublisher` y `NotificationConsumer` por Kafka (`notification-requests`), con DLT; preservar el manejo de "mensaje no enrutable" como métrica/log.
- [x] T035 [US4] Restaurar `correlationId` en MDC al consumir y ponerlo en cabeceras al publicar. Test.
- [x] T036 [US4] Runbook de reprocesado de DLT en `docs/` (qué mirar, cómo reinyectar).
- [x] T037 [US4] Con ambos flags en `kafka`, ejecutar el flujo completo y los tests de `mvn test`/`mvn verify` (Docker). Registrar resultados reales. — *Flujo con Testcontainers + stack real; webhook Stripe real no ejecutado.*
- [x] T038 [US4] Retirar RabbitMQ: `RabbitConfiguration`, clases `Rabbit*`, `spring-boot-starter-amqp` del `pom.xml`, servicio `rabbitmq` y `RABBITMQ_URL` de Compose, referencias en docs (`docs/INTEGRATIONS.md`, `docs/TESTING.md`, `docs/DEPLOYMENT-CHECKLIST.md`, `docs/PROJECT_CONTEXT.md`, `docs/CONTRIBUTING.md`, etc. — grep; **no** tocar `docs/audit/*`). Se ejecuta en la Ola final tras T037 verde (defecto de Q3).
- [x] T039 [US4] Ajustar Kafka para RAM: heap 512 MB, retención corta, 1 réplica; verificar `arm64`.

**Checkpoint F2**: SC-002 verde con RabbitMQ ausente.

---

## Fase 3: `ai-service` (US5)  — depende de T005

- [x] T040 [US5] Crear módulo `ai-service/` (Maven independiente, hexagonal) con `pom.xml`, Dockerfile JVM, actuator y springdoc. Sin dependencia de `backend/`.
- [x] T041 [US5] Mover puertos y adaptadores de IA (`AiRecommendationPort`, `VisionPort`, `SpeechToTextPort`, `TextToSpeechPort`, cadena de fallback ADR-0007, RAG HU-26) según la decisión de T005, sin importar frameworks en el dominio.
- [x] T042 [US5] BD/schema propio (`ai`) con usuario propio y migraciones Flyway del servicio; read model por eventos `booking-events`/catálogo si aplica.
- [x] T043 [P] [US5] Contract tests: mismas respuestas que el monolito para `/api/ai/**` y `/api/admin/ai/providers/**`.
- [x] T044 [US5] Seguridad: validación JWT con clave pública, CSRF/autorización iguales; Resilience4j en llamadas a proveedores.
- [x] T045 [US5] Ruta del gateway `/api/ai/**` → `ai-service` con perfil `ai` y breaker; mantener ruta de reversión documentada. — *Cutover probado en vivo; desactivado por defecto.*
- [ ] T046 [US5] Retirar el código de IA del monolito solo tras una release estable; actualizar `docs/MODULES/backend.md` y `docs/INTEGRATIONS.md`.

## Fase 4: `notification-service` (US6) — [P] con Fase 3

- [x] T050 [US6] Crear módulo `notification-service/` (sin controladores públicos): consumidor de `notification-requests`, plantillas, envío (Mailpit local / SMTP real por variable), idempotencia, DLT.
- [ ] T051 [US6] Desactivar el consumidor en el monolito tras validar; retirar código cuando sea estable. — *Parcial: flag `app.messaging.kafka.local-notification-consumer` (prod=false); retirar código tras release estable.*
- [x] T052 [US6] Tests: duplicado, fallo SMTP → reintento → DLT, correlación.

## Fase 5: `payment-service` (US6) — alto riesgo

- [ ] T060 [US6] Crear módulo `payment-service/`: Stripe (tokenización, sin PAN/CVV), `PaymentController`, `StripeWebhookController` (cuerpo crudo y verificación de firma), publicación en `payment-events`.
- [ ] T061 [US6] Resolver la propiedad de la reserva sin leer la BD del monolito: decisión documentada (evento con `ownerId` en `booking-events` + read model, o API de lectura interna autenticada). Test de autorización negativo.
- [ ] T062 [US6] Pruebas con Stripe en modo test; verificar que ningún log contiene datos de tarjeta; ruta `/api/webhooks/**` sin `basicAuth`/rate limit restrictivo.
- [ ] T063 [US6] Mover rutas en el gateway y mantener la reversión. Retirar el código de pagos del monolito tras una release estable.

## Fase 6: Native, CI/CD y operación (US7, US8)

- [ ] T070 [US7] Perfil native por servicio Spring (monolito, ai, payment, notification, audit-backend): hints para Jackson, Stripe SDK, Resilience4j, Flyway, springdoc. Criterio por servicio: pasa humo/contrato en native, **o** queda en JVM afinada con razón en `research.md`.
- [ ] T071 [US7] Workflow `.github/workflows/build-native-arm64.yml`: runner ARM (o `buildx` con QEMU si es repo privado, aceptando lentitud), push a GHCR con tags `sha` y `latest`.
- [ ] T072 [US8] Workflow `deploy-oracle.yml`: SSH a la VM, `docker compose -f docker-compose.prod.yml pull && up -d`, comprobación de salud, rollback a tag previo.
- [ ] T073 [US8] Backups: cron `pg_dump` y `mongodump` hacia almacenamiento externo gratuito + prueba de restauración documentada (FR-015).
- [ ] T074 [US8] Keep-alive/actividad contra reclamación de Oracle; alerta básica (Prometheus/uptime externo gratis).
- [ ] T075 [US7] Medir con `measure/measure.sh` (RAM, arranque, latencia gateway vs directo) en portátil y en Oracle, 3 repeticiones; registrar en `progress.md` y sustituir las estimaciones `[INFERRED]` de `plan.md`/ADR-0009 por valores `[VERIFIED]`. Si no hay VM aún: "No ejecutado en esta sesión" y queda en `HUMAN-TODO.md`.
- [ ] T076 [US8] Cerrar `docs/guides/oracle-free-deployment.md`; actualizar `docs/architecture/overview.md`, `docs/MODULES/*`, `docs/DEPLOYMENT-CHECKLIST.md`, `CLAUDE.md` (tabla de módulos/comandos), `docs/RISK-REGISTER.md` (nuevos riesgos).
- [ ] T077 Revisión final: `speckit.analyze` (consistencia spec/plan/tasks), checklist de seguridad y verificación de SC-001..SC-007. Pasar el ADR-0009 a Accepted **solo con aprobación humana**.

---

## Dependencias
F0 → F1 → F2 → {F3 ∥ F4} → F5 → F6. T038 depende de T037. T041 depende de T005. T061 depende de T033.
Paralelización segura: dentro de F1 (T012, T013, T014, T018, T021); F3 y F4 en worktrees distintos (módulos distintos).

## Definition of Done (por fase)
Humo verde · tests relevantes ejecutados y reportados (o "No ejecutado en esta sesión") · evidencia en `progress.md` · docs actualizadas · reversión probada · sin secretos en el repo · sin cambios sin commit fuera del alcance.

## Fase 7: Correcciones post-análisis (speckit.analyze + code-review, 2026-11-13)

- [x] T080 [US8] C1: `SameSite=Strict` para `access_token` y `XSRF-TOKEN` en producción (`docker-compose.prod.yml`); cookie CSRF configurable (`CSRF_COOKIE_SAME_SITE`) en backend, ai-service y payment-service.
- [x] T081 [US6] E1: `BookingStatusChanged` v1 (CONFIRMED / CANCELLED por plazo vencido) y `BookingStateSnapshot` v1 al arrancar (`APP_BOOKINGS_SNAPSHOT_ON_STARTUP`); payment-service los consume. Precondición del cutover de pagos.
- [x] T082 [US6] E1: `processPaymentRejected` cancela la reserva si el plazo de pago venció (paridad con el flujo en línea anterior).
- [x] T083 [US5] S1: `UserAccessChanged` v1 (estado/rol, sin PII) + snapshot de suspendidos; ai-service y payment-service rechazan tokens de usuarios suspendidos o con rol cambiado.
- [x] T084 [US5] R1: `RecommendationGenerated` también en `recommendation-events` (monolito y ai-service) para que trending cuente recomendaciones reales.
- [x] T085 [US2] E2: Prometheus recoge Traefik (`ops`), ai-service, payment-service y notification-service.
- [x] T086 Code-review: publicar antes de cachear el pago, eventIds deterministas, `OfferingUpserted` por `booking-events`, audit ignora eventos de read model, snapshot de todas las ofertas, notificación tras commit, `ProcessedEventStorePort` como puerto de salida, destinatario pasado por el llamador.
