# .specify/ — GitHub Spec Kit Configuration & Assets

Estructura oficial de configuración y artefactos de **GitHub Spec Kit** adaptada para **SkillBridge AI**.

## Estructura del directorio

```text
.specify/
├── memory/
│   └── constitution.md        # Constitución técnica, arquitectónica y de gobernanza de SkillBridge AI (v1.0.0)
├── templates/
│   ├── spec-template.md       # Plantilla de especificación funcional (user stories, requisitos, criterios de aceptación)
│   ├── plan-template.md       # Plantilla de plan técnico (arquitectura, dependencias, modelo de datos, contratos)
│   ├── tasks-template.md      # Plantilla de desglose de tareas ordenadas por dependencia y fase
│   ├── checklist-template.md  # Plantilla de listas de chequeo y puertas de calidad
│   ├── agent-file-template.md # Plantilla para generación y actualización de contexto de agentes
│   └── constitution-template.md # Plantilla base de constitución
├── scripts/
│   └── bash/
│       ├── check-prerequisites.sh # Verificación de prerequisitos y extracción de metadatos de feature
│       ├── common.sh              # Biblioteca compartida de utilidades para scripts bash de Spec Kit
│       ├── create-new-feature.sh  # Creación automatizada de ramas de feature y carpetas specs/<NNN>-<slug>/
│       ├── resolve-template.sh    # Resolución jerárquica de plantillas a través de la pila de overrides
│       ├── setup-plan.sh          # Inicialización del plan de implementación para la feature activa
│       ├── setup-tasks.sh         # Inicialización del archivo de tareas para la feature activa
│       └── update-agent-context.sh # Actualización de archivos de contexto de agentes (CLAUDE.md, AGENTS.md)
└── README.md                  # Este documento
```

## Flujo Spec-Driven Development (SDD)

1. **Constitution** (`speckit.constitution`): Establece los invariantes arquitectónicos, reglas de seguridad y estándares de verificación en `.specify/memory/constitution.md`.
2. **Specify** (`speckit.specify`): Genera la especificación funcional en `specs/<NNN>-<slug>/spec.md` a partir de la User Story.
3. **Clarify** (`speckit.clarify`): Resuelve ambigüedades e identifica casos borde antes de la planificación.
4. **Plan** (`speckit.plan`): Desarrolla el diseño técnico en `specs/<NNN>-<slug>/plan.md` verificando la constitución.
5. **Tasks** (`speckit.tasks`): Desglosa la implementación en tareas independientes y testeables en `specs/<NNN>-<slug>/tasks.md`.
6. **Analyze** (`speckit.analyze`): Verifica la consistencia cruzada entre spec, plan y tasks.
7. **Implement** (`speckit.implement`): Ejecuta las tareas en fases delegando a worktrees aislados.
8. **Checklist** (`speckit.checklist`): Valida puertas de calidad y requisitos no funcionales.
