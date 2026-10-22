# SPARK-001 — Evaluación de Apache Spark y prueba de concepto aislada

| Campo | Valor |
|---|---|
| Responsable | Antigravity CLI (AGY) vía `scripts/agent-worktree-agy.sh` |
| Base | `1d5dde4` (rama `develop`) |
| Estado inicial | PLANNED (lanzamiento condicionado, ver `WORKSTREAM-LOG.md`) |

## Criterios de aceptación (validables por el coordinador)

1. El informe contiene una **recomendación explícita** (integrar / integrar condicionado a umbrales / no adoptar) respaldada por evidencia del repositorio: volumen real de datos, eventos existentes y arquitectura actual.
2. Se compara Spark con al menos tres alternativas más simples (consultas SQL/agregaciones en PostgreSQL, el consumidor Kafka incremental ya existente, Kafka Streams, jobs programados/DuckDB) en coste operativo, complejidad, latencia y escalabilidad.
3. La prueba de concepto está **aislada**: no modifica `backend/`, `frontend/`, `audit-service/`, `docker-compose.yml`, `pom.xml` ni `package.json` productivos; tiene su propio README y build.
4. La PoC es reproducible con un comando documentado, **sin servicios externos de pago ni credenciales**, y declara el origen de sus datos: eventos reales exportables del repositorio o datos **sintéticos claramente etiquetados** que respeten el esquema real de `BusinessEvent` (`BookingCreated`, `BookingCancelled`, `RecommendationRequested/Generated`). Nunca se presentan datos sintéticos como medición de producción.
5. La PoC calcula al menos una métrica de negocio existente (p. ej. reservas/cancelaciones y conversión por oferta y día, equivalentes a `recommendation_daily_metrics`) y **valida el resultado contra una implementación de referencia** (SQL/Java/Python simple) con igualdad verificable.
6. Se reportan métricas medidas (tiempo de ejecución, memoria máxima, tamaño de datos, filas/s) para al menos tres volúmenes (p. ej. 10⁴, 10⁵, 10⁶ eventos) y se compara con la referencia sencilla, indicando el punto de cruce si existe.
7. Pruebas automáticas de la PoC (corrección de la agregación, datos vacíos, eventos duplicados/idempotencia, fechas límite) ejecutadas y con resultado real informado.
8. Se detalla coste operativo y requisitos de despliegue (JDK/Scala/Spark, memoria, cluster vs. local, CI, operación) y los criterios cuantitativos que justificarían adoptar Spark en el futuro.
9. Ningún cambio fuera del alcance; el worktree mantiene el estado limpio salvo el contenido del alcance; sin artefactos pesados versionables (datasets generados, `target/`, `metastore_db/`, `spark-warehouse/`) fuera de rutas ignoradas o documentadas.
10. Informe final con el formato indicado.

## Ubicación (desviación declarada)

El destino preferido era `experiments/spark-analytics/`, pero el orquestador exige que el directorio padre del alcance exista en el commit base y `experiments/` no existe en `1d5dde4` (crearlo requeriría un commit, no autorizado). Se usa **`scripts/spark-analytics/`**, cuyo padre existe. Si en el futuro se commitea `experiments/`, la PoC puede moverse sin cambios de contenido.

## Investigación obligatoria

Leer solo lo necesario:
- Flujo de eventos: `docs/INTEGRATIONS.md`, `docs/kafka.md`, `docs/adr/0003-kafka-event-topic-unification.md`, `docs/architecture/overview.md`, `docs/MODULES/audit-service.md`.
- Modelo y analítica existente: migraciones `V11__recommendation_events.sql`, `V12__recommendation_daily_metrics.sql`, `V4__booking_cancellation_audit.sql`; `DemandAnalyticsService` (fórmulas de `conversionRate`, `growthRate`, `trendScore`, `forecastNext7Days`); `RecommendationAnalyticsRepositoryPort`.
- Esquema de eventos: `application/port/out/event/BusinessEvent.java` y `audit-service/backend/.../model/BusinessEvent.java` (sin acoplar compilación entre ambos).
- Datos disponibles: `V2__seed_data.sql` y datos de pruebas. Determinar el volumen real (hoy es de demostración) y proyectar escenarios de crecimiento con supuestos explícitos.
- Restricciones del entorno de pruebas: máquina de desarrollo con ~16 GB de RAM y ~16 GB de disco libres; la PoC debe poder correr en modo local (`local[*]`) con límites de memoria configurables.

