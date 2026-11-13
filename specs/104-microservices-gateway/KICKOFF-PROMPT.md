# Prompt de arranque para Opus (copiar y pegar en la sesión nueva)

```
Ejecuta HU-104 de una sola pasada en este repo.

Lee, en este orden y nada más al inicio: CLAUDE.md, AGENTS.md, .specify/memory/constitution.md,
specs/104-microservices-gateway/{HANDOFF.md, ORCHESTRATION.md, spec.md, plan.md, tasks.md},
contracts/*.md y work-packages/*.md.

Tú eres el orquestador: sigue ORCHESTRATION.md (olas W0→W5). Delega los WP-A..WP-F con el tool Agent
(isolation: worktree) o con agy-worktree-orchestrator según la sección 1, en paralelo donde la tabla lo
permite. Revisa cada informe, re-ejecuta tú los checks, integra los parches y registra evidencia real en
progress.md. Aplica los defectos de la sección "Decisiones por defecto" de spec.md; no me preguntes lo que
ya tiene defecto. Solo detente por la lista de la sección 5 de ORCHESTRATION.md.

Reglas: no commitear ni hacer stage, no cambiar de rama, no tocar secretos/.env, no hacer acciones externas
(issues, PR, push, despliegue, Stripe real). Lo que dependa de mí va a HUMAN-TODO.md. Reporta tests solo si
los ejecutaste; si no, "No ejecutado en esta sesión". El ADR-0009 permanece Proposed.

Al terminar: speckit.analyze, code-review del diff y un informe final corto.
```

Notas para el usuario:
- Lanza en la rama `feature/104-microservices-gateway` con los docs ya commiteados (ver ORCHESTRATION.md §0).
- Modelo recomendado: Opus para el orquestador; los WP pueden correr en el modelo por defecto de subagentes.
- Si Docker no está activo en la máquina, los tests con Testcontainers quedarán como "no ejecutados".
