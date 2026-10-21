#!/usr/bin/env bash
# PreToolUse hook (Edit|Write|MultiEdit|NotebookEdit): blocks edits to protected paths.
#
# Protected paths mirror AGENTS.md section 3 plus Flyway immutability:
#   - docs/audit/findings.md, docs/audit/evidence/**, docs/audit/generated/**
#   - scripts/repo_audit.py
#   - backend/src/main/resources/db/migration/* that already exist in HEAD
#     (an applied migration must never change; add a new V<n+1> instead)
#   - .env files (defense in depth next to permissions.deny)
#
# Contract: reads the hook JSON on stdin. Exit 0 = allow, exit 2 = block (stderr is
# shown to Claude). Fails closed when jq is missing or the input is unparseable.
# Scope: only Claude Code's file-edit tools. Bash commands (sed -i, redirects, ...)
# are NOT inspected; see docs/agents/GUARDRAILS.md.

set -u

deny() {
  printf 'BLOCKED by .claude/hooks/protect-paths.sh: %s\n' "$1" >&2
  exit 2
}

command -v jq >/dev/null 2>&1 || deny "jq is required to evaluate this guard; install jq or fix the hook."

input=$(cat)
file=$(jq -r '.tool_input.file_path // .tool_input.notebook_path // empty' <<<"$input" 2>/dev/null) \
  || deny "could not parse hook input; refusing the edit."
[ -n "$file" ] || exit 0

cwd=$(jq -r '.cwd // empty' <<<"$input" 2>/dev/null)
root=${CLAUDE_PROJECT_DIR:-$cwd}
[ -n "$root" ] || deny "cannot determine the project root; refusing the edit."

case "$file" in
  /*) abs=$(realpath -m -- "$file") ;;
  *)  abs=$(realpath -m -- "${cwd:-$root}/$file") ;;
esac
root=$(realpath -m -- "$root")

case "$abs" in
  "$root"/*) rel=${abs#"$root"/} ;;
  *) exit 0 ;; # outside the project: not this hook's concern
esac

case "$rel" in
  docs/audit/findings.md)
    deny "$rel is the central audit register (AGENTS.md section 3). Ask the user to edit it." ;;
  docs/audit/evidence/*|docs/audit/generated/*)
    deny "$rel is historical/generated audit evidence (AGENTS.md section 3). Ask the user to edit it." ;;
  scripts/repo_audit.py)
    deny "$rel is protected (AGENTS.md section 3). Ask the user to edit it." ;;
  .env|*/.env|.env.local|*/.env.local|.env.production|*/.env.production|.env.development|*/.env.development|.env.staging|*/.env.staging|.env.test|*/.env.test)
    deny "$rel may contain secrets. Never read or edit .env files; use .env.example for documentation." ;;
  backend/src/main/resources/db/migration/*)
    if git -C "$root" cat-file -e "HEAD:$rel" 2>/dev/null; then
      deny "$rel already exists in HEAD, so Flyway may have applied it (checksum would break). Create a new V<next>__description.sql migration instead."
    fi ;;
esac

exit 0
