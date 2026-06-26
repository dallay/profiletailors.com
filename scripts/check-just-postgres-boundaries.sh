#!/usr/bin/env bash
set -euo pipefail

justfile="${1:-Justfile}"

# Extract the body of a just recipe: from the recipe declaration line
# up to (but not including) the next recipe or end of file.
extract_recipe() {
  local recipe="$1"
  awk -v recipe="$recipe" '
    BEGIN { found = 0; stack = "" }
    /^[a-zA-Z][a-zA-Z0-9_-]*[ :]/ && !/:=/ {
      if (found) exit
      if ($0 ~ "^" recipe "(:| )") found = 1
    }
    found { stack = stack $0 ORS }
    END { if (found) printf "%s", stack }
  ' "$justfile"
}

require_recipe_contains() {
  local recipe="$1" needle="$2"
  local block
  block="$(extract_recipe "$recipe")"
  if [[ -z "$block" ]]; then
    printf 'Recipe not found: %s\n' "$recipe" >&2
    exit 1
  fi
  if [[ "$block" != *"$needle"* ]]; then
    printf 'Missing expected content in recipe [%s]: %s\n' "$recipe" "$needle" >&2
    exit 1
  fi
}

# ── backend-test-fast ──────────────────────────────────────────
require_recipe_contains 'backend-test-fast' 'backend-test-fast:'
require_recipe_contains 'backend-test-fast' '-PexcludeTags=modularity,postgres'

# ── backend-test-postgres ──────────────────────────────────────
require_recipe_contains 'backend-test-postgres' 'backend-test-postgres:'
require_recipe_contains 'backend-test-postgres' ':server:smp:postgresIntegrationTest'

# ── ci-local ───────────────────────────────────────────────────
require_recipe_contains 'ci-local' 'ci-local:'
require_recipe_contains 'ci-local' ':server:smp:test --no-daemon -PexcludeTags=modularity,postgres'

# ── ci-full ────────────────────────────────────────────────────
require_recipe_contains 'ci-full' 'ci-full: infra-up'
require_recipe_contains 'ci-full' ':server:smp:postgresIntegrationTest'
require_recipe_contains 'ci-full' ':server:smp:bddPostgresTest'
