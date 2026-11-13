# ORCHESTRATION: ejecutar HU-104 de una pasada (Opus orquesta y delega)

Quien orquesta (Opus, sesión principal) **no implementa todo**: reparte paquetes de trabajo (WP) con archivos disjuntos, integra, verifica y registra evidencia. Los WP están en `work-packages/`.

## 0. Prerrequisito del usuario (único paso manual antes de lanzar)
Los worktrees de subagentes y de `agy` solo ven contenido **commiteado**. El orquestador **no commitea** (AGENTS.md §3). El usuario debe hacer esto una vez:
```bash
git switch -c feature/104-microservices-gateway      # desde develop
git add docs/adr/0006-*.md docs/adr/0009-*.md docs/adr/README.md specs/104-microservices-gateway .specify/feature.json
git commit -m "docs(sdd): spec, plan, tasks y ADR-0009 de HU-104"
```
No incluir `specs/083-mentorship-base-service/` (no es de esta feature). Si el usuario no commitea, el orquestador trabaja **sin worktrees**: subagentes en el mismo árbol con propiedad de archivos disjunta (sección 3), de a uno por área.

## 1. Mecanismos de delegación (en orden de preferencia)
1. **Tool `Agent` con `isolation: "worktree"`**, un agente por WP, prompt = contenido del WP + "lee CLAUDE.md/AGENTS.md; devuelve informe de ≤ 30 líneas con archivos tocados, comandos ejecutados y resultado real". Fuente: HEAD de la rama de feature.
2. **`agy-worktree-orchestrator`** con `scripts/agent-worktree-agy.sh run --task-id WP-X --base feature/104-microservices-gateway --spec work-packages/WP-X.md --scope <rutas> --non-interactive` (cada WP ya incluye sección "Acceptance criteria"; `--scope` exige archivos/dirs existentes en la base y rechaza directorios top-level completos y globs, por lo que para módulos nuevos se pasa el directorio padre ya existente o se crea un directorio semilla commiteado). Si el script rechaza el scope, usar el mecanismo 1.
3. **Subagentes en el mismo árbol** (sin worktree) solo si los WP tocan archivos totalmente disjuntos.

Integración: el orquestador revisa el informe, **re-ejecuta los checks él mismo**, y aplica el parche (`git -C <worktree> diff > x.patch` → `git apply --check` → `git apply`) al árbol principal. Los resultados auto-reportados de un subagente se consideran **no verificados** hasta re-ejecutarlos. No hay commits ni `git stash/reset/checkout`.

## 2. Olas (cada una termina con puerta de verificación)

| Ola | Quién | Contenido | Paralelo | Puerta |
|---|---|---|---|---|
| **W0** | Orquestador (+ Explore read-only) | T001-T003, T005-T009: preflight, baseline, verificación `arm64`, mapa de acoplamiento de IA, spike native | Explore en paralelo para T005/T006/T009 | `progress.md` con baseline real (`measure/measure.sh ram baseline`, si Docker corre); Q1-Q7 aplicadas |
| **W1** | WP-A ∥ WP-B | A: gateway Traefik, middlewares, plugin, swagger, compose, guía Oracle. B: Kafka en el monolito con flags, idempotencia, DLT, tests | Sí (archivos disjuntos) | Humo verde vía gateway; `mvn test` del backend con Kafka; SC-001, SC-004 parcial |
| **W2** | Orquestador | Integrar A+B; apagar Rabbit con flags; T037 flujo completo; **T038 retirar RabbitMQ**; T039 ajuste Kafka | No | SC-002 verde con RabbitMQ ausente |
| **W3** | WP-C ∥ WP-D | `ai-service` (según regla Q4) y `notification-service` | Sí (módulos distintos) | Contract tests de cada uno; rutas **desactivadas por defecto** |
| **W4** | WP-E | `payment-service` (alto riesgo) | Solo | Tests negativos de propiedad, Stripe test, sin PAN/CVV en logs; ruta desactivada |
| **W5** | WP-F + Orquestador | Native por módulo (con salida a JVM), workflows CI/CD, backups/keep-alive, RAM final, docs, `speckit.analyze`, `code-review` | F por módulo en paralelo | SC-001..SC-007 con evidencia; ADR sigue **Proposed** |

