# HANDOFF: continuar HU-104 en una sesión nueva (Opus)

> **Ejecución de una pasada**: usa [`ORCHESTRATION.md`](ORCHESTRATION.md) (olas, paquetes de trabajo, reglas de delegación) y pega [`KICKOFF-PROMPT.md`](KICKOFF-PROMPT.md). Las preguntas abiertas ya tienen defecto vinculante en `spec.md` (sección Decisiones por defecto); lo que solo el usuario puede aportar está en [`HUMAN-TODO.md`](HUMAN-TODO.md). Donde este documento dice "pedir al usuario", **no te detengas**: aplica el defecto.

Documento de arranque. Léelo primero; no necesitas el historial de la sesión anterior.

## Qué se decidió (no reabrir sin motivo)
1. **Plataforma**: una VM Oracle Cloud Always Free ARM (aarch64), Docker Compose, sin Kubernetes.
2. **Gateway**: **Traefik v3** (no Spring Cloud Gateway). Rate limiting, circuit breaker, propagación de `X-Correlation-Id` y Swagger agregado. Sin ForwardAuth: cada servicio valida JWT RS256.
3. **Un solo broker: Kafka.** RabbitMQ se retira (pagos y notificaciones pasan a `payment-events` y `notification-requests`).
4. **GraalVM native** por servicio Spring, con salida a JVM afinada si no es viable. Compilar en CI, no en la VM.
5. **Servicios a extraer, en orden**: `ai-service`, `notification-service`, `payment-service`. `identity/catalog/booking` quedan para una compuerta posterior.

## Qué ya existe (creado en la sesión anterior, sin commit)
- `docs/adr/0009-edge-gateway-kafka-only-native-images.md` (**Proposed**)
- `docs/adr/README.md` (fila 0009; siguiente libre 0010) y errata al final de `docs/adr/0006-*.md`
- `specs/104-microservices-gateway/`: `spec.md`, `plan.md`, `tasks.md`, `research.md`, `progress.md`, `contracts/gateway-routes.md`, `contracts/kafka-topics.md`, este `HANDOFF.md`

**No se escribió código, ni se ejecutaron tests, ni se levantó infraestructura.** Todo lo técnico marcado `[INFERRED]`/`[UNKNOWN]` está sin verificar.

## Orden de lectura
1. `CLAUDE.md` y `AGENTS.md` (reglas duras: no commitear, no `git reset/stash/checkout`, no tocar `.env`/secretos, no tocar `docs/audit/findings.md`, `docs/audit/evidence/`, `scripts/repo_audit.py`, ni otras carpetas `specs/0*`).
2. `.specify/memory/constitution.md` y `docs/agents/SDD-HYBRID-GUIDE.md`.
3. `docs/adr/0006-hybrid-monolith-and-services.md` + errata, y `0009-*`.
4. `specs/104-microservices-gateway/spec.md` → `plan.md` → `tasks.md` → `contracts/`.
5. Solo lo necesario por tarea (tabla de enrutamiento de contexto de `CLAUDE.md`).

## Primer movimiento recomendado
1. Pedir al usuario que **apruebe o corrija** spec + ADR-0009 (siguen en Draft/Proposed). Un ADR no pasa a Accepted sin aprobación humana.
2. Resolver con el usuario las preguntas **bloqueantes**: Q1 (dominio/DNS), Q2 (¿repo público?), Q3 (equipo NestJS y Kafka). El resto se investiga en la Fase 0.
3. Ejecutar **Fase 0** (T001-T009) y registrar evidencia en `progress.md`.
4. Después **Fase 1** (gateway): es el MVP, bajo riesgo y desplegable por sí sola.
5. Usar `/speckit.analyze` antes de empezar a implementar para validar consistencia spec/plan/tasks.

## Cosas que debes vigilar
- **Contrato de mentorías**: `docs/contracts/mentorships/events.md` y los issues #83-#91 asumen RabbitMQ. No edites esos contratos (es otro equipo): abre issue de coordinación (con permiso del usuario) y no ejecutes T038 (retirar RabbitMQ) hasta resolverlo.
- **Issue #103**: deuda que bloquea extraer identity/catalog/booking. No la abordes aquí salvo lo mínimo que `ai/payment/notification` exijan.
- **Pagos**: nunca PAN/CVV; tokenización Stripe; validar propiedad de la reserva; webhook con cuerpo crudo.
- **`ai-service` y sus datos** (Q4): puede depender de tablas del monolito (ofertas, pgvector). Decide con evidencia antes de cortar; aplazar es una salida válida.
- **IP real y rate limit**: sin `trustedIPs`/`ipStrategy` correctos limitarás a todos juntos.
- **`.specify/feature.json`** (sin seguimiento) apunta a `specs/026-rag-knowledge`; el usuario no autorizó cambiarlo. Si usas comandos `/speckit.*`, apúntalo a `specs/104-microservices-gateway` y avísalo.
- **Número de feature**: `104` es provisional; el último issue visto fue #103. Si el issue real difiere, renombra carpeta y rama y corrige referencias (`grep -rn "104-microservices-gateway"`).
- **Arquitectura ARM**: toda imagen debe ser `linux/arm64`.
- Un servicio que no funcione en native **no es un fallo**: documentar y dejar JVM afinada.

## Estrategia de ejecución sugerida
- Fases 0-2 en serie en la rama `feature/104-microservices-gateway` (no cambies de rama sin que el usuario lo pida; tampoco `git stash`).
- Fases 3 y 4 son independientes: pueden ir en worktrees separados con `scripts/agent-worktree-agy.sh` y el agente `agy-worktree-orchestrator`, **solo** con spec/plan/tasks revisados (ya existen) y una tarea acotada por worktree.
- Fase 5 (pagos) en serie y con revisión humana antes de mover la ruta.
- Cada fase: humo verde, tests **ejecutados y reportados** (`mvn test`, `mvn verify` si hay Docker, `npm test -- --watch=false`), docs actualizadas, reversión probada.

## Definition of Done global
SC-001..SC-007 de `spec.md` con evidencia en `progress.md`; ADR-0009 en Accepted por aprobación humana; `docs/` y `CLAUDE.md` actualizados; sin secretos; sin commits automáticos (los hace el usuario).
