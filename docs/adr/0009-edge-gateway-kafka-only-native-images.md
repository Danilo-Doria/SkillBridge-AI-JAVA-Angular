# ADR-0009: Gateway de borde con Traefik, Kafka como único broker e imágenes GraalVM native en Oracle Cloud

## Status

Proposed (requiere aprobación humana)

## Date

2026-11-13

## Context

ADR-0001 fijó "monolito modular primero" y ADR-0006 (Proposed) fijó el reparto monolito + servicios
satélite (`audit-service`, `mentorship-service` NestJS, job Spark). Ahora se quiere avanzar hacia
microservicios **sin aumentar el costo** (objetivo: $0) y **sin complicar el despliegue** (un solo lugar).

Restricciones reales:

- Hoy `docker-compose.yml` levanta ~13 contenedores (Postgres, Redis, RabbitMQ, Kafka, Mongo, backend,
  frontend, audit-backend, audit-frontend, Prometheus, Grafana, Mailpit...). Cada JVM de Spring Boot
  cuesta ~400-600 MB; con más servicios la RAM manda.
- `frontend/nginx.conf.template` hace `proxy_pass ${BACKEND_URL}/api/` hacia **un único backend**: no
  hay enrutamiento por prefijo (la incógnita que dejó abierta ADR-0006).
- Hay **dos brokers** (RabbitMQ para puntos a punto, Kafka para el log de eventos). Dos brokers son
  RAM y operación duplicadas.
- `CorrelationIdFilter` ya propaga `X-Correlation-Id` dentro del monolito; no existe garantía en el borde.
- El issue #103 documenta deuda que bloquea extraer servicios (controladores con JPA directo,
  `application` dependiendo de `infrastructure`).

## Decision

### 1. Plataforma: una VM Oracle Cloud Always Free (ARM Ampere A1)

Todo corre en **una VM** (hasta 4 OCPU / 24 GB RAM, `aarch64`) con **Docker Compose**. No se adopta
Kubernetes. Si se superara la VM, el siguiente paso sería k3s en la misma máquina, no un clúster.
Consecuencia: **todas las imágenes deben ser `linux/arm64`** (o multi-arch).

### 2. Gateway de borde: Traefik v3 (no Spring Cloud Gateway)

Traefik es un binario Go (~50-100 MB de RAM) con TLS ACME, enrutamiento por prefijo y middlewares
declarativos. Spring Cloud Gateway costaría una JVM más (~300 MB) y se descarta.

