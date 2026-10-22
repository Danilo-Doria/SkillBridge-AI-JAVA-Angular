# specs/ — Especificaciones Spec-Driven Development (GitHub Spec Kit)

Este directorio alberga los artefactos de diseño, análisis y planificación de funcionalidades de **SkillBridge AI**, gestionados conforme al estándar de **GitHub Spec Kit**.

Cada funcionalidad o User Story (HU) cuenta con su propio subdirectorio aislado: `specs/<NNN>-<slug>/`.

---

## 1. Convención de Nomenclatura y Enlace a GitHub

| Elemento | Convención | Ejemplo |
|---|---|---|
| **Directorio de Feature** | `specs/<NNN>-<slug>/` | `specs/025-ai-recommendations/`, `specs/027-booking-rescheduling/` |
| **Prefijo Numérico `<NNN>`** | 3 dígitos con relleno de ceros coincidente con la User Story / Issue de GitHub (`HU-X` → `00X-`, `HU-25` → `025-`) | `HU-25` → `025-`, `HU-27` → `027-` |
| **Rama Git de Feature** | `feature/<NNN>-<slug>` | `feature/025-ai-recommendations`, `feature/027-booking-rescheduling` |
| **Enlace a Issue en `spec.md`** | Campo obligatorio en los metadatos de cabecera enlazando el Issue de GitHub | `**Issue**: [HU-25](https://github.com/riwi-io/SkillBridge-AI/issues/25) (#25)` |

---

## 2. Ciclo de Vida de Artefactos por Feature

Dentro de cada directorio `specs/<NNN>-<slug>/` residen los documentos de las distintas fases SDD:

```text
specs/<NNN>-<slug>/
├── spec.md              # Fase 1: Especificación funcional (Qué y Por qué). Aprobada por el Humano.
├── plan.md              # Fase 2: Plan de implementación técnica (Cómo). Aprobada por el Humano.
├── tasks.md             # Fase 3: Desglose ejecutable de tareas y dependencias. Base de delegación.
├── research.md          # Artefacto de apoyo: Investigación previa, alternativas evaluadas y decisiones.
├── data-model.md        # Artefacto de apoyo: Entidades de dominio, modelos JPA y mapeos.
├── contracts/           # Artefacto de apoyo: Contratos OpenAPI / esquemas de eventos.
└── checklists/          # Artefacto de apoyo: Listas de verificación de calidad y puertas de revisión.
```

1. **`spec.md`** (Generado con `/speckit.specify` o `/speckit.clarify`):
   - Define el problema, historias de usuario priorizadas (P1: MVP, P2, P3), criterios de aceptación en formato Given-When-Then y casos borde.
   - Incluye el enlace formal al Issue de GitHub correspondiente.
   - **Puerta de Calidad**: Requiere revisión y aprobación explícita de un humano antes de pasar al plan.
2. **`plan.md`** (Generado con `/speckit.plan`):
   - Traduce la especificación en una arquitectura ejecutable respetando la constitución de SkillBridge AI (`.specify/memory/constitution.md`).
   - Define puertos entrantes/salientes, adaptadores, DTOs, cambios en base de datos y estrategias de observabilidad.
   - **Puerta de Calidad**: Requiere revisión y aprobación explícita de un humano antes de desglosar tareas.
3. **`tasks.md`** (Generado con `/speckit.tasks`):
   - Desglosa el plan en tareas unitarias, independientes y ordenadas por fase/dependencia con formato `[T001] [P?] [US1] Descripción`.
   - Cada tarea o fase de tareas se convierte en la entrada (`--spec`) para delegación a agentes en worktrees aislados.
4. **Artefactos auxiliares**:
   - `research.md`, `data-model.md`, `contracts/` y `checklists/` proporcionan contexto profundo bajo demanda para los agentes de implementación.

---

## 3. Reglas de Desarrollo en Paralelo y Concurrencia

Para garantizar que múltiples ingenieros y agentes puedan trabajar simultáneamente sin conflictos ni pisado de trabajo:

1. **Aislamiento de Directorio**: Cada agente o desarrollador trabaja exclusivamente en el directorio `specs/<NNN>-<slug>/` de su funcionalidad asignada.
2. **Protección de Features Paralelas**:
   - Está terminantemente prohibido modificar, sobreescribir o borrar directorios de otras funcionalidades (`specs/0*`) en ramas o worktrees ajenos.
3. **Flujo de Ramas**:
   - Cada feature se desarrolla en su rama `feature/<NNN>-<slug>` creada a partir de `develop` (o la base confirmada).
   - Los cambios de implementación se delegan a worktrees de corta duración mediante `scripts/agent-worktree-agy.sh`.
   - La integración a la rama principal de la feature se efectúa mediante revisión de diffs y pruebas verificadas.
