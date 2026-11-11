# HU-26 — Avance de implementación (asistente RAG)

> Documento de avance temporal. Se actualiza en cada pausa o cierre de carril. Fuente de tareas: [tasks.md](tasks.md).

Estado al 2026-11-11 (pausa solicitada). Rama: `feature/026-rag-knowledge`.

## Resumen

- **Terminadas e integradas: 28 de 41** (ver tabla).
- **Pausadas (sin cambios, se retoman):** L7 (T026, T029) y L8 (cableado de arranque, T025, T027, T034, T035). Sus worktrees siguen en disco pero sin ningún cambio ni reporte real; hay que relanzarlos.
- **Pendientes de agente:** T025, T026, T027, T029, T034, T035, T038, T040, T041.
- **Pendientes del propietario:** T002 y T003 (Railway y consola de Google), T030 (umbral `minScoreThreshold` y `rrfK` tras el informe real), T039 (enmienda de la constitución).

## Tareas integradas

| Commit | Tareas | Verificación |
|---|---|---|
| `c997a18` | SDD: spec, plan, tasks, data-model, contrato, quickstart | — |
| `93d7557` | T008, T009, T010 dominio puro: coseno, BM25, RRF | 29 pruebas, JDK 21 |
| `0473ff0` | T007 modelos de dominio | 52 pruebas (anidadas) |
| `72fdac3` | T013 propiedades `app.rag`, sin umbral inventado | 5 pruebas |
| `2b4ea4c` | T001 propiedades de Spring AI 1.1.8 verificadas en el jar | — |
| `eccea1a` | T005 migración `V14__knowledge_chunks.sql` | aplicada en pgvector/pg17 (T004) |
| `c416f31` | T006 imagen `pgvector/pgvector:pg17` | test-compile |
| `e0c086d`, `158783e` | T011 cargador del corpus, T012 puertos; corrección de segmentos `docs`/`knowledge` | 113 pruebas |
| `26eb54c` | T004 spike Docker: V14 aplicada, HNSW sobre `vector(768)` | prueba de humo |
| `8c147e3` | T011-R I/O de archivos fuera de `application` | 101 pruebas |
| `0de7a9e` | T015 adaptador pgvector | 130 pruebas |
| `a14a6fb` | T017 `KnowledgePromptBuilder`, `SourceCitationValidator` | 169 pruebas |
| `6ea943a` | T014 adaptador de embeddings Gemini; exclusión de autoconfigs sin clave | 13 pruebas + contexto sin clave |
| `d7045f6` | T016 prueba pgvector con Testcontainers; `ddl-auto: validate` | 5/5 con PostgreSQL 17 real |
| `0c6fbb3` | T018 adaptador de respuesta LLM | 10 pruebas |
| `bfffc5d` | T031 cliente HTTP del frontend | tsc app+spec; Karma 15/15 |
| `5b0aaa2` | T028 borrador de evaluación aprobado (42 preguntas, 12 sin respuesta) | JSON válido |
| `32ef99f` | T036 sin mocks silenciosos | 8 pruebas |
| `5898782` | T019 `AskKnowledgeService` | 16 pruebas |
| `cbfc1c4` | corrección de aislamiento: `app.ai.provider` se filtraba entre pruebas | suite IA+conocimiento 216/216 |
| `879e4ed` | documentación de avance y ejemplos de sourceId corregidos | — |
| `5953f78` | T032 chat del frontend con pestaña de conocimiento | tsc app+spec; Karma 9/9 |
| `55b25e3` | T037 higiene de logs | 4 pruebas |
| `91e6d3f` | T020, T021 controlador `POST /api/ai/knowledge/ask` | 9 pruebas MockMvc; 229 en el conjunto |
| `8dba831` | T022, T023, T024 índice y sincronización | 267 en el conjunto |

T033 queda cubierta por T032: sus pruebas de 503 y timeout con reintento y conservación del texto ya existen.

## Carriles pausados (retomar)

| Carril | Tareas | Estado | Acción al retomar |
|---|---|---|---|
| L7 | T026 → T029 | sin cambios; sin reporte real | relanzar con la misma spec |
| L8 | cableado de arranque → T025 → T027 → T034 → T035 | sin cambios; sin reporte real | relanzar con la misma spec |

**Hallazgo a resolver antes del arranque real:** la carga inicial del índice existe (`KnowledgeIndexService.loadInitialIndex()`), pero no está conectada al arranque. Eso es el primer paso de L8. Mientras tanto el índice queda vacío al levantar el backend.

## Decisiones pendientes del propietario

1. **`snippet` de cada fuente** (T019): hoy es el contenido completo del fragmento. ¿Se recorta (p. ej. 200 caracteres) o se devuelve completo?
2. **Respuesta con contexto pero sin citas** (T019): si el LLM no cita ninguna fuente, la respuesta sale con `hasSufficientContext: true` y `sources` vacío. ¿Se marca como sin información?
3. **Umbral `minScoreThreshold` y `rrfK`** (T030): dependen del informe real con Gemini. El informe de T029 será con dobles deterministas y así debe leerse.
4. **Enmienda de la constitución** para listar `EmbeddingPort` y los puertos de conocimiento (T039).

## Problemas conocidos

- **Corregido en esta ronda:** `app.ai.provider` se filtraba entre pruebas (cbfc1c4). Los ids de ejemplo `cancellation-policy` se cambiaron por `policy-cancellation` (879e4ed).
- **Cliente del frontend (T031):** dos caminos (HttpClient y fetch) y un objeto que mezcla Observable con Promise. Funciona y está probado; es más de lo que pedía la spec.
- **Prueba de no-mocks (T036):** una aserción de mensaje es débil. No afecta el resultado.
- **Cambio grande en el chat (T032):** el diff es grande porque el contenido de recomendaciones se movió dentro de bloques `@if`. El modo por defecto sigue siendo recomendaciones.
- **Autenticación de GitHub:** la sesión `gh` caducó; hay que volver a iniciar sesión antes de hacer push.

## Verificación

Cifras de ejecuciones con JDK 21 (`/home/santiagojava/.jdks/azul-21.0.12.1`), en el worktree de integración o en el del worker antes de integrar. Las pruebas de Docker usan Testcontainers real (PostgreSQL 17 con pgvector 0.8.7). Lo que no se ha ejecutado se marca en la tarea correspondiente.
