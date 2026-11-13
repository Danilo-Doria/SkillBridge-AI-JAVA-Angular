# WP-A — Gateway Traefik, middlewares, Swagger y guía Oracle (tareas T010-T015, T018, T021, T022 repo-side)
**Léelo junto con**: `CLAUDE.md`, `AGENTS.md`, `specs/104-microservices-gateway/{spec.md,plan.md,tasks.md}` y `contracts/`. Reglas: no commit/stage, no cambiar de rama, no secretos/`.env`, no editar `docker-compose*.yml` (devuelve el fragmento de servicio en tu informe), no acciones externas. Reporta solo lo que ejecutaste; si no, "No ejecutado en esta sesión". Informe final ≤ 30 líneas: archivos tocados, comandos y resultado real, pendientes.

## Scope (archivos propios)
`infra/traefik/**`, `infra/swagger-ui/**`, `infra/oracle/**`, `specs/104-microservices-gateway/smoke/**`, `docs/guides/oracle-free-deployment.md`.
**Forbidden**: `backend/**`, `audit-service/**`, `frontend/**`, `docker-compose*.yml`, `docs/audit/**`.

## Entregables
1. `traefik.yml` estático (entrypoints, redirección a HTTPS, ACME HTTP-01 con `DOMAIN` por variable, `trustedIPs`, access log JSON con `X-Correlation-Id`, métricas Prometheus).
2. `dynamic/routers.yml` según `contracts/gateway-routes.md` fase 1 (todo `/api/**` → `backend`; `/` → `frontend`; `/audit` → audit; `/docs` → swagger-ui).
3. `dynamic/routers.cutover.yml`: rutas de `/api/ai/**` (con `/api/ai/knowledge` y `/api/admin/ai/providers` según Q4), `/api/payments`, `/api/webhooks` hacia los servicios nuevos. **Fuera del directorio que Traefik carga por defecto** (p. ej. `dynamic-cutover/`), documentado cómo activarlo y revertirlo.
4. `dynamic/middlewares.yml`: `rateLimit` (auth/ai/default/webhook, valores desde variables con los defectos de `gateway-routes.md`), `circuitBreaker`, `retry` solo GET, `headers` (HSTS; borrar `X-User-Id`/`X-User-Roles`), `basicAuth` para `/docs` (usuarios desde secreto/variable, nunca en claro).
5. Plugin local Yaegi `correlationid` (conserva UUID válido, genera si falta/ inválido, también en la respuesta) con sus pruebas Go si es posible ejecutarlas; si Yaegi no carga, documentar el Plan B de T013.
6. `infra/swagger-ui`: configuración con `URLS` por servicio.
7. `infra/oracle/`: cloud-init o script de preparación (Docker, firewall), cron de backup (`pg_dump`, `mongodump`) y keep-alive; sin secretos.
8. `smoke/`: `smoke.sh` (login, ofertas, reserva, auditoría, `/api/ai`) parametrizado por `BASE_URL` y `edge-checks.sh` (429 auth/ai, circuito, spoof `X-User-*`, correlación).
9. `docs/guides/oracle-free-deployment.md` completo (VM A1, security list + iptables, DNS, ACME, variables, despliegue, rollback, restauración).
10. Informe con el **fragmento de Compose** (`traefik`, `swagger-ui`, etiquetas/volúmenes) para que el orquestador lo integre.

## Acceptance criteria
- `traefik` valida su configuración (p. ej. `traefik --configFile=... --check` o arranque en contenedor contra un backend de prueba) y la salida se adjunta; si Docker no está disponible, "No ejecutado en esta sesión".
- Los scripts `smoke` pasan `shellcheck` si está instalado y son idempotentes.
- Ningún secreto ni dominio real en los archivos; todo por variable.
- La ruta `/api/webhooks/**` no tiene `basicAuth` ni el perfil estricto.
- No hay cambios fuera del scope.
