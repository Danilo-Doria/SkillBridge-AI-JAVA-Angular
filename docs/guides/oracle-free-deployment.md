# Despliegue en Oracle Cloud Always Free (HU-104)

Estado: **borrador de la Fase 1** (T021). Se cierra en T076. Nada de lo descrito aquí se ha
ejecutado aún sobre una VM real: los pasos de Oracle son **[UNKNOWN] hasta ejecutarlos**; la
configuración de Traefik sí se validó en local (ver "Evidencia" al final).

Archivos que usa esta guía:

| Archivo | Para qué |
|---|---|
| `infra/traefik/traefik.yml` | Configuración estática: entrypoints 80/443, ACME, access log JSON, métricas, plugin local |
| `infra/traefik/dynamic/routers.yml` | Rutas de la Fase 1 (todo `/api/**` al monolito) |
| `infra/traefik/dynamic/middlewares.yml` | Rate limit, circuit breaker, retry GET, cabeceras, basicAuth de `/docs`, redirecciones |
| `infra/traefik/dynamic-cutover/routers.cutover.yml` | Rutas a `ai-service` y `payment-service` (no se cargan por defecto) |
| `infra/traefik/plugins-local/src/github.com/skillbridge/correlationid/` | Plugin Yaegi de `X-Correlation-Id` + tests Go |
| `infra/traefik/scripts/gen-docs-htpasswd.sh` | Genera el htpasswd de `/docs` (salida ignorada por git) |
| `infra/swagger-ui/swagger-ui.env` / `.cutover.env` | `URLS` de Swagger agregado |
| `infra/oracle/cloud-init.yaml` | Preparación de la VM: Docker, iptables 80/443, swap, cron |
| `infra/oracle/backup.sh` / `restore.sh` / `keepalive.sh` | Respaldo `pg_dump`/`mongodump`, restauración, keep-alive |
| `specs/104-microservices-gateway/smoke/{smoke.sh,edge-checks.sh}` | Humo funcional y comprobaciones del borde |

---

## 1. Crear la VM (A1 Flex, ARM)

1. Consola OCI → *Compute → Instances → Create instance*.
2. Imagen: **Canonical Ubuntu 24.04** (aarch64). Shape: **VM.Standard.A1.Flex**, hasta 4 OCPU / 24 GB
   dentro del cupo Always Free (la feature presupone ≤ 8 GB usados; ver `plan.md`).
3. Red: VCN con subred **pública** y IP pública asignada. Si no hay capacidad A1 en la región,
   reintentar en otro *availability domain* o más tarde.
4. SSH: sube tu clave pública. **Nunca** subas claves privadas al repo.
5. *Advanced options → Management → Cloud-init*: pega `infra/oracle/cloud-init.yaml`.
6. Boot volume: 50–100 GB (el cupo gratuito es 200 GB en total).

## 2. Firewall: Security List **y** iptables

Oracle filtra en dos sitios; hay que abrir ambos.

**Security List** (o NSG) de la subred → *Ingress rules*:

| Origen | Protocolo | Puerto | Motivo |
|---|---|---|---|
| `0.0.0.0/0` | TCP | 80 | ACME HTTP-01 y redirección a HTTPS |
| `0.0.0.0/0` | TCP | 443 | Tráfico de la aplicación |
| tu IP/32 | TCP | 22 | SSH (restringe el origen) |

**iptables del host**: las imágenes Ubuntu de Oracle traen una regla `REJECT` final. El
`cloud-init` inserta las reglas antes de esa línea y las persiste. Comprobar:

```bash
sudo iptables -L INPUT -n --line-numbers | head -12   # 80 y 443 ACCEPT antes del REJECT
```

No se publican puertos de Postgres, Redis, Kafka, Mongo, Prometheus ni Grafana. Las métricas de
Traefik (`:8082/metrics`) quedan en la red interna; para verlas, túnel SSH:
`ssh -L 9090:localhost:9090 ubuntu@<ip>` con Prometheus en el perfil `observability`.

## 3. DNS

Crear dos registros `A` hacia la IP pública de la VM (en tu proveedor DNS o un DNS dinámico gratuito):

| Nombre | Uso |
|---|---|
| `${DOMAIN}` | Frontend, `/api/**`, `/docs` |
| `audit.${DOMAIN}` (o el valor de `AUDIT_HOST`) | UI y API de auditoría |

La UI de auditoría se compila con `<base href="/">`, por eso va en su propio host y no bajo
`/audit` (que redirige a `https://audit.${DOMAIN}/`). Servirla en una ruta exigiría recompilarla
con `--base-href /audit/` (fuera del alcance de WP-A).

## 4. Certificados (ACME HTTP-01)

- Traefik obtiene los certificados al primer acceso a cada host si `ACME_RESOLVER` está definido.
- Primero usa **staging** (`ACME_RESOLVER=le-staging`) hasta ver que la emisión funciona (certificado
  no confiable pero sin límites estrictos); después cambia a `ACME_RESOLVER=le` y borra
  `acme-staging.json` del volumen si quieres.
