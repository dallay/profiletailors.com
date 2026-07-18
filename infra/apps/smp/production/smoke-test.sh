#!/usr/bin/env bash

set -euo pipefail

production_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
environment_file="${production_dir}/.env"
compose_file="${production_dir}/compose.yaml"

if [ ! -f "$environment_file" ]; then
    echo "Missing ${environment_file}. Run just production-prepare first."
    exit 1
fi

set -a
# shellcheck source=/dev/null
source "$environment_file"
set +a

compose=(docker compose --env-file "$environment_file" -f "$compose_file")
bind_address="${HTTP_BIND_ADDRESS:-127.0.0.1}"
if [ "$bind_address" = "0.0.0.0" ] || [ "$bind_address" = "::" ]; then
    bind_address="127.0.0.1"
fi
base_url="http://${bind_address}:${HTTP_PORT:-8080}"

wait_for_readiness() {
    local readiness
    for attempt in $(seq 1 120); do
        if readiness="$(curl -fsS "${base_url}/healthz" 2>/dev/null)" && [[ "$readiness" == *'"status":"UP"'* ]]; then
            return 0
        fi
        if [ "$attempt" -eq 120 ]; then
            echo "Production stack did not become ready within 10 minutes."
            return 1
        fi
        sleep 5
    done
}

assert_read_only_rootfs() {
    local service="$1"
    local container_id
    container_id="$("${compose[@]}" ps -q "$service")"
    if [ -z "$container_id" ]; then
        echo "Service ${service} has no running container."
        exit 1
    fi
    if [ "$(docker inspect "$container_id" --format '{{.HostConfig.ReadonlyRootfs}}')" != "true" ]; then
        echo "Service ${service} does not have a read-only root filesystem."
        exit 1
    fi
}

assert_secret_not_in_environment() {
    local service="$1"
    local container_id
    container_id="$("${compose[@]}" ps -q "$service")"
    if docker inspect "$container_id" --format '{{range .Config.Env}}{{println .}}{{end}}' |
        grep -Eq '^(SMP_DB_PASSWORD|SMP_LOCAL_JWT_SECRET|PUBLISHING_CREDENTIALS_KEY|SMP_MEDIA_PREVIEW_SIGNING_SECRET|SMP_LINKEDIN_STATE_SIGNING_SECRET|SMP_LINKEDIN_CLIENT_SECRET|SMP_RESEND_API_KEY)='; then
        echo "Service ${service} exposes a secret through its environment."
        exit 1
    fi
}

run_checks() {
    local root_status
    local api_status
    local migration_count
    local development_principal_count

    wait_for_readiness
    root_status="$(curl -sS -o /dev/null -w '%{http_code}' "${base_url}/")"
    if [ "$root_status" != "200" ]; then
        echo "Dashboard returned HTTP ${root_status}; expected 200."
        exit 1
    fi

    api_status="$(curl -sS -o /dev/null -w '%{http_code}' -H 'Accept: application/vnd.api.v1+json' "${base_url}/api/auth/me")"
    if [ "$api_status" != "401" ]; then
        echo "Unauthenticated API proxy check returned HTTP ${api_status}; expected 401."
        exit 1
    fi

    migration_count="$("${compose[@]}" exec -T postgresql psql -U "${POSTGRES_USER:-profiletailors}" -d "${POSTGRES_DB:-profiletailors_smp}" -Atc 'SELECT COUNT(*) FROM databasechangelog;')"
    if ! [[ "$migration_count" =~ ^[1-9][0-9]*$ ]]; then
        echo "Liquibase migration count is invalid: ${migration_count}."
        exit 1
    fi

    development_principal_count="$("${compose[@]}" exec -T postgresql psql -U "${POSTGRES_USER:-profiletailors}" -d "${POSTGRES_DB:-profiletailors_smp}" -Atc "SELECT COUNT(*) FROM principals WHERE id = 'dev-user-001';")"
    if [ "$development_principal_count" != "0" ]; then
        echo "Production database contains the development principal."
        exit 1
    fi

    assert_read_only_rootfs dashboard
    assert_read_only_rootfs backend
    assert_secret_not_in_environment backend

    echo "Production smoke test passed: dashboard=200 api=401 health=UP migrations=${migration_count} dev_seed=0."
}

run_checks

if [ "${1:-}" = "--restart" ]; then
    "${compose[@]}" stop
    "${compose[@]}" up -d --wait
    run_checks
    echo "Production restart and persistence check passed."
elif [ -n "${1:-}" ]; then
    echo "Usage: $0 [--restart]"
    exit 2
fi