**Cutover seguro**: las rutas nuevas (`ai`, `payment`) se entregan en `infra/traefik/dynamic/routers.cutover.yml`, **desactivado** salvo que el usuario lo habilite. Así la pasada deja código completo sin cambiar el tráfico real.

## 3. Propiedad de archivos (para evitar conflictos)

| Dueño | Rutas exclusivas |
|---|---|
| WP-A | `infra/traefik/**`, `infra/swagger-ui/**`, `infra/oracle/**`, `specs/104-.../smoke/**`, `docs/guides/oracle-free-deployment.md` |
| WP-B | `backend/src/main/java/**/messaging/**`, `backend/src/main/java/**/config/*Kafka*`, `*Rabbit*`, `backend/src/test/**` (mensajería), `backend/pom.xml` (solo deps de mensajería), `application*.yml` (solo claves `app.messaging`, `spring.kafka`) |
| WP-C | `ai-service/**` (nuevo) |
| WP-D | `notification-service/**` (nuevo) |
| WP-E | `payment-service/**` (nuevo) |
| WP-F | `.github/workflows/**`; perfil native en el `pom.xml` de **cada módulo** (solo cuando el dueño lo libere) |
| **Orquestador** | `docker-compose*.yml`, `CLAUDE.md`, `docs/**` (salvo guía Oracle), `specs/104-.../{progress,research}.md`, retirada de código en el monolito |

Los WP **no editan** `docker-compose*.yml`; devuelven el fragmento de servicio en su informe y el orquestador lo integra.

## 4. Reglas duras para todo el equipo de agentes
- `AGENTS.md` y la constitución aplican. Nunca: commit/stage, `git reset/checkout --/stash`, cambio de rama, `.env`/secretos, `docs/audit/*`, `scripts/repo_audit.py`, otras `specs/0*`, migraciones Flyway ya commiteadas (hook `protect-paths.sh`; crear `V<n+1>` si hace falta).
- Dominio sin imports de framework. `audit-service/` sin dependencia de compilación con `backend/` ni con servicios nuevos.
- Cada servicio nuevo: Maven independiente, hexagonal, actuator, springdoc, BD/schema propio.
- **Verdad**: "pasa" solo con salida de comando de esta sesión; si no se ejecutó, "No ejecutado en esta sesión". Docker puede no estar disponible: los tests con Testcontainers se reportan como no ejecutados en ese caso.
- No ejecutar acciones externas: crear issues/PR, desplegar a Oracle, push, publicar imágenes, llamar a Stripe real. Se preparan y se listan en `HUMAN-TODO.md`.
- No añadir funcionalidades fuera de `spec.md`; si algo bloquea, aplicar el defecto de `spec.md` o documentarlo en `progress.md` y seguir con lo desbloqueado.

## 5. Cuándo detenerse y preguntar (única lista)
1. Un cambio exigiría borrar o reescribir datos/migraciones aplicadas.
2. Se necesita un secreto, credencial o acceso a la VM.
3. Una tarea exige acción externa (ver 4).
4. Dos WP chocan en un archivo y no se resuelve con la tabla de propiedad.
Todo lo demás se resuelve con el defecto y se registra.

## 6. Cierre
- `progress.md` actualizado por ola con evidencia real y lista de lo **no ejecutado**.
- Revisión final: skill `speckit.analyze`; skill `code-review` sobre el diff; checklist de seguridad (pagos, JWT/CSRF, spoof de cabeceras).
- Informe final al usuario: qué quedó hecho, qué no se pudo verificar, `HUMAN-TODO.md` pendiente.