- Requisitos: puerto 80 abierto (paso 2) y DNS ya propagado (paso 3).
- Sin `ACME_RESOLVER` (desarrollo) Traefik sirve su certificado autofirmado por defecto.
- El router `catch-all` (hosts desconocidos → 404) **nunca** pide certificados.
- Si hay un CDN/proxy delante (p. ej. Cloudflare en modo proxy): añade sus rangos a
  `forwardedHeaders.trustedIPs` en `traefik.yml` y define `RL_IP_DEPTH=1`, o el rate limit
  contará la IP del proxy en vez de la del cliente.

## 5. Variables de entorno (`/opt/skillbridge/.env`, fuera del repo)

Se copian por SSH (`scp`) o se generan en la VM; nunca se commitean. Mínimo para la Fase 1:

| Variable | Valor en prod | Notas |
|---|---|---|
| `DOMAIN` | tu dominio | Sin esquema ni barra |
| `AUDIT_HOST` | `audit.${DOMAIN}` | Opcional (es el defecto) |
| `ACME_RESOLVER` | `le-staging` → `le` | Vacío en dev |
| `PUBLIC_HTTPS_PORT` | `443` | En dev, el puerto HTTPS publicado (p. ej. `8443`) |
| `CORS_ALLOWED_ORIGINS` | `https://${DOMAIN}` | Q7: con origen único basta con el propio dominio (añade `https://audit.${DOMAIN}` para audit-backend) |
| `JWT_COOKIE_SAME_SITE` | `Lax` (o `Strict`) | Q7: el defecto del backend es `None`; con origen único `Lax` basta y es más seguro. Propiedad `same-site: ${JWT_COOKIE_SAME_SITE:None}` en `backend/src/main/resources/application.yml` [VERIFIED] |
| `JWT_SECRET`, `POSTGRES_PASSWORD`, `GEMINI_API_KEY`, Stripe… | secretos | Solo en `.env`/Secrets de GitHub |
| `RL_*`, `CB_*` | opcionales | Defectos en `middlewares.yml` (= `contracts/gateway-routes.md`, valores [INFERRED] a calibrar en T020) |

Q7 no requiere cambios de código: `server.forward-headers-strategy: framework` ya está activo, así
que el backend ve el esquema/host reales que envía Traefik.

Credenciales de `/docs` (basicAuth): genera el htpasswd en la VM y móntalo como secreto de Compose:

```bash
cd /opt/skillbridge
DOCS_USER=admin infra/traefik/scripts/gen-docs-htpasswd.sh   # pide la contraseña; escribe infra/traefik/secrets/docs.htpasswd (0600, git-ignored)
```

## 6. Fragmento de Compose (gateway)

`docker-compose.yml` lo integra el orquestador (T015). Servicios nuevos:

```yaml
  traefik:
    image: traefik:v3.5
    restart: unless-stopped
    environment:
      DOMAIN: ${DOMAIN:-localhost}
      AUDIT_HOST: ${AUDIT_HOST:-}
      ACME_RESOLVER: ${ACME_RESOLVER:-}
      PUBLIC_HTTPS_PORT: ${GATEWAY_HTTPS_PORT:-8443}
    ports:
      - ${GATEWAY_HTTP_PORT:-8000}:80
      - ${GATEWAY_HTTPS_PORT:-8443}:443
    volumes:
      - ./infra/traefik/traefik.yml:/etc/traefik/traefik.yml:ro
      - ./infra/traefik/dynamic:/etc/traefik/dynamic:ro
      - ./infra/traefik/plugins-local:/plugins-local:ro
      - letsencrypt:/letsencrypt
    secrets:
      - docs_htpasswd
    healthcheck:
      test: ["CMD", "traefik", "healthcheck", "--configFile=/etc/traefik/traefik.yml"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks: [skillbridge]
  swagger-ui:
    image: swaggerapi/swagger-ui:v5.29.0
    restart: unless-stopped
    env_file: ./infra/swagger-ui/swagger-ui.env
    networks: [skillbridge]
secrets:
  docs_htpasswd:
    file: ./infra/traefik/secrets/docs.htpasswd
volumes:
  letsencrypt: null
```

En `docker-compose.prod.yml`: puertos `80:80` y `443:443`, `PUBLIC_HTTPS_PORT: 443`,
`ACME_RESOLVER: ${ACME_RESOLVER:-le}`, `mem_limit` (Traefik ~128 MB, swagger-ui ~64 MB medidos en
reposo: 27 MiB y 14 MiB en local), y **sin** `ports:` en `backend`, `audit-backend`, `frontend`,
`audit-frontend`, bases de datos ni brokers.

## 7. Despliegue

```bash
ssh ubuntu@<ip>
cd /opt/skillbridge && git clone <url-del-repo> . && git checkout <tag>
# copiar .env y generar el htpasswd (paso 5)
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
docker compose -f docker-compose.yml -f docker-compose.prod.yml ps
docker compose logs --since 5m traefik | grep -E '"level":"(error|warn)"'
```

Verificación (T019):

