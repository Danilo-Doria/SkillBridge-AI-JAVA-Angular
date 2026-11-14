#!/usr/bin/env bash
# HU-104 smoke collection (T008/T019): login, offerings, booking, audit, /api/ai.
# Runs against ANY base URL: the gateway (https://$DOMAIN) or the monolith directly
# (http://localhost:8080). Compare both runs for SC-001.
#
# Environment:
#   BASE_URL          required, e.g. https://localhost:8443 or http://localhost:8080
#   AUDIT_BASE_URL    optional, audit host (e.g. https://audit.localhost:8443); unset = skip
#   SMOKE_EMAIL       user able to book (CUSTOMER) — required for authenticated steps
#   SMOKE_PASSWORD    its password (read from env only; never commit it)
#   SMOKE_BOOKING     1 (default) creates a booking; 0 skips it
#   SMOKE_AI          1 (default) calls POST /api/ai/recommendations; 0 skips it
#   SMOKE_AI_ALLOW_503  1 accepts a controlled 503 problem+json from /api/ai (provider down)
#   CURL_INSECURE     1 accepts self-signed certificates (local gateway)
#
# Idempotent: the booking uses a deterministic Idempotency-Key per offering and day,
# so re-running the same day returns the same booking instead of creating new ones.
# Exit code: 0 if every executed step passed, 1 otherwise. Skipped steps are reported.
set -euo pipefail

: "${BASE_URL:?set BASE_URL, e.g. https://localhost:8443}"
BASE_URL="${BASE_URL%/}"
AUDIT_BASE_URL="${AUDIT_BASE_URL:-}"
SMOKE_BOOKING="${SMOKE_BOOKING:-1}"
SMOKE_AI="${SMOKE_AI:-1}"
SMOKE_AI_ALLOW_503="${SMOKE_AI_ALLOW_503:-0}"