| Necesidad | Mecanismo | Nota |
|---|---|---|
| Enrutamiento por prefijo | Routers/Services de Traefik (file provider) | Sustituye el `proxy_pass` de nginx para `/api/` |
| TLS | ACME (Let's Encrypt) en Traefik | Dominio gratuito (p. ej. DuckDNS) o propio |
| **Rate limiting** | Middleware `rateLimit` (por IP real) | **En memoria, por instancia**. Válido con una sola réplica de Traefik. Perfiles: `auth` (estricto), `ai` (protege costo de LLM), `default` |
| **Circuit breaker** | Middleware `circuitBreaker` (+ `retry` solo para GET idempotentes) | Complementa, no sustituye, a Resilience4j dentro de los servicios para llamadas salientes |
| **Propagación** | Plugin **local** (Yaegi) que genera `X-Correlation-Id` si falta; los servicios lo propagan a MDC, HTTP saliente y envelope Kafka | Plugin local = código en el repo, sin descargar de un catálogo externo |
| **Swagger** | Un contenedor `swagger-ui` con múltiples `urls`, cada servicio expone `/v3/api-docs` bajo `/docs/<svc>/` | Traefik **no** agrega OpenAPI; se agrega en cliente. Protegido con `basicAuth` fuera de local |
| Seguridad | JWT RS256 **validado por cada servicio** (ADR-0006 regla 6). El gateway **no** autentica | Cero confianza interna: ningún servicio acepta `X-User-*` como identidad |

Decisión consciente: **sin ForwardAuth/edge-auth en esta iteración.** Evita una pieza más y evita
acoplar el borde al monolito. Se reevalúa solo si un servicio no puede validar JWT por sí mismo.
CSRF (`CookieCsrfTokenRepository`) también se queda en los servicios; el gateway reenvía cookies y
cabeceras sin tocar.

### 3. Kafka como único broker (retirar RabbitMQ)

Se elimina RabbitMQ. Se sustituye por topics Kafka (nombres definitivos en
`specs/104-microservices-gateway/contracts/kafka-topics.md`):

| Antes (RabbitMQ) | Después (Kafka) |
|---|---|
| exchange `payment.events` (`payment.approved/rejected`) | topic `payment-events`, clave = `bookingId`, envelope `BusinessEvent` |
| `notification.exchange` / `notification.queue` | topic `notification-requests`, clave = `bookingId` |
| `notification.dlq`, `dlx.exchange`, DLQ de mentorías | topics `*.DLT` con `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` y reintentos con backoff |
| log de negocio/auditoría (`booking-events`, ADR-0003) | **sin cambios** |

Trade-offs aceptados: Kafka no tiene colas por consumidor ni *requeue* nativo (se resuelve con reintentos
y DLT); el consumo es **at-least-once**, por lo que **todo consumidor debe ser idempotente** (clave
de idempotencia = `eventId`). Se pierde la UI de gestión de RabbitMQ.

Kafka se queda en **KRaft de un nodo**, heap acotado (~512 MB). Verificar que la imagen elegida sea
`arm64`.

### 4. Imágenes GraalVM native para los servicios Spring

Cada servicio Spring Boot 3.5 se empaqueta como imagen **native** para bajar RAM y arranque. Reglas:

- Se construye **por servicio y con criterio de salida**: si un servicio no supera las pruebas en native
  (reflexión en Jackson/Stripe SDK/Resilience4j, recursos Flyway, springdoc), **permanece en JVM
  afinada** (`-XX:+UseSerialGC`, `-Xss256k`, `-XX:MaxRAMPercentage`, AppCDS) y se documenta.
- `native-image` necesita varios GB de RAM y debe producir `aarch64`: se compila en **CI**
  (runner ARM) y se publica en GHCR; **no se compila en la VM de producción**.
- NestJS (`mentorship-service`) y Angular quedan fuera (no aplica GraalVM).

### 5. Extracción de servicios: orden y compuertas

Orden: **gateway → Kafka único → `ai-service` → `notification-service` → `payment-service`**.
`identity`, `catalog` y `booking` **no se extraen en esta iteración**; requieren una compuerta
explícita (issue #103 resuelto, datos propios por servicio, Outbox, sagas) y un ADR propio.

Reglas que se mantienen de ADR-0006: una BD (o schema con usuario propio) por servicio, sin lecturas
cruzadas, sin HTTP síncrono entre servicios en el flujo de pago, eventos versionados.

## Consequences

**Positivas**
- Costo $0 de infraestructura; un solo `docker compose` en una VM.
- Se reemplazan dos brokers por uno; baja RAM y superficie operativa.
- El borde da, por fin, enrutamiento por prefijo, rate limiting, circuit breaker y correlación uniformes.
- Native reduce RAM por servicio (estimación **[INFERRED]**: de ~400-600 MB a ~80-200 MB; a confirmar con medición).

**Negativas / riesgos**
- **Rompe el contrato de mentorías**: `docs/contracts/mentorships/events.md` y HU-MNT-02 (#84) asumen
  RabbitMQ (`payment.events`, `mentorship.payment.approved.queue`). Requiere **acuerdo con el equipo
  NestJS** y actualizar el contrato (ver errata en ADR-0006).
- Rate limiting en memoria: se pierde el estado al reiniciar Traefik y no escala a varias réplicas.
- Detrás de un proxy/CDN, sin `trustedIPs`/`ipStrategy` correctos todos los clientes compartirían IP y el
  limitador bloquearía a usuarios legítimos.
- Native: tiempos de build largos y fallos de reflexión solo visibles en runtime; el free tier de runners
  ARM de GitHub aplica a repos públicos **[UNKNOWN]** si este repo lo es.
- Una sola VM = un único punto de falla y reclamación por inactividad de Oracle. Aceptable para
  entorno académico/portafolio (coherente con ADR-0002), **no** para producción real.
- Más servicios = más contratos que mantener; sin Outbox hay riesgo de pérdida de eventos al publicar.

## Alternativas descartadas

| Alternativa | Motivo |
|---|---|
| Spring Cloud Gateway | +1 JVM (~300 MB), Java sobre el camino crítico; el free tier lo penaliza |
| Kong / APISIX | Más piezas (BD/etcd) para el mismo resultado |
| Mantener RabbitMQ y Kafka | Duplica RAM/operación; el usuario pidió un solo broker |
| Redpanda (API Kafka) | No es Apache Kafka; la decisión es Kafka. Se menciona solo como plan B de RAM |
| Render/Railway/Fly | Duermen servicios y cobran por servicio; inviable con 5+ servicios |
| Kubernetes/k3s desde el inicio | Complejidad sin beneficio en una VM |
| Redis-backed rate limit en el borde | Traefik OSS no lo trae; se difiere |

## Supersession

- Sustituye la **regla 2** y la mención a `payment.events` de la sección 2 de ADR-0006
  (hechos punto a punto → Kafka, no RabbitMQ).
- Resuelve la incógnita de enrutamiento por prefijo de ADR-0006 (consecuencias).
- No modifica ADR-0003 (topic `booking-events` intacto).

## References

- `specs/104-microservices-gateway/` (spec, plan, tasks, contratos, handoff)
- ADR-0001, ADR-0002, ADR-0003, ADR-0006
- Issue #103 (deuda de arquitectura previa a extraer servicios)