```bash
BASE_URL=https://$DOMAIN AUDIT_BASE_URL=https://audit.$DOMAIN \
  SMOKE_EMAIL=... SMOKE_PASSWORD=... specs/104-microservices-gateway/smoke/smoke.sh
BASE_URL=https://$DOMAIN HTTP_URL=http://$DOMAIN specs/104-microservices-gateway/smoke/edge-checks.sh
```

`edge-checks.sh` consume el cupo de rate limit de tu IP (se recupera en ~1 min).

## 8. Cutover a `ai-service` / `payment-service` y reversión

Decisión Q4: `ai-service` atiende `/api/ai/recommendations/**`, `/api/ai/tts` y
`/api/admin/ai/providers/**`; `/api/ai/knowledge/**` y `/api/ai/trending` **siempre** van al
monolito (router `ai-monolith-only`, prioridad mayor que `/api/ai/**`). `payment-service` atiende
`/api/payments/**` y `/api/webhooks/**` (sin basicAuth, perfil `webhook`, cuerpo intacto).

```bash
# Activar (Traefik recarga en caliente):
cp infra/traefik/dynamic-cutover/routers.cutover.yml infra/traefik/dynamic/
# Swagger con los nuevos servicios: env_file -> infra/swagger-ui/swagger-ui.cutover.env
# Solo uno de los dos: CUTOVER_AI=false o CUTOVER_PAYMENTS=false en el entorno de traefik (recrear el contenedor)

# Revertir (inmediato):
rm infra/traefik/dynamic/routers.cutover.yml
```

## 9. Correlación: plugin Yaegi y Plan B (T013)

El plugin local `correlationid` conserva un `X-Correlation-Id` UUID válido, genera uno si falta o
es inválido, lo devuelve una sola vez en la respuesta y convierte los errores propios del borde
(429, 502, 503, 504 en texto plano) en `application/problem+json` con `correlationId` y
`Retry-After`. También responde 404 problem+json para hosts desconocidos.

**Plan B** si una versión futura de Traefik deja de cargar el plugin (log `Plugins loaded` ausente
o error de Yaegi al arrancar): quitar `experimental.localPlugins` de `traefik.yml` y las
entradas `correlation-id`/`edge-notfound` de las cadenas en `middlewares.yml`. La correlación pasa
a generarse en `CorrelationIdFilter` del backend (ya existente), que la lee/crea, la pone en MDC y
la devuelve; el borde deja de crearla y los 429/503 de Traefik vuelven a texto plano. Documentar
el cambio en `contracts/gateway-routes.md`.

## 10. Rollback

- **Gateway (Fase 1)**: `docker compose stop traefik` y volver a publicar el puerto del `frontend`
  (su nginx sigue teniendo `location /api/`) o reapuntar el DNS.
- **Versión de la app**: `git checkout <tag-anterior>` (o fijar el tag de imagen anterior en `.env`)
  y `docker compose ... pull && up -d`.
- **Cutover**: ver paso 8.

## 11. Respaldos, restauración y keep-alive

- `infra/oracle/backup.sh` (cron diario 03:15 UTC, instalado por `cloud-init`): `pg_dump -Fc` y
  `mongodump --archive --gzip` a `/var/backups/skillbridge`, retención 7 días, y copia externa con
  `rclone` si `RCLONE_REMOTE` está definido (configura `rclone config` en la VM; credenciales fuera
  del repo). Si no hay servicio Mongo en ejecución, se omite y se registra.
- **Restauración** (probar en un stack de prueba antes de necesitarla):

  ```bash
  CONFIRM=yes infra/oracle/restore.sh postgres /var/backups/skillbridge/postgres-<stamp>.dump
  CONFIRM=yes infra/oracle/restore.sh mongo    /var/backups/skillbridge/mongo-<stamp>.archive.gz
  ```

  Registrar fecha y resultado de la prueba de restauración en `progress.md` (FR-015).
- `infra/oracle/keepalive.sh` (cada 30 min): sondea `https://$DOMAIN/` y los healthchecks de Docker;
  con `ALERT_WEBHOOK` avisa en caso de fallo. Oracle puede reclamar instancias Always Free con uso
  muy bajo durante 7 días; el tráfico real más este sondeo mantiene actividad, sin quemar CPU.
  Complementar con un monitor externo gratuito (uptime) contra `https://$DOMAIN/`.

## Evidencia de validación local (2026-11-13, WP-A)

Ejecutado con `traefik:v3.5` (3.5.6) contra contenedores `traefik/whoami` con los nombres de servicio
reales, puertos 18000/18443: plugin cargado (`Plugins loaded`), redirección 301 a HTTPS, 404
problem+json en host desconocido, enrutamiento por prefijo y cutover/reversión, correlación
generada/conservada/regenerada, `X-User-*` eliminadas, 429 en `auth` y `ai` con `Retry-After`,
502 → 503 del breaker y recuperación, basicAuth en `/docs`, webhook sin basicAuth, access log con
`X-Correlation-Id`. **No ejecutado en esta sesión**: despliegue en Oracle, emisión ACME real,
humo contra el stack real (backend, Postgres, Kafka).
