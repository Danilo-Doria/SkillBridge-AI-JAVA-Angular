# Decisiones RAG-001 y SPARK-001 — material de estudio

Redactado por el coordinador el 2026-10-22 tras revisar los informes de AGY y **re-ejecutar las pruebas**. Estado de ambas tareas: **REVIEW** (nada integrado, nada commiteado). Base común: `1d5dde4`.

Marcas: `[VERIFIED]` comprobado por el coordinador en esta sesión · `[REPORTED]` afirmado por el agente, no re-verificado · `[INFERRED]` deducción.

| | SPARK-001 | RAG-001 |
|---|---|---|
| Decisión | **No adoptar Spark.** Mantener el consumidor Kafka incremental; si hiciera falta batch, DuckDB. | **Implementar un RAG mínimo con BM25 en memoria**, sin embeddings ni base vectorial. |
| Veredicto del coordinador | ready-for-review con 2 retoques | **needs-fixes** (3 puntos) |
| Run | `SPARK-001-20261022T002200Z`, 00:23Z → 00:41Z, exit 0, 0 violaciones | `RAG-001-20261022T002200Z`, 00:29Z → 01:04Z, exit 0, 0 violaciones |
| Rama / worktree | `agent/SPARK-001-20261022T002200Z` | `agent/RAG-001-20261022T002200Z` |
| Pruebas re-ejecutadas | 6/6 pasan `[VERIFIED]` | `mvn -o test` exit 0, 0 fallos, 1 omitida; 32 pruebas RAG en 8 clases `[VERIFIED]` |

Raíz de worktrees: `/home/santiagojava/IdeaProjects/rejuv-wt/SkillBridge-AI-JAVA-Angular-agent-worktrees/<run>/` (contiene `report.md`, `changes.patch`, `summary.md`).

---

## 1. SPARK-001 — por qué NO adoptar Spark

**Pregunta:** ¿justifican los eventos de reservas/recomendaciones un procesamiento distribuido?

**Evidencia `[VERIFIED]`**
- El repositorio solo trae datos de demostración: 3 usuarios (`V2__seed_data.sql`) y 3 ofertas (`V1__init.sql`), sin reservas ni eventos. No hay volumen que medir. (El informe de Spark dice "cero ofertas"; solo es cierto para `V2`, lo corregí tras comprobar `V1`.)
- La analítica ya existe y es incremental: `KafkaAnalyticsConsumer` (grupo `skillbridge-analytics-group`) actualiza `recommendation_daily_metrics` por evento; `DemandAnalyticsService` calcula tendencia sobre una ventana de 14 días.
- Los eventos viajan por un único topic, `booking-events` (ADR 0003).

**Qué se construyó:** PoC aislada en `scripts/spark-analytics/` (la ruta preferida `experiments/` no existe en el commit base y el orquestador la rechaza). Tres motores calculan la misma métrica sobre eventos con el esquema de `BusinessEvent`: PySpark 4.2.0, DuckDB 2.0.0 y Python puro. Los datos son **sintéticos** y están etiquetados como tales (`_data_origin`). Hay 6 pruebas: igualdad exacta entre motores, dataset vacío, idempotencia por `eventId`, cancelación sin creación previa, fechas límite y reproducibilidad.

**Resultados medidos por el agente `[REPORTED]`** (sin re-ejecutar el benchmark)

| Eventos | Spark | DuckDB | Python puro |
|---|---|---|---|
| 10⁴ | 1,39 s | 0,10 s | 0,04 s |
| 10⁵ | 1,50 s | 0,18 s | 0,45 s |
| 10⁶ | 5,92 s | 1,48 s | 5,60 s |

Lectura: Spark paga una sobrecarga fija de arranque y planificación que no amortiza en una sola máquina con estos volúmenes. Con 16 GB de RAM y datos que caben en memoria, DuckDB gana a todos los tamaños.

**Alternativas comparadas (resumen):** consumidor Kafka incremental (existente, tiempo real, coste cero); consultas o vistas materializadas en PostgreSQL; DuckDB sobre volcados (batch, ~50–200 MB); Kafka Streams (más complejo); Spark (2–8 GB por ejecutor, clúster o pod pesado).

**Cuándo reconsiderar `[INFERRED]`, umbrales propuestos por el agente:** más de 500.000 eventos/día (hoy menos de 1.000), más de 100 GB de historial analítico que no quepa en un nodo, o más de 50 pipelines analíticos concurrentes o modelos distribuidos.

**Retoques antes de aceptar**
1. `requirements.txt` usa `>=`; la spec pedía versiones fijadas. Fijar `pyspark==4.2.0`, `duckdb==2.0.0`, `pytest==9.1.1`, `psutil==7.2.2`.
2. El informe marca `[VERIFIED]` el "punto de cruce a 1,2–1,5 M de eventos" entre Spark y Python puro. Es una extrapolación: solo se midió hasta 10⁶ y ahí Python (5,60 s) aún no pierde frente a Spark (5,92 s). Debe leerse como `[INFERRED]`.

