#!/usr/bin/env bash
set -euo pipefail

NAME="threads-mock"
PORT="${WIREMOCK_PORT:-8089}"
CONTAINER="pt-main-0c6a3a6b87-social-api-wiremock-1"

cd "$(dirname "$0")/.."

if ! command -v portless >/dev/null 2>&1 && ! pnpm exec portless --version >/dev/null 2>&1; then
  echo "ERROR: portless is not installed. Run: pnpm install" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -qE "${CONTAINER}|wiremock"; then
  echo "ERROR: wiremock container is not running. Start it with: just infra-up" >&2
  exit 1
fi

echo "Registering portless alias: https://${NAME}.localhost -> 127.0.0.1:${PORT}"
pnpm exec portless alias "${NAME}" "${PORT}"

HOST_PORT=$(node scripts/compose-run.mjs ports 2>/dev/null \
  | awk -v svc="social-api-wiremock" '$1 ~ svc {print $3}' \
  | head -1 \
  | awk -F: '{print $NF}')

if [ -z "${HOST_PORT}" ]; then HOST_PORT="${PORT}"; fi

echo
echo "Verifying stub: GET https://${NAME}.localhost/v1.0/me?fields=id"
HTTP_STATUS=$(/usr/bin/curl -sk -o /dev/null -w "%{http_code}" \
  "https://${NAME}.localhost/v1.0/me?fields=id,username,name,threads_profile_picture_url" || echo "000")

if [ "${HTTP_STATUS}" = "200" ]; then
  echo "OK: Threads mock reachable."
else
  echo "WARN: Threads mock returned status=${HTTP_STATUS}. Re-run infra-up if needed." >&2
  exit 1
fi

echo
echo "Next steps:"
echo "  1. Ensure .env has: SMP_THREADS_API_BASE_URL=https://${NAME}.localhost"
echo "  2. Restart the SMP backend so it picks up the new base URL."
echo "  3. Reload https://pt-app.localhost/ — Threads should appear in the sidebar."