## Entrega esperada

- **Caso de uso propuesto** y evidencia (o ausencia de ella) que lo respalda; si el volumen no lo justifica, decirlo.
- **Arquitectura y flujo de datos** (diagrama textual/Mermaid): origen (export de Kafka/PostgreSQL), procesamiento, destino de resultados; cómo se integraría sin tocar el camino transaccional.
- **PoC reproducible** (PySpark o Spark Java/Scala; elegir con justificación, priorizando el menor número de dependencias y versiones fijadas) con README, script de generación/exportación de datos, ejecución y comparación contra la referencia.
- **Pruebas y métricas** observables (ver criterios 5-7).
- **Coste operativo, dependencias y despliegue**: versiones, imágenes, memoria, operación en producción, alternativas gestionadas, riesgos de licencia.
- **Recomendación** con umbrales de revisión (volumen de eventos/día, latencia requerida, nº de consumidores analíticos).

## Alcance de archivos (único permitido)

- `scripts/spark-analytics/` (código, README, datos de ejemplo pequeños, pruebas, build propio)

El informe final se entrega como `report.md` del orquestador; no se crean documentos en `docs/`.

## Restricciones

- Sin commits, `git add`, stash, reset ni cambios de rama; sin `--dangerously-skip-permissions`.
- Prohibido tocar: `backend/**`, `frontend/**`, `audit-service/**`, `docker-compose.yml`, `.github/**`, `docs/**` (incluidos `PROJECT_CONTEXT.md`, `WORKSTREAM-LOG.md`, `docs/audit/**`), `scripts/repo_audit.py`, y los demás archivos de `scripts/`.
- No añadir Spark a ningún build productivo ni proponer cambios de arquitectura sin evidencia.
- Sin servicios de pago ni credenciales; descargas de dependencias solo desde los repositorios estándar de cada gestor de paquetes y declaradas en el informe. No modificar la configuración global del usuario (`~/.m2`, `~/.gemini`, pip global): usar entornos locales dentro del alcance (p. ej. `scripts/spark-analytics/.venv`, ignorado y documentado).
- Si no puede ejecutarse Spark en el entorno (JDK, red, memoria), documentar el bloqueo y entregar el análisis más una referencia no-Spark verificada; no simular resultados.

## Fuera de alcance

Integración en producción, nuevos topics o consumidores en el backend, cambios de esquema, dashboards, cambios de CI, ajuste del `DemandAnalyticsService`.

## Pruebas necesarias

- Corrección de agregación vs. referencia (igualdad exacta en enteros, tolerancia declarada en flotantes).
- Casos borde: sin eventos, eventos duplicados por `eventId`, eventos fuera de ventana, cancelaciones sin creación previa, zonas horarias/fecha límite.
- Reproducibilidad: dos ejecuciones con la misma semilla producen el mismo resultado.
- Informar comandos y totales reales; lo no ejecutado, con motivo.

## Riesgos y pendientes conocidos

- Datos de producción inexistentes en el repositorio: los benchmarks usan datos sintéticos y solo son indicativos.
- Spark en modo local suele ser más lento que SQL para volúmenes pequeños; el resultado honesto puede ser "no adoptar todavía".
- Consumo de RAM/disco de la máquina durante benchmarks grandes; limitar el volumen máximo y declararlo.
- Dependencia de versión de JDK (el proyecto usa Java 21; verificar compatibilidad con la versión de Spark elegida).

## Formato del informe final (`report.md`)

1. Resumen ejecutivo con la recomendación.
2. Evidencia del repositorio (rutas/líneas) y volumen real vs. supuestos.
3. Arquitectura, flujo y comparación con alternativas (tabla).
4. PoC: estructura, cómo reproducir, resultados de validación contra la referencia.
5. Métricas medidas (tabla por volumen) y metodología; pruebas **ejecutadas** y **no ejecutadas**.
6. Coste operativo, dependencias y despliegue; umbrales para reconsiderar.
7. Limitaciones y recomendación final: ready-for-review / needs-fixes / reject. Marcar `[VERIFIED]`, `[INFERRED]`, `[UNKNOWN]`.
