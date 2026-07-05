#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-server/smp/src/main/resources/db/changelog}"

if [ ! -d "$ROOT" ]; then
  echo "Liquibase changelog directory not found: $ROOT" >&2
  exit 1
fi

matches=$(grep -REn '\$2[aby]\$|password_hash' "$ROOT" --include='*.yaml' --include='*.yml' --include='*.csv' --include='*.sql' || true)
forbidden_matches=$(printf '%s\n' "$matches" | grep -v '/identity/003-create-local-password-credentials.yaml:' || true)

if [ -n "$forbidden_matches" ]; then
  echo "Forbidden Liquibase seed credential material found:" >&2
  printf '%s\n' "$forbidden_matches" >&2
  exit 1
fi

echo "Liquibase seed secret scan passed"
