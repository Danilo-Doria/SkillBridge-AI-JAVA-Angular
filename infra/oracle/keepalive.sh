#!/usr/bin/env bash
# Keep-alive / health probe for the Oracle Always Free VM (HU-104, T074).
# Oracle may reclaim idle Always Free instances (low CPU/network/memory use over 7 days).
# Real user traffic plus this periodic probe keeps the VM "in use" and doubles as a
# health check. It never generates artificial CPU burn.
#
# Environment (optional):
#   HEALTH_URL        public URL to probe (default https://$DOMAIN/ ; DOMAIN read from APP_DIR/.env)
#   APP_DIR           default /opt/skillbridge
#   ALERT_WEBHOOK     optional URL that receives a JSON POST when the probe fails
#                     (e.g. a free ntfy.sh topic or a Discord/Slack webhook kept outside the repo)
#
# Idempotent; exit code 0 = healthy, 1 = unhealthy.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/skillbridge}"
if [[ -z "${HEALTH_URL:-}" ]]; then
  domain=""
  if [[ -r "$APP_DIR/.env" ]]; then
    domain="$(grep -E '^DOMAIN=' "$APP_DIR/.env" | tail -1 | cut -d= -f2- | tr -d '"'"'"' ')"
  fi
  HEALTH_URL="https://${domain:-localhost}/"
fi

ts="$(date -u +%FT%TZ)"
code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$HEALTH_URL" || echo 000)"
unhealthy="$(docker ps --filter health=unhealthy --format '{{.Names}}' | paste -sd, -)"

if [[ "$code" =~ ^(2|3)[0-9][0-9]$ && -z "$unhealthy" ]]; then
  echo "$ts ok $HEALTH_URL $code"
  exit 0
fi

msg="$ts FAIL $HEALTH_URL status=$code unhealthy=[${unhealthy}]"
echo "$msg"
if [[ -n "${ALERT_WEBHOOK:-}" ]]; then
  curl -sS --max-time 10 -H 'Content-Type: application/json' \
    --data "$(jq -n --arg m "$msg" '{text: $m, content: $m}')" "$ALERT_WEBHOOK" >/dev/null || true
fi
exit 1
