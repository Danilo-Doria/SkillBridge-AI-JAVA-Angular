#!/usr/bin/env bash
# shellcheck disable=SC2016  # $VARS in single-quoted sh -c strings expand inside the DB container on purpose.
# Nightly backup of Postgres (pg_dump) and MongoDB (mongodump) for the SkillBridge stack (HU-104, T073).
# Runs on the VM from cron (see cloud-init.yaml). No secrets in this file: DB credentials are read
# from the containers' own environment; the off-site remote comes from rclone's config.
#
# Environment (optional):
#   APP_DIR              repo checkout with the compose files (default /opt/skillbridge)
#   COMPOSE_FILES        "-f docker-compose.yml -f docker-compose.prod.yml" (default)
#   BACKUP_DIR           local destination (default /var/backups/skillbridge)
#   RETENTION_DAYS       local retention (default 7)
#   PG_SERVICE           compose service name of Postgres (default postgres)
#   MONGO_SERVICE        compose service name of MongoDB (default mongo; skipped if not running)
#   RCLONE_REMOTE        e.g. "b2:skillbridge-backups" or "oci-os:bucket"; unset = local only
#
# Idempotent: each run writes a new timestamped file and prunes files older than RETENTION_DAYS.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/skillbridge}"
COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/skillbridge}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
PG_SERVICE="${PG_SERVICE:-postgres}"
MONGO_SERVICE="${MONGO_SERVICE:-mongo}"
stamp="$(date -u +%Y%m%dT%H%M%SZ)"

cd "$APP_DIR"
read -r -a compose_args <<< "$COMPOSE_FILES"
compose() { docker compose "${compose_args[@]}" "$@"; }
running() { compose ps --status running --services 2>/dev/null | grep -qx "$1"; }

mkdir -p "$BACKUP_DIR"
umask 077
log() { printf '%s %s\n' "$(date -u +%FT%TZ)" "$*"; }

if running "$PG_SERVICE"; then
  out="$BACKUP_DIR/postgres-$stamp.dump"
  # Custom format (-Fc): compressed, restorable per table with pg_restore.
  compose exec -T "$PG_SERVICE" sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > "$out"
  log "postgres -> $out ($(du -h "$out" | cut -f1))"
else
  log "postgres service '$PG_SERVICE' not running: skipped"
fi

if running "$MONGO_SERVICE"; then
  out="$BACKUP_DIR/mongo-$stamp.archive.gz"
  compose exec -T "$MONGO_SERVICE" sh -c 'mongodump --quiet --archive --gzip ${MONGO_INITDB_ROOT_USERNAME:+--username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin}' > "$out"
  log "mongo -> $out ($(du -h "$out" | cut -f1))"
else
  log "mongo service '$MONGO_SERVICE' not running: skipped"
fi

find "$BACKUP_DIR" -type f \( -name 'postgres-*.dump' -o -name 'mongo-*.archive.gz' \) -mtime +"$RETENTION_DAYS" -delete

if [[ -n "${RCLONE_REMOTE:-}" ]]; then
  if command -v rclone >/dev/null; then
    rclone copy "$BACKUP_DIR" "$RCLONE_REMOTE" --include "*-$stamp.*" --quiet
    log "uploaded *-$stamp.* to $RCLONE_REMOTE"
  else
    log "RCLONE_REMOTE set but rclone not installed: off-site copy NOT done"
    exit 1
  fi
fi