**Limitaciones:** los benchmarks dependen de esta máquina y de datos sintéticos; sirven para comparar motores, no para predecir producción. El `.venv` (584 MB) se borró del worktree para liberar disco; `./run.sh` lo recrea.

---

## 2. RAG-001 — qué se decidió y qué hay que corregir

**Pregunta:** ¿qué RAG respalda el conocimiento real del producto?

**Evidencia `[VERIFIED]`:** el corpus real es pequeño: 3 ofertas semilla (según el informe, en `V1__init.sql`) y 3 documentos Markdown nuevos (~10 KB, menos de 25 fragmentos) más la política de cancelación (24 h de antelación mínima).

**Decisión del agente:** con ese tamaño, BM25 léxico en memoria basta. Se descartan embeddings remotos y pgvector (sin servicios nuevos, sin coste, determinista, menos de 1 ms `[REPORTED]`).

**Qué se construyó (29 archivos nuevos, ninguno existente modificado)**
- `POST /api/ai/knowledge/ask`, autenticado (401 anónimo); petición `{question}` con un máximo de 500 caracteres; respuesta `{answer, hasSufficientContext, sources[]}`.
- Puertos `AskKnowledgeUseCase` y `KnowledgeSearchPort`, servicio `AskKnowledgeService`, adaptadores BM25 y LLM, `PromptSanitizer` y `KnowledgePromptBuilder` (contexto límite de 6.000 caracteres).
- Si la recuperación no encuentra nada, **no se llama al LLM** y se responde "sin información". Solo se indexan ofertas activas y documentos públicos. Se publican eventos de auditoría de longitud y recuento, sin el texto de la pregunta.
- Comprobado en código `[VERIFIED]`: el prompt completo con los fragmentos sí llega al LLM real (el servicio lo construye y se lo pasa; el parámetro se llama `question` por error de nombre).

**Por qué es needs-fixes (hallazgos del coordinador, `[VERIFIED]` por lectura de código)**
1. **Respaldo silencioso a un mock con textos y precios fijos.** `SpringAiKnowledgeLlmAdapter` es `@Primary` y, con `provider=auto` (el valor por defecto), cae a `DeterministicMockKnowledgeLlmAdapter` cuando no hay `ChatClient`, cuando la clave falta o es inválida o cuando hay `IllegalArgumentException`. El mock responde plantillas con precios escritos en el código (85.000, 75.000 y 120.000 COP) y la política de cancelación, **no** derivados de los fragmentos, y las firma con `[Fuente: …]`. En producción, una clave caducada produciría respuestas que parecen fundamentadas y pueden quedar obsoletas. Es justo el tipo de "falsa demostración" que la spec prohibía. Corrección: mock solo con `provider=mock` explícito (o perfil de pruebas); sin clave, error 503 controlado.
2. **El timeout no se aplica.** `RagProperties.timeoutSeconds` (15 s) solo tiene getter y setter; no hay ninguna llamada que lo use. El timeout se detecta únicamente buscando "timeout" en el mensaje de la excepción. Corrección: aplicar el límite en el cliente HTTP/`ChatClient` o con `Future.get(timeout)`.
3. **Las fuentes devueltas no se validan contra la respuesta.** `AskKnowledgeService` devuelve siempre todos los fragmentos recuperados como `sources`, aunque la respuesta no use alguno. La spec pedía comprobar que las fuentes citadas existen entre las recuperadas.

**Otros puntos**
- La detección de "sin información" usa `contains("SIN_INFORMACION")`; si el LLM cita esa palabra en una respuesta válida se pierde la respuesta. Menor.
- El informe del agente dice 243 pruebas; sumando los reportes de Surefire en mi re-ejecución salen 221 (0 fallos, 1 omitida). La diferencia no está explicada `[UNKNOWN]`; no afecta a las 32 pruebas RAG, que sí coinciden.
- Rate limiting por usuario no existe (SEC-010 sigue abierto y quedó fuera de alcance).
- La spec pedía que la configuración no editara `application.yml`: se cumplió con `@ConfigurationProperties` (`app.rag.*`).
- El `report.md` del agente empieza con cinco líneas de texto de progreso, que conviene ignorar.

**Lo que no se probó:** ninguna llamada a un LLM real (no hay claves en el entorno), ni latencia/coste reales, ni la carga de los documentos desde el empaquetado `jar`.

---

## 3. Qué hacer con esto

| Acción | Quién | Notas |
|---|---|---|
| Aceptar la recomendación de no adoptar Spark | usted | Guardar la PoC o descartarla; no hay dependencias en el build productivo. |
| Pedir las 3 correcciones de RAG a una nueva ejecución acotada | usted decide | Alcance: solo `infrastructure/adapter/out/rag/` y `application/rag/`. |
| Integrar cualquiera de los dos | usted | `git apply` desde `changes.patch` tras revisión; nada se aplicó automáticamente. |
| `mark-reviewed` / `cleanup` | usted | No ejecutados. Los worktrees se conservan. |
