#!/usr/bin/env bash
# shellcheck disable=SC2016  # $VARS in single-quoted sh -c strings expand inside the DB container on purpose.
# Restores a backup produced by backup.sh (HU-104, T073). DESTRUCTIVE for the target database:
# requires CONFIRM=yes. Test it first on a scratch stack (see the guide, "Restauración").
#
# Usage:
#   CONFIRM=yes infra/oracle/restore.sh postgres /var/backups/skillbridge/postgres-<stamp>.dump
#   CONFIRM=yes infra/oracle/restore.sh mongo    /var/backups/skillbridge/mongo-<stamp>.archive.gz
set -euo pipefail

kind="${1:?postgres|mongo}"
file="${2:?backup file}"
APP_DIR="${APP_DIR:-/opt/skillbridge}"
COMPOSE_FILES="${COMPOSE_FILES:--f docker-compose.yml -f docker-compose.prod.yml}"
[[ "${CONFIRM:-}" == "yes" ]] || { echo "refusing to restore without CONFIRM=yes" >&2; exit 1; }
[[ -r "$file" ]] || { echo "cannot read $file" >&2; exit 1; }

cd "$APP_DIR"
read -r -a compose_args <<< "$COMPOSE_FILES"
compose() { docker compose "${compose_args[@]}" "$@"; }

case "$kind" in
  postgres)
    compose exec -T "${PG_SERVICE:-postgres}" sh -c \
      'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' < "$file"
    ;;
  mongo)
    compose exec -T "${MONGO_SERVICE:-mongo}" sh -c \
      'mongorestore --quiet --archive --gzip --drop ${MONGO_INITDB_ROOT_USERNAME:+--username "$MONGO_INITDB_ROOT_USERNAME" --password "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin}' < "$file"
    ;;
  *) echo "unknown kind: $kind" >&2; exit 1 ;;
esac
echo "restored $kind from $file"