command -v curl >/dev/null || { echo "curl is required" >&2; exit 2; }
command -v jq >/dev/null || { echo "jq is required" >&2; exit 2; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
jar="$work/cookies"
curl_opts=(-sS --max-time 30 -b "$jar" -c "$jar")
[[ "${CURL_INSECURE:-0}" == "1" ]] && curl_opts+=(-k)

pass=0 fail=0 skip=0
ok()   { pass=$((pass + 1)); printf 'PASS  %s\n' "$1"; }
ko()   { fail=$((fail + 1)); printf 'FAIL  %s\n' "$1"; }
skp()  { skip=$((skip + 1)); printf 'SKIP  %s\n' "$1"; }

# request METHOD URL [curl args...] -> sets $status, body in $work/body, headers in $work/headers
request() {
  local method="$1" url="$2"
  shift 2
  status="$(curl "${curl_opts[@]}" -X "$method" -o "$work/body" -D "$work/headers" -w '%{http_code}' "$@" "$url" || echo 000)"
}
header() { grep -i "^$1:" "$work/headers" | tail -1 | cut -d' ' -f2- | tr -d '\r' || true; }

xsrf=""
refresh_xsrf() {
  local h
  h="$(header X-XSRF-TOKEN)"
  [[ -n "$h" ]] && xsrf="$h"
  return 0
}

echo "smoke against $BASE_URL"

# 1. Public catalog -----------------------------------------------------------
request GET "$BASE_URL/api/offerings"
# Accepts both shapes: plain array (old API) and paged {"items": [...]} (current API).
if [[ "$status" == 200 ]] && jq -e '(.items // .) | type == "array"' "$work/body" >/dev/null 2>&1; then
  ok "GET /api/offerings -> 200 ($(jq '(.items // .) | length' "$work/body") offerings)"
else
  ko "GET /api/offerings -> $status"
fi
offering_id="$(jq -r '(.items // .)[0].id // empty' "$work/body" 2>/dev/null || true)"

corr="$(header X-Correlation-Id)"
if [[ -n "$corr" ]]; then ok "X-Correlation-Id present ($corr)"; else ko "X-Correlation-Id missing in response"; fi

# 2. Login -------------------------------------------------------------------------
logged_in=0
if [[ -z "${SMOKE_EMAIL:-}" || -z "${SMOKE_PASSWORD:-}" ]]; then
  skp "login/booking/ai (SMOKE_EMAIL or SMOKE_PASSWORD not set)"
else
  login_body="$(jq -n --arg e "$SMOKE_EMAIL" --arg p "$SMOKE_PASSWORD" '{email: $e, password: $p}')"
  request POST "$BASE_URL/api/auth/login" -H 'Content-Type: application/json' --data "$login_body"
  if [[ "$status" == 200 ]] && grep -q 'access_token' "$jar"; then
    ok "POST /api/auth/login -> 200 (access_token cookie set)"
    logged_in=1
  else
    ko "POST /api/auth/login -> $status"
  fi
fi

if [[ "$logged_in" == 1 ]]; then
  request GET "$BASE_URL/api/auth/me"
  refresh_xsrf
  if [[ "$status" == 200 ]]; then ok "GET /api/auth/me -> 200"; else ko "GET /api/auth/me -> $status"; fi

  # 3. Booking (cookie + X-XSRF-TOKEN, like the SPA) ---------------------------------
  if [[ "$SMOKE_BOOKING" != 1 ]]; then
    skp "POST /api/bookings (SMOKE_BOOKING=0)"
  elif [[ -z "$offering_id" ]]; then
    skp "POST /api/bookings (no active offering)"
  else
    scheduled="$(date -u -d 'tomorrow 10:00' +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+1d +%Y-%m-%dT10:00:00Z)"
    key="smoke-104-${offering_id}-${scheduled%%T*}"
    body="$(jq -n --arg o "$offering_id" --arg s "$scheduled" '{offeringId: $o, scheduledAt: $s}')"
    request POST "$BASE_URL/api/bookings" -H 'Content-Type: application/json' \
      -H "Idempotency-Key: $key" -H "X-XSRF-TOKEN: $xsrf" --data "$body"
    refresh_xsrf
    if [[ "$status" == 201 || "$status" == 200 ]]; then
      ok "POST /api/bookings -> $status (Idempotency-Key $key)"
    else
      ko "POST /api/bookings -> $status $(head -c 200 "$work/body")"
    fi
    request GET "$BASE_URL/api/bookings/me"
    if [[ "$status" == 200 ]]; then ok "GET /api/bookings/me -> 200"; else ko "GET /api/bookings/me -> $status"; fi
  fi

  # 4. AI ------------------------------------------------------------------------------
  if [[ "$SMOKE_AI" != 1 ]]; then
    skp "POST /api/ai/recommendations (SMOKE_AI=0)"
  else
    request POST "$BASE_URL/api/ai/recommendations" -H 'Content-Type: application/json' \
      -H "X-XSRF-TOKEN: $xsrf" --data '{"goal":"Quiero aprender Java desde cero"}'
    if [[ "$status" == 200 ]]; then
      ok "POST /api/ai/recommendations -> 200"
    elif [[ "$status" == 503 && "$SMOKE_AI_ALLOW_503" == 1 ]]; then
      ok "POST /api/ai/recommendations -> 503 (accepted: SMOKE_AI_ALLOW_503=1)"
    else
      ko "POST /api/ai/recommendations -> $status"
    fi
  fi
fi

# 5. Audit -------------------------------------------------------------------------------
if [[ -z "$AUDIT_BASE_URL" ]]; then
  skp "audit (AUDIT_BASE_URL not set)"
else
  AUDIT_BASE_URL="${AUDIT_BASE_URL%/}"
  request GET "$AUDIT_BASE_URL/"
  if [[ "$status" == 200 ]]; then ok "GET audit UI / -> 200"; else ko "GET audit UI / -> $status"; fi
  request GET "$AUDIT_BASE_URL/api/audit/events"
  if [[ "$status" == 200 ]]; then ok "GET /api/audit/events -> 200"; else ko "GET /api/audit/events -> $status"; fi
fi

echo "summary: pass=$pass fail=$fail skip=$skip"
[[ "$fail" == 0 ]]
