# Deployment Checklist

> Current target (HU-104, ADR-0009 Proposed): a single Oracle Cloud Always Free VM with Docker Compose and the Traefik gateway, see the [Oracle Free Deployment Guide](guides/oracle-free-deployment.md). The older multi-provider [Free Tier Deployment Guide](guides/free-tier-deployment.md) is partially obsolete (its message-broker step no longer applies: Kafka is the only broker).

## Before deploying
- [ ] Tests pass.
- [ ] No secrets are committed.
- [ ] `JWT_PRIVATE_KEY` is set (RSA >= 2048 bits, PKCS#8 PEM) and is **not** committed. The backend refuses to start without it.
- [ ] `JWT_GENERATE_EPHEMERAL_KEYS` is `false` (or unset). It is for local development only: sessions are lost on every restart.
- [ ] Migrating from HS256: `JWT_LEGACY_SECRET` is set only until the old tokens expire (120 min by default), then removed.
- [ ] Services that validate tokens (`mentorship-service`, `audit-service`) receive only `JWT_PUBLIC_KEY`, never the private key.
- [ ] Production database uses TLS.
- [ ] Redis URL uses TLS when the provider requires it.
- [ ] `KAFKA_BOOTSTRAP_SERVERS` points to the Kafka broker (only broker; RabbitMQ retired in HU-104) and the Kafka port is not published outside the VM.
- [ ] Gateway: the `docs_htpasswd` secret was generated with `infra/traefik/scripts/gen-docs-htpasswd.sh` (never committed) and `DOMAIN`/ACME settings are correct.
- [ ] `APP_CATALOG_SNAPSHOT_ON_STARTUP` is set intentionally (`true` only when a consumer read model, e.g. `ai-service`, needs a cold-start snapshot of offerings).
- [ ] `GEMINI_API_KEY` exists only in backend environment variables.
- [ ] `CORS_ALLOWED_ORIGINS` contains only the real frontend URL(s).
- [ ] `/actuator/health` returns UP.
- [ ] Swagger is reviewed; consider disabling it for a real production product.

## After deploying
- [ ] Register/login works.
- [ ] Public catalog works twice (second request should be cache-eligible).
- [ ] Authenticated AI endpoint works.
- [ ] Create booking works.
- [ ] Kafka topics `payment-events` and `notification-requests` are consumed (no consumer lag) and their `*.DLT` topics are empty or triaged ([Kafka DLT runbook](guides/kafka-dlt-runbook.md)).
- [ ] `specs/104-microservices-gateway/smoke/smoke.sh` and `edge-checks.sh` pass against the gateway.
- [ ] Logs contain no passwords/tokens/API keys.
- [ ] Metrics are visible locally with the observability profile.
