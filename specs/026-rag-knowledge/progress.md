# HU-26 — Avance de implementación (asistente RAG)

Estado al 2026-11-11. Rama: `feature/026-rag-knowledge`. Fuente de tareas: [tasks.md](tasks.md) (41 tareas).

## Resumen

- **Terminadas e integradas:** 20 de 41.
- **En curso, sin integrar:** T032, T037 (carriles L2 y L4), T020 y T021 (L5), T022, T023 y T024 (L6).
- **Pendientes de agente:** T025 a T027, T029, T033 a T035, T038, T040, T041.
- **Pendientes del propietario:** T002 y T003 (Railway y consola de Google), T030 (umbral `minScoreThreshold` y `rrfK` tras el informe de evaluación), T039 (enmienda de la constitución).

## Tareas integradas

| Commit | Tareas | Verificación |
|---|---|---|
| `c997a18` | SDD: spec, plan, tasks, data-model, contrato, quickstart | — |
| `93d7557` | T008, T009, T010 dominio puro: coseno, BM25, RRF | 29 pruebas, JDK 21 |
| `0473ff0` | T007 modelos de dominio | 52 pruebas (anidadas) |
| `72fdac3` | T013 propiedades `app.rag`, sin umbral inventado | 5 pruebas |
| `2b4ea4c` | T001 verificación de propiedades de Spring AI 1.1.8 (verificado en el jar) | — |
| `eccea1a` | T005 migración `V14__knowledge_chunks.sql` | aplicada en pgvector/pg17 (T004) |
| `c416f31` | T006 imagen `pgvector/pgvector:pg17` en compose y tests | test-compile |
| `e0c086d`, `158783e` | T011 cargador del corpus, T012 puertos; corrección de segmentos `docs`/`knowledge` | 113 pruebas |
| `26eb54c` | T004 spike Docker: V14 aplicada, HNSW sobre `vector(768)`, búsqueda coseno | prueba de humo |
| `8c147e3` | T011-R I/O de archivos fuera de `application` | 101 pruebas |
| `0de7a9e` | T015 adaptador pgvector `KnowledgeSearchPort`/`KnowledgeIndexPort` | 130 pruebas |
| `a14a6fb` | T017 `KnowledgePromptBuilder`, `SourceCitationValidator` | 169 pruebas |
| `6ea943a` | T014 adaptador de embeddings Gemini; exclusión de autoconfigs sin clave | 13 pruebas + contexto sin clave |
| `d7045f6` | T016 prueba pgvector con Testcontainers; `ddl-auto: validate` | 5/5 con PostgreSQL 17 real |
| `0c6fbb3` | T018 adaptador de respuesta LLM | 10 pruebas |
| `bfffc5d` | T031 cliente HTTP del frontend | tsc app+spec; Karma 15/15 |
| `5b0aaa2` | T028 borrador de evaluación aprobado (42 preguntas, 12 sin respuesta) | JSON válido |
| `32ef99f` | T036 sin mocks silenciosos | 8 pruebas |
| `5898782` | T019 `AskKnowledgeService` | 16 pruebas |
| `cbfc1c4` | corrección de aislamiento: `app.ai.provider` se filtraba entre pruebas | 216/216 suite IA+conocimiento |

## Decisiones pendientes del propietario

1. **`snippet` de cada fuente** (T019): hoy es el contenido completo del fragmento, que puede ser largo. ¿Se recorta (p. ej. 200 caracteres) o se devuelve completo?
2. **Respuesta con contexto pero sin citas** (T019): si el LLM no cita ninguna fuente, la respuesta sale con `hasSufficientContext: true` y `sources` vacío. ¿Se marca como sin información?
3. **Umbral `minScoreThreshold` y `rrfK`** (T030): dependen del informe de evaluación (T029).
4. **Enmienda de la constitución** para listar `EmbeddingPort` y los puertos de conocimiento (T039).

## Problemas conocidos

- **Lo corregido en esta ronda:** `app.ai.provider` se filtraba entre `GeminiKnowledgeAnswerAdapterTest` y `GeminiAiAdapterTest` (cbfc1c4). También se corrigió el id de ejemplo `cancellation-policy` por `policy-cancellation` en el contrato, el quickstart y `tasks.md`.
- **Cliente del frontend (T031):** tiene dos caminos (HttpClient y fetch) y extiende el Observable con métodos de Promise. Funciona y está probado, pero es más de lo que pedía la spec. Limpieza posterior.
- **Prueba de no-mocks (T036):** una aserción de mensaje es débil (`contains("unavailable")`). No afecta el resultado.
- **Pruebas de aislamiento:** la suite completa de IA y conocimiento se corrió al final de esta ronda. Conviene correr la suite completa del backend (`mvn test`) antes de la integración final (T040).
- **Tiempos de arranque de Testcontainers:** el test de pgvector tarda ~12 s. Aceptable.

## Verificación

Todas las cifras son de ejecuciones con JDK 21 (`/home/santiagojava/.jdks/azul-21.0.12.1`) en el worktree de integración o en el worktree del worker antes de integrar. Las pruebas de Docker usan Testcontainers real (PostgreSQL 17 con pgvector 0.8.7). Lo que no se ha ejecutado se marca en la tarea correspondiente.
