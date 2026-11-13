# Feature Specification: Gateway de borde, Kafka único y primeros microservicios en Oracle Free (HU-104)

**Feature Branch**: `feature/104-microservices-gateway` | **Created**: 2026-11-13 | **Status**: Draft (pendiente de aprobación humana) | **Issue**: [UNKNOWN] aún no creado; el número 104 es provisional (último issue visto: #103). Si el issue real tiene otro número, renombrar este directorio y la rama.
**Clasificación SDD**: Grande / alto riesgo (cambia límites arquitectónicos, mensajería y despliegue). Ciclo completo. Ver `docs/agents/SDD-HYBRID-GUIDE.md`.
**ADR**: [ADR-0009](../../docs/adr/0009-edge-gateway-kafka-only-native-images.md) (Proposed)
**Input**: Como equipo de SkillBridge, queremos pasar de un monolito con un proxy nginx a un conjunto de servicios detrás de un gateway, desplegado gratis en una sola VM, con un solo broker (Kafka) e imágenes nativas, para escalar partes del sistema por separado sin pagar infraestructura ni multiplicar la operación.

---

## Alcance

**Dentro**
1. Gateway Traefik v3 como único punto de entrada (enrutamiento, TLS, rate limiting, circuit breaker, correlación, Swagger agregado).
2. Retirar RabbitMQ; Kafka como único broker (pagos y notificaciones migran a topics).
3. Extraer `ai-service`, `notification-service` y `payment-service` del monolito.
4. Imágenes GraalVM native (ARM64) por servicio Spring, con criterio de salida a JVM afinada.
5. Despliegue en una VM Oracle Cloud Always Free con Docker Compose y CI/CD por SSH.

**Fuera** (no tocar en esta iteración)
- Extraer `identity`, `catalog`, `booking` (compuerta posterior, ADR propio).
- Reescribir `mentorship-service` (NestJS, otro equipo, issues #83-#91). Solo se **coordina** su cambio de RabbitMQ a Kafka.
- Job Spark (HU-25), RAG (HU-26) más allá de lo necesario para mover `ai-service`.
- Kubernetes, multi-región, alta disponibilidad, WAF/CDN de pago.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Un único punto de entrada con enrutamiento por prefijo (Priority: P1) 🎯 MVP

Como usuario del frontend, quiero acceder a toda la API desde `https://<dominio>/api/...` para que el cambio de servicios internos sea invisible.

**Why this priority**: Es el prerrequisito de todo lo demás y casi no tiene riesgo: el gateway se pone delante del monolito sin cambiar comportamiento.

**Independent Test**: Con el stack local/Oracle levantado, ejecutar la colección de humo (login, listar ofertas, crear reserva, consultar auditoría) contra `https://<dominio>` y contra el monolito directo; las respuestas (status y cuerpo) deben ser equivalentes.

**Acceptance Scenarios**:
1. **Given** el gateway delante del monolito, **When** el frontend llama `GET /api/offerings`, **Then** responde 200 con el mismo cuerpo que el monolito directo.
2. **Given** una petición a un prefijo sin ruta (`/api/desconocido`), **When** llega al gateway, **Then** responde 404 con formato ProblemDetail y cabecera `X-Correlation-Id`.
3. **Given** el frontend servido por el gateway en `/`, **When** se navega, **Then** las cookies HttpOnly `access_token` y `XSRF-TOKEN` funcionan igual (mismo origen) y un POST con cookie y `X-XSRF-TOKEN` válido es aceptado.
4. **Given** una petición HTTP (puerto 80), **When** llega, **Then** se redirige a HTTPS.

---

### User Story 2 - Protección y trazabilidad en el borde (Priority: P1)

Como operador, quiero rate limiting, circuit breaker y correlación en el gateway para proteger servicios y costos (LLM, Stripe) y poder seguir una petición de extremo a extremo.

**Why this priority**: Es lo que justifica tener gateway; y `ai` tiene costo por llamada.

**Independent Test**: Test de carga corto (p. ej. `hey`/`k6`) por perfil de ruta y verificación de cabeceras/trazas.

**Acceptance Scenarios**:
1. **Given** el perfil `auth` (p. ej. 10 req/min por IP, ráfaga 5), **When** una IP excede el límite en `/api/auth/login`, **Then** recibe 429 con `Retry-After` y el evento queda en el log de acceso.
2. **Given** el perfil `ai` más estricto que `default`, **When** se excede en `/api/ai/**`, **Then** 429 sin invocar al servicio.
3. **Given** un servicio detenido o con >30 % de errores 5xx en la ventana configurada, **When** continúan las peticiones, **Then** el circuit breaker abre, responde 503 inmediato (ProblemDetail), y cierra tras la recuperación.
4. **Given** una petición **sin** `X-Correlation-Id`, **When** atraviesa el gateway, **Then** el servicio la recibe con un UUID, la respuesta lo devuelve, aparece en logs (MDC) de todos los servicios implicados y en el envelope Kafka (`correlationId`) de los eventos que genere.
5. **Given** una petición **con** `X-Correlation-Id` válido, **When** atraviesa el gateway, **Then** se conserva sin cambios.
6. **Given** tráfico detrás de un proxy/CDN o de la red Docker, **When** se aplica rate limit, **Then** la clave es la IP real del cliente (no la del proxy).
7. **Given** una petición externa con `X-User-Id`/`X-User-Roles` forjados, **When** llega al gateway, **Then** esas cabeceras se eliminan antes de llegar a ningún servicio (y de todos modos los servicios no las usan para autenticar).

---

### User Story 3 - Swagger agregado (Priority: P2)

Como desarrollador/consumidor de la API, quiero un único Swagger UI que liste la documentación de cada servicio.

**Independent Test**: Abrir `https://<dominio>/docs` y comprobar que el selector lista todos los servicios y que "Try it out" funciona con el esquema de seguridad vigente.

**Acceptance Scenarios**:
1. **Given** N servicios expuestos, **When** se abre `/docs`, **Then** el selector muestra N definiciones, cada una cargada desde `/docs/<servicio>/v3/api-docs`.
2. **Given** un entorno no local, **When** se accede a `/docs`, **Then** exige `basicAuth` (credenciales por variable de entorno, nunca en el repo).
3. **Given** un servicio caído, **When** se abre `/docs`, **Then** las demás definiciones siguen disponibles.

---

### User Story 4 - Kafka como único broker (Priority: P1)

Como operador, quiero eliminar RabbitMQ para ahorrar RAM y reducir piezas, sin perder pagos ni notificaciones.

**Why this priority**: Libera ~300-500 MB y es prerrequisito de `payment-service` y `notification-service`.

**Independent Test**: Flujo reserva → pago aprobado → notificación → aprovisionamiento de mentoría ejecutado con RabbitMQ **apagado**; verificar que no queda ninguna dependencia `spring-boot-starter-amqp` ni contenedor `rabbitmq`.

**Acceptance Scenarios**:
1. **Given** una reserva creada, **When** se confirma el pago, **Then** `payment-events` recibe un evento `PaymentApproved` con el estado completo (ECST) y `BookingService` lo consume y actualiza la reserva.
2. **Given** un evento duplicado (mismo `eventId`), **When** se consume dos veces, **Then** el efecto es idempotente (una sola transición).
3. **Given** un evento no procesable tras N reintentos con backoff, **When** se agota, **Then** va a `<topic>.DLT` con cabeceras de error y un runbook indica cómo reprocesar.
4. **Given** la migración, **When** se compila el backend, **Then** no existe `spring-boot-starter-amqp` ni clases `Rabbit*`; las pruebas equivalentes a las de RabbitMQ pasan con Kafka.
5. **Given** la cabecera `X-Correlation-Id` de la petición origen, **When** se publica en Kafka, **Then** viaja en el envelope y en una cabecera Kafka, y el consumidor la restaura en MDC.

---

### User Story 5 - `ai-service` independiente (Priority: P2)

Como operador, quiero escalar y apagar la IA por separado (es el componente con costo externo y carga distinta).

**Independent Test**: `/api/ai/**` y `/api/admin/ai/providers` se atienden desde `ai-service`; con el servicio detenido el resto de la plataforma sigue funcionando y el gateway responde 503 controlado para `/api/ai/**`.

**Acceptance Scenarios**:
1. **Given** `ai-service` desplegado, **When** se pide una recomendación, **Then** la respuesta cumple el mismo contrato que antes (contract test).
2. **Given** `ai-service` sin acceso a tablas del monolito, **When** necesita datos de ofertas, **Then** los obtiene de un *read model* alimentado por eventos Kafka, no por JOIN ni consulta a la BD del monolito.
3. **Given** el servicio caído, **When** el frontend llama `/api/ai/...`, **Then** recibe 503 ProblemDetail y la UI muestra el mensaje amigable ya existente (HU-26).

---

### User Story 6 - `notification-service` y `payment-service` (Priority: P2)

Como equipo, queremos aislar el alcance de seguridad de pagos (Stripe) y la entrega de notificaciones.

**Acceptance Scenarios**:
1. **Given** `payment-service`, **When** llega el webhook de Stripe a `/api/webhooks/stripe`, **Then** se valida la firma, se publica `PaymentApproved/Rejected` en Kafka y el monolito reacciona; el webhook **no** pasa por rate limit por IP restrictivo ni por `basicAuth`.
2. **Given** `payment-service`, **When** se inicia un pago, **Then** se valida que el actor sea dueño de la reserva (invariante de seguridad de pagos) sin leer la BD del monolito (datos por evento o consulta a una API de lectura acordada).
3. **Given** un `booking-created` o recordatorio de pago, **When** `notification-service` consume `notification-requests`, **Then** envía el correo (Mailpit en local) de forma idempotente y envía los fallos a DLT.
4. **Given** ninguno de los dos servicios imprime PAN/CVV en logs ni persiste datos de tarjeta.

---

### User Story 7 - Imágenes nativas y presupuesto de RAM (Priority: P2)

Como operador, quiero que todo el stack quepa holgadamente en la VM gratuita.

**Independent Test**: `docker stats` tras 10 min de humo; suma de RAM de todo el stack ≤ 8 GB (objetivo) y cada servicio Spring native ≤ 200 MB en reposo.

**Acceptance Scenarios**:
1. **Given** un servicio Spring, **When** se ejecuta el perfil native, **Then** arranca, pasa su suite de contrato/humo y su RAM en reposo queda documentada.
2. **Given** un servicio que falla en native por reflexión/recursos y no se resuelve con *hints* en tiempo acotado, **When** se decide, **Then** permanece en JVM afinada con la razón registrada en `research.md` (no es un fallo de la feature).
3. **Given** el CI, **When** se publica, **Then** las imágenes son `linux/arm64` en GHCR y la VM solo hace `pull`.

---

### User Story 8 - Despliegue y operación en Oracle Free (Priority: P1)

Como operador, quiero un despliegue reproducible y reversible.

**Acceptance Scenarios**:
1. **Given** una VM Oracle nueva, **When** se sigue `docs/guides/oracle-free-deployment.md`, **Then** el stack queda arriba con HTTPS válido.
2. **Given** un push a `main`/tag, **When** corre el pipeline, **Then** se construyen/publican imágenes y se despliega por SSH con `docker compose pull && up -d`, con *rollback* a la etiqueta anterior documentado.
3. **Given** el cron de respaldo, **When** se ejecuta, **Then** `pg_dump` y `mongodump` salen a almacenamiento externo gratuito y se documenta una restauración probada.
4. **Given** una VM inactiva, **When** pasa el tiempo, **Then** existe una verificación/keep-alive documentada contra la reclamación por inactividad de Oracle.
5. **Given** secretos (JWT privada, Stripe, DB, ACME, basicAuth), **When** se despliega, **Then** viven fuera del repo (variables/Secrets), nunca en imágenes.

---

### Edge Cases
- Cookie `access_token` con `Domain`/`SameSite` pensada para un host distinto al nuevo origen único.
- CORS: con mismo origen debería ser innecesario; los entornos con frontend separado deben seguir funcionando.
- Webhook de Stripe: cuerpo crudo intacto (no reescribir ni comprimir en el gateway).
- Un consumidor lento no debe bloquear la partición de otros eventos (separar `group-id` por servicio).
- Reinicio de Traefik: el rate limiter se reinicia (aceptado, documentado).
- Orden de eventos de pago: clave `bookingId` garantiza orden por reserva.
- Servicios nuevos que arranquen antes que Kafka/Postgres: *health* y reintentos.
- `target/` versionado en `audit-service` (issue #103) ensucia builds/imágenes.

---

## Requirements *(mandatory)*

### Functional Requirements
- **FR-001**: El gateway MUST ser el único puerto público (80/443); ningún servicio ni BD publica puertos al exterior en producción.
- **FR-002**: El enrutamiento MUST seguir la tabla de `contracts/gateway-routes.md`, por prefijo y prioridad.
- **FR-003**: Rate limiting MUST tener perfiles `auth`, `ai` y `default`, con valores configurables por entorno (variables), y responder 429 + `Retry-After`.
- **FR-004**: El circuit breaker MUST aplicarse por servicio con expresión configurable, fallback 503 ProblemDetail y recuperación automática.
- **FR-005**: El gateway MUST generar `X-Correlation-Id` si falta y conservarlo si existe; MUST eliminar `X-User-Id`/`X-User-Roles` entrantes.
- **FR-006**: Todo servicio MUST registrar `correlationId` en MDC, propagarlo en HTTP saliente y en el envelope y cabeceras Kafka.
- **FR-007**: Cada servicio MUST publicar `/v3/api-docs`; el gateway MUST servir un Swagger UI agregado en `/docs`, protegido fuera de local.
- **FR-008**: RabbitMQ MUST eliminarse del código, dependencias, Compose y docs; los contratos de `payment-events` y `notification-requests` MUST documentarse en `contracts/kafka-topics.md` (versionados).
- **FR-009**: Todo consumidor Kafka MUST ser idempotente por `eventId` y tener política de reintento + DLT.
- **FR-010**: Cada servicio MUST tener BD o schema propio con credenciales propias; PROHIBIDO leer tablas de otro servicio.
- **FR-011**: Los servicios MUST validar el JWT RS256 con la clave pública (ADR-0006) y mantener CSRF para cookie + mutaciones; el gateway MUST NOT ser la única barrera.
- **FR-012**: El dominio MUST seguir sin imports de framework (`AGENTS.md` §1); `audit-service` MUST NOT tener dependencias de compilación con `backend/` ni con los servicios nuevos.
- **FR-013**: Las imágenes MUST publicarse para `linux/arm64`; el perfil native MUST existir por servicio Spring o la excepción MUST quedar registrada.
- **FR-014**: Compose de producción MUST declarar límites de memoria por contenedor y *healthchecks*.
- **FR-015**: MUST existir respaldo automatizado y restauración documentada de Postgres y Mongo.
- **FR-016**: Pagos MUST seguir sin tratar PAN/CVV; tokenización Stripe; validar propiedad de la reserva.
- **FR-017**: Cada fase MUST ser desplegable y reversible por separado (flag de ruta en el gateway o etiqueta de imagen).

### Non-Functional Requirements
- **NFR-001 (Costo)**: Infraestructura recurrente = $0 (Oracle Always Free + GHCR + dominio gratuito/propio existente).
- **NFR-002 (RAM)**: Total del stack ≤ 8 GB en reposo tras 10 min de humo; margen ≥ 50 % de los 24 GB disponibles.
- **NFR-003 (Latencia)**: Sobrecarga del gateway p95 < 20 ms sobre el servicio directo, medida y registrada.
- **NFR-004 (Arranque)**: Servicio native listo (< 3 s) tras `docker start`.
- **NFR-005 (Observabilidad)**: Métricas de Traefik y de Resilience4j en Prometheus; log de acceso JSON con `correlationId`.
- **NFR-006 (Seguridad)**: Cabeceras de seguridad estándar (HSTS) en el borde; Swagger y métricas no públicos.

### Key Entities / Contratos
- **BusinessEvent** (envelope existente, ADR-0006 §4): se extiende, si hace falta, con `correlationId` y `version` sin romper a audit.
- **Topics**: `booking-events` (existente), `payment-events`, `notification-requests` y sus `*.DLT`.
- **Rutas del gateway**: ver `contracts/gateway-routes.md`.

---

## Success Criteria *(mandatory)*

- **SC-001**: Colección de humo equivalente (monolito directo vs. gateway) 100 % verde.
- **SC-002**: Con `rabbitmq` ausente del Compose y del código, el flujo reserva → pago → notificación → aprovisionamiento pasa de extremo a extremo.
- **SC-003**: RAM total ≤ 8 GB y cada servicio Spring native ≤ 200 MB en reposo (o excepción documentada).
- **SC-004**: 429 y 503-por-circuito demostrados con pruebas automatizadas o script reproducible.
- **SC-005**: Un `X-Correlation-Id` generado en el borde aparece en logs de gateway, servicio y consumidor Kafka (evidencia en `progress.md`).
- **SC-006**: Despliegue desde cero en una VM nueva siguiendo solo la guía, sin pasos no documentados.
- **SC-007**: `mvn test` (backend y audit) y `npm test -- --watch=false` siguen en verde; resultados reportados solo si se ejecutaron (`AGENTS.md` §2).

---

## Assumptions / Decisiones por defecto (sin bloqueo)

Para poder ejecutar de una pasada, cada pregunta tiene un **valor por defecto vinculante**. Si el usuario no dice lo contrario, se aplica el defecto; lo que solo el usuario puede aportar (dominio real, VM, secretos) va a `HUMAN-TODO.md` y **no detiene** el trabajo en el repo.

| # | Pregunta | Estado | Defecto aplicado |
|---|---|---|---|
| Q1 | Dominio/DNS | [UNKNOWN] | Variable `DOMAIN`; ACME HTTP-01 con DNS en modo "solo DNS" (DuckDNS u otro). Nada se hardcodea |
| Q2 | ¿Repo público? | **[VERIFIED] público** (`gh repo view`, 2026-11-13) | Runners `ubuntu-24.04-arm` para native (sujeto a confirmar gratuidad vigente en docs de GitHub) |
| Q3 | Equipo NestJS y Kafka | [UNKNOWN] | **Se ejecuta la retirada de RabbitMQ** en este repo (no existe `mentorship-service` aquí) y se entrega `contracts/kafka-topics.md` + borrador de issue en `HUMAN-TODO.md`; el usuario lo envía al otro equipo. No se editan los contratos de mentorías |
| Q4 | `ai-service` sin tablas del monolito | [UNKNOWN] | Regla: si recomendación/visión/STT/TTS necesitan ≤ 2 tablas del monolito, usar read model por eventos. `/api/ai/knowledge` (RAG, pgvector) **permanece en el monolito** con una ruta de mayor prioridad que `/api/ai/**` |
| Q5 | Imágenes `arm64` | [UNKNOWN] | Se verifica en T003; si Kafka cp 7.8 no es multi-arch → `apache/kafka` oficial |
| Q6 | Issue #103 | **Pausado por el usuario** | No bloquea `ai`/`notification`/`payment`. `identity`/`catalog`/`booking` siguen **fuera de alcance**; el código no se refactoriza aquí salvo lo mínimo para mover los puertos |
| Q7 | Cookies con origen único | [UNKNOWN] | Origen único = mismo host que el frontend; ajustar solo lo que T006 demuestre necesario |
