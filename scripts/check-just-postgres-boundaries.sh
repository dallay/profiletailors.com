#!/usr/bin/env bash
set -euo pipefail

justfile="${1:-Justfile}"
content="$(<"$justfile")"

require_contains() {
  local needle="$1"
  if [[ "$content" != *"$needle"* ]]; then
    printf 'Missing expected Justfile boundary: %s\n' "$needle" >&2
    exit 1
  fi
}

require_contains 'backend-test-fast:'
require_contains '-PexcludeTags=modularity,postgres'
require_contains 'backend-test-postgres:'
require_contains ':server:smp:postgresIntegrationTest'
require_contains 'ci-local:'
require_contains ':server:smp:test --no-daemon -PexcludeTags=modularity,postgres'
require_contains 'ci-full: infra-up'
require_contains ':server:smp:postgresIntegrationTest'
require_contains ':server:smp:bddPostgresTest'
