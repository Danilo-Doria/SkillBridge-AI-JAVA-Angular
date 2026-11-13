# Progress: HU-104

Registro de evidencia. **Solo marcar tareas hechas con salida real de comandos.** Si algo no se ejecutó: "No ejecutado en esta sesión".

| Fecha | Tarea | Resultado | Evidencia (comando/salida resumida) |
|---|---|---|---|
| 2026-11-13 | Spec, plan, tasks, ADR-0009 redactados | Hecho | Archivos creados; **no** se ejecutaron tests ni se levantó infraestructura |

## Baseline (T007)
RAM por contenedor: _pendiente_ · p95 de 3 endpoints: _pendiente_

## Decisiones aprobadas por el humano
_pendiente_ (ADR-0009 sigue en **Proposed**)

## Mediciones (rellenar con `measure/measure.sh`; ver `measure/README.md`)
| Fecha | Entorno | Qué | Comando | Resultado | Meta | CSV |
|---|---|---|---|---|---|---|
| _pendiente_ | portátil | RAM baseline (stack actual) | `ram baseline 10` | | informativo | |
| _pendiente_ | portátil | RAM sin RabbitMQ (F2) | `ram f2 10` | | menor que baseline | |
| _pendiente_ | Oracle | RAM total final | `ram final 10` | | ≤ 8192 MiB | |
| _pendiente_ | Oracle | RAM por servicio native | `ram final 10` | | ≤ 200 MiB c/u | |
| _pendiente_ | Oracle | Sobrecarga del gateway p95 | `latency` + `compare` | | < 20 ms | |
| _pendiente_ | Oracle | Arranque native | `startup` | | < 3 s | |
