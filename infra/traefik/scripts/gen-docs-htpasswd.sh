#!/usr/bin/env bash
# Generates the htpasswd file used by the Traefik basicAuth middleware on /docs.
# Credentials come from DOCS_USER / DOCS_PASSWORD (or an interactive prompt) and
# are never written in clear text. Output is git-ignored (infra/traefik/secrets/).
#
# Usage: DOCS_USER=admin infra/traefik/scripts/gen-docs-htpasswd.sh [output-file]
# Idempotent: rewrites the file with a fresh salted hash each run.
set -euo pipefail

out="${1:-$(dirname "$0")/../secrets/docs.htpasswd}"
user="${DOCS_USER:-}"
pass="${DOCS_PASSWORD:-}"

if [[ -z "$user" ]]; then
  read -r -p "basicAuth user for /docs: " user
fi
if [[ -z "$pass" ]]; then
  read -r -s -p "basicAuth password for /docs: " pass
  echo
fi
if [[ -z "$user" || -z "$pass" ]]; then
  echo "user and password are required" >&2
  exit 1
fi
if [[ "$user" == *:* ]]; then
  echo "user must not contain ':'" >&2
  exit 1
fi

command -v openssl >/dev/null || { echo "openssl is required" >&2; exit 1; }

mkdir -p "$(dirname "$out")"
umask 077
# -stdin keeps the password out of the process list.
hash="$(printf '%s\n' "$pass" | openssl passwd -apr1 -stdin)"
printf '%s:%s\n' "$user" "$hash" > "$out"
echo "wrote $out (user: $user)"
