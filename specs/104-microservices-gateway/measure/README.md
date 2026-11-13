# Medición de cifras (HU-104)

Las cifras del ADR-0009 y del plan (RAM, latencia, arranque) son **estimaciones [INFERRED]** hasta que se midan con esto. `measure.sh` solo lee: no cambia el stack. Esta carpeta **no se ejecutó** al crearla.

## Qué se mide y cuándo

| Cifra (meta) | Comando | Cuándo |
|---|---|---|
| RAM total ≤ 8 GB; Spring native ≤ 200 MB en reposo (NFR-002, SC-003) | `./measure.sh ram baseline 10` | **Antes** (T007, stack actual), después de F2 (sin RabbitMQ), después de F6 (native) |
| Sobrecarga del gateway p95 < 20 ms (NFR-003) | `latency directo <url-backend>` y `latency gateway <url-https>`, luego `compare` | T020, y tras cada ruta movida |
| Arranque native < 3 s (NFR-004) | `startup native-ai <contenedor> <url-health>` | T075, por servicio |
| Límites de rate limit y circuito | `smoke/edge-checks.sh` (WP-A) | T018/T020, ajustar valores en `contracts/gateway-routes.md` |

## Protocolo (para que los números sean comparables)
1. Mismo equipo/VM, sin otras cargas; stack ya arriba y con datos de humo cargados.
2. Esperar 2 min tras `up -d` antes de medir RAM; muestrear 10 min con el humo corriendo de fondo (`smoke/smoke.sh` en bucle).
3. Latencia: ≥ 300 peticiones, concurrencia 5, mismo endpoint GET autenticado/público equivalente en ambos lados; el primer lote de calentamiento se descarta.
4. Medir en **dos entornos**: portátil de desarrollo (orientativo) y la VM Oracle (el que cuenta para las metas). Anotar CPU/RAM/arquitectura de cada uno.
5. Repetir 3 veces y reportar la mediana.

## Dónde se registran
Pegar el resumen en `../progress.md` (tabla "Mediciones") con: fecha, entorno, comando, resultado y ruta del CSV. Luego **sustituir** en `plan.md`/ADR-0009 las estimaciones `[INFERRED]` por el valor medido y marcarlo `[VERIFIED]`. Los CSV de `results/` pueden no versionarse; el resumen en `progress.md` sí.

## Requisitos
`bash`, `docker`, `curl`, `awk`, `xargs` (opcional: `hey`/`k6` para más rigor; el script usa `curl` para no añadir dependencias).
