#!/usr/bin/env bash
# HU-104 edge checks (T018) — run ONLY against the gateway, never the monolith directly.
#   correlation (generated / preserved / invalid regenerated), HSTS, X-User-* spoofing,
#   404 problem+json for unknown hosts, HTTP->HTTPS redirect, webhook without basicAuth,
#   429 on the auth and ai profiles, circuit breaker 503 (optional, needs stop/start commands).
#
# Environment:
#   BASE_URL          required, gateway HTTPS URL (e.g. https://localhost:8443)
#   HTTP_URL          optional, gateway HTTP URL (e.g. http://localhost:8000) for the redirect check
#   CURL_INSECURE     1 accepts self-signed certificates (local)
#   RL_PROBES         requests sent to trigger 429 (default 30; must exceed the profile burst)
#   EDGE_ECHO         1 when the upstream echoes request headers (traefik/whoami test bed):
#                     enables the strict "X-User-* never reaches upstream" assertion
#   CB_STOP_CMD / CB_START_CMD  e.g. "docker compose stop backend" / "docker compose start backend";
#                     unset = circuit breaker check skipped
#   CB_PATH           path used for the breaker check (default /api/bookings, POST)
#
# Idempotent and read-only for data: it only sends anonymous requests. It does consume the
# caller IP's rate-limit budget (auth/ai buckets refill within RL_PERIOD, 1 minute by default).
set -euo pipefail

: "${BASE_URL:?set BASE_URL to the gateway HTTPS URL}"
BASE_URL="${BASE_URL%/}"
RL_PROBES="${RL_PROBES:-30}"
CB_PATH="${CB_PATH:-/api/bookings}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
curl_opts=(-sS --max-time 15)
[[ "${CURL_INSECURE:-0}" == "1" ]] && curl_opts+=(-k)

pass=0 fail=0 skip=0
ok()  { pass=$((pass + 1)); printf 'PASS  %s\n' "$1"; }
ko()  { fail=$((fail + 1)); printf 'FAIL  %s\n' "$1"; }
skp() { skip=$((skip + 1)); printf 'SKIP  %s\n' "$1"; }

request() {
  local method="$1" url="$2"
  shift 2
  status="$(curl "${curl_opts[@]}" -X "$method" -o "$work/body" -D "$work/headers" -w '%{http_code}' "$@" "$url" || echo 000)"
}
header() { grep -i "^$1:" "$work/headers" | tail -1 | cut -d' ' -f2- | tr -d '\r' || true; }
is_uuid() { [[ "$1" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; }
is_problem() { [[ "$(header Content-Type)" == application/problem+json* ]] && grep -q '"correlationId"' "$work/body"; }

echo "edge checks against $BASE_URL"

# Correlation --------------------------------------------------------------------------
request GET "$BASE_URL/"
gen="$(header X-Correlation-Id)"
if is_uuid "$gen"; then ok "correlation generated when missing ($gen)"; else ko "correlation not generated (got '$gen')"; fi

known="3f2c1a9e-8b7d-4c6e-9f1a-0b2c3d4e5f60"
request GET "$BASE_URL/" -H "X-Correlation-Id: $known"
if [[ "$(header X-Correlation-Id)" == "$known" ]] && [[ "$(grep -ci '^x-correlation-id:' "$work/headers")" == 1 ]]; then
  ok "valid correlation preserved and returned once"
else
  ko "valid correlation not preserved (got '$(header X-Correlation-Id)')"
fi

request GET "$BASE_URL/" -H "X-Correlation-Id: not-a-uuid'><script>"
got="$(header X-Correlation-Id)"
if is_uuid "$got"; then ok "invalid correlation replaced ($got)"; else ko "invalid correlation kept ('$got')"; fi

# HSTS ------------------------------------------------------------------------------------
request GET "$BASE_URL/"
if [[ -n "$(header Strict-Transport-Security)" ]]; then ok "HSTS header present"; else ko "HSTS header missing"; fi

# X-User-* spoofing -------------------------------------------------------------------
request GET "$BASE_URL/api/auth/me" -H 'X-User-Id: 00000000-0000-0000-0000-000000000001' -H 'X-User-Roles: ADMIN'
if [[ "${EDGE_ECHO:-0}" == "1" ]]; then
  if grep -qi '^X-User-' "$work/body"; then ko "X-User-* reached the upstream"; else ok "X-User-* stripped before the upstream"; fi
elif [[ "$status" == 401 || "$status" == 403 ]]; then
  ok "forged X-User-* does not authenticate (status $status)"
else
  ko "forged X-User-* got status $status on /api/auth/me"
fi

# Unknown host -> 404 problem+json -------------------------------------------------------
request GET "$BASE_URL/anything" -H 'Host: unknown-host.invalid'
if [[ "$status" == 404 ]] && is_problem; then ok "unknown host -> 404 problem+json"; else ko "unknown host -> $status"; fi

# HTTP -> HTTPS ------------------------------------------------------------------------------
if [[ -n "${HTTP_URL:-}" ]]; then
  request GET "${HTTP_URL%/}/api/offerings"
  loc="$(header Location)"
  if [[ "$status" =~ ^30[178]$ && "$loc" == https://* ]]; then ok "HTTP redirects to HTTPS ($status $loc)"; else ko "HTTP redirect -> $status '$loc'"; fi
else
  skp "HTTP -> HTTPS redirect (HTTP_URL not set)"
fi

# Webhook: no basicAuth --------------------------------------------------------------------
request POST "$BASE_URL/api/webhooks/stripe" -H 'Content-Type: application/json' -H 'Stripe-Signature: t=0,v1=invalid' --data '{}'
if grep -qi '^WWW-Authenticate: *Basic' "$work/headers"; then ko "webhook route asks for basicAuth"; else ok "webhook route without basicAuth (status $status)"; fi

# Rate limits ---------------------------------------------------------------------------------
probe_429() { # name method path [curl args]
  local name="$1" method="$2" path="$3" i got429=0
  shift 3
  for ((i = 1; i <= RL_PROBES; i++)); do
    request "$method" "$BASE_URL$path" "$@"
    if [[ "$status" == 429 ]]; then got429=1; break; fi
  done
  if [[ "$got429" == 1 && -n "$(header Retry-After)" ]] && is_problem; then
    ok "$name profile -> 429 problem+json with Retry-After after $i requests"
  else
    ko "$name profile -> no 429 with Retry-After in $RL_PROBES requests (last $status)"
  fi
}
probe_429 auth POST /api/auth/login -H 'Content-Type: application/json' --data '{"email":"edge-check@example.invalid","password":"x"}'
probe_429 ai POST /api/ai/recommendations -H 'Content-Type: application/json' --data '{"goal":"edge check"}'

# Circuit breaker ----------------------------------------------------------------------------
if [[ -n "${CB_STOP_CMD:-}" && -n "${CB_START_CMD:-}" ]]; then
  bash -c "$CB_STOP_CMD"
  opened=0
  for ((i = 1; i <= 30; i++)); do
    request POST "$BASE_URL$CB_PATH" -H 'Content-Type: application/json' --data '{}'
    if [[ "$status" == 503 && -n "$(header Retry-After)" ]] && is_problem; then opened=1; break; fi
    sleep 1
  done
  bash -c "$CB_START_CMD"
  if [[ "$opened" == 1 ]]; then ok "circuit breaker -> 503 problem+json with Retry-After"; else ko "circuit breaker did not open (last $status)"; fi
else
  skp "circuit breaker (CB_STOP_CMD/CB_START_CMD not set)"
fi

echo "summary: pass=$pass fail=$fail skip=$skip"
[[ "$fail" == 0 ]]
