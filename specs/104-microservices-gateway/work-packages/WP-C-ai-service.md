# WP-C — Módulo `ai-service` (tareas T040-T045)
**Léelo junto con**: `CLAUDE.md`, `AGENTS.md`, `specs/104-microservices-gateway/{spec.md,plan.md,tasks.md}` y `contracts/`. Reglas: no commit/stage, no cambiar de rama, no secretos/`.env`, no editar `docker-compose*.yml` (devuelve el fragmento de servicio en tu informe), no acciones externas. Reporta solo lo que ejecutaste; si no, "No ejecutado en esta sesión". Informe final ≤ 30 líneas: archivos tocados, comandos y resultado real, pendientes.

## Scope
`ai-service/**` (directorio nuevo; crear con un archivo semilla si el mecanismo lo exige). **Forbidden**: `backend/**` (el código se **copia/adapta**, el monolito no se toca en esta ola), `audit-service/**`, `docker-compose*.yml`.

## Regla de alcance (Q4)
Mapear con `grep` qué tablas/repositorios usan `AiRecommendationService`, la cadena de fallback (ADR-0007) y los adaptadores de `adapter/out/ai/`.
- Si recomendación/visión/STT/TTS necesitan ≤ 2 tablas del monolito → read model propio alimentado por eventos de `booking-events`.
- **RAG/knowledge (`/api/ai/knowledge`) y pgvector NO se mueven**: permanecen en el monolito.
- Documentar la decisión en `ai-service/README.md` y en `specs/104-.../research.md`. Si el acoplamiento es mayor, entrega solo el esqueleto + el informe y no inventes datos.

### Decisión ya tomada en W0 (T005, ver `research.md` §Q4) — vinculante
- `ai-service` atiende `POST /api/ai/recommendations{,/voice,/image}`, `POST /api/ai/tts` y `/api/admin/ai/providers/**`.
- `/api/ai/knowledge/**` y `GET /api/ai/trending` **se quedan en el monolito**.
- Read model `ai.offering_view` alimentado por el `eventType` `OfferingUpserted` v1 en `booking-events` (clave `offeringId`; payload = estado completo de `Offering`: id, providerId, title, description, category, price, active, mode, durationHours). El orquestador añade el productor en el monolito; tú solo el consumidor (idempotente, group-id `ai-service`).
- Preselección RAG desactivada en `ai-service` (`KnowledgeSearchPort` ausente → catálogo completo, comportamiento ya soportado).
- Actor desde el JWT (claims `uid`, `role`, `sub`=email); sin tabla `users`.
- Los eventos `RecommendationRequested/Generated` se publican igual que hoy en `booking-events` (mismo envelope).

## Entregables
Maven independiente (Java 21, Spring Boot 3.5.6), hexagonal, actuator, springdoc, Resilience4j, JWT RS256 con clave pública (`JWT_PUBLIC_KEY`) y CSRF como el monolito, schema propio `ai` con Flyway propio, Dockerfile JVM afinada (`-XX:+UseSerialGC -Xss256k -XX:MaxRAMPercentage=70`), contract tests contra las respuestas del monolito, fragmento de Compose en el informe.

## Acceptance criteria
- `cd ai-service && mvn test` ejecutado y reportado.
- Contrato de `/api/ai/**` (excepto knowledge) idéntico en forma al del monolito (test).
- Dominio sin frameworks; ninguna dependencia de compilación con `backend/`.
- El servicio arranca sin RabbitMQ y sin acceso a la BD del monolito.
- Sin cambios fuera de `ai-service/`.
