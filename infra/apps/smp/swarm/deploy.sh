#!/usr/bin/env bash

set -euo pipefail

swarm_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
environment_file="${swarm_dir}/.env"
stack_file="${swarm_dir}/stack.yaml"

if [ ! -f "$environment_file" ]; then
    echo "Missing ${environment_file}. Run just swarm-prepare first."
    exit 1
fi

set -a
# shellcheck source=/dev/null
source "$environment_file"
set +a

: "${SWARM_STACK_NAME:?Set SWARM_STACK_NAME in swarm/.env}"
: "${SMP_IMAGE:?Set SMP_IMAGE in swarm/.env}"
: "${DASHBOARD_IMAGE:?Set DASHBOARD_IMAGE in swarm/.env}"
: "${PUBLIC_ORIGIN:?Set PUBLIC_ORIGIN in swarm/.env}"

if [ "$(docker info --format '{{.Swarm.LocalNodeState}}')" != "active" ]; then
    echo "This Docker engine is not part of an active Swarm."
    exit 1
fi

if [ "$(docker info --format '{{.Swarm.ControlAvailable}}')" != "true" ]; then
    echo "Run the deployment from a Swarm manager."
    exit 1
fi

storage_label="${SWARM_STORAGE_NODE_LABEL:-profiletailors.storage}"
storage_nodes="$(
    docker node ls --filter "node.label=${storage_label}=true" --format '{{.Status}} {{.Availability}}' |
        awk '$1 == "Ready" && $2 == "Active" { count++ } END { print count + 0 }'
)"
if [ "$storage_nodes" -ne 1 ]; then
    echo "Exactly one ready active node must have the label ${storage_label}=true; found ${storage_nodes}."
    exit 1
fi

create_secret() {
    local secret_name="$1"
    local source_file="$2"
    if ! docker secret inspect "$secret_name" >/dev/null 2>&1; then
        docker secret create "$secret_name" "$source_file" >/dev/null
    fi
}

secrets_dir="${swarm_dir}/secrets"
required_secret_files=(
    db-password
    local-jwt-secret
    publishing-credentials-key
    media-preview-signing-secret
    linkedin-state-signing-secret
    linkedin-client-secret
    resend-api-key
)
for secret_file in "${required_secret_files[@]}"; do
    if [ ! -s "${secrets_dir}/${secret_file}" ]; then
        echo "Secret source ${secrets_dir}/${secret_file} is empty."
        exit 1
    fi
done

create_secret "${SWARM_DB_PASSWORD_SECRET:-profiletailors_db_password_v1}" "${secrets_dir}/db-password"
create_secret "${SWARM_LOCAL_JWT_SECRET:-profiletailors_local_jwt_v1}" "${secrets_dir}/local-jwt-secret"
create_secret "${SWARM_PUBLISHING_KEY_SECRET:-profiletailors_publishing_key_v1}" "${secrets_dir}/publishing-credentials-key"
create_secret "${SWARM_MEDIA_SIGNING_SECRET:-profiletailors_media_signing_v1}" "${secrets_dir}/media-preview-signing-secret"
create_secret "${SWARM_LINKEDIN_STATE_SECRET:-profiletailors_linkedin_state_v1}" "${secrets_dir}/linkedin-state-signing-secret"
create_secret "${SWARM_LINKEDIN_CLIENT_SECRET:-profiletailors_linkedin_client_v1}" "${secrets_dir}/linkedin-client-secret"
create_secret "${SWARM_RESEND_API_KEY_SECRET:-profiletailors_resend_api_key_v1}" "${secrets_dir}/resend-api-key"

service_version() {
    docker service inspect --format '{{.Version.Index}}' "${SWARM_STACK_NAME}_$1" 2>/dev/null || true
}

backend_version_before="$(service_version backend)"
dashboard_version_before="$(service_version dashboard)"

rollback_if_changed() {
    local service="$1"
    local previous_version="$2"
    local current_version
    current_version="$(service_version "$service")"
    if [ -n "$previous_version" ] && [ -n "$current_version" ] && [ "$previous_version" != "$current_version" ]; then
        echo "Rolling back ${SWARM_STACK_NAME}_${service} after readiness validation failed."
        if ! docker service rollback "${SWARM_STACK_NAME}_${service}" >/dev/null; then
            echo "Automatic rollback failed for ${SWARM_STACK_NAME}_${service}; run just swarm-rollback ${service}."
        fi
    fi
}

rollback_changed_application_services() {
    rollback_if_changed backend "$backend_version_before"
    rollback_if_changed dashboard "$dashboard_version_before"
}

docker stack config --compose-file "$stack_file" >/dev/null
docker stack deploy \
    --compose-file "$stack_file" \
    --detach=true \
    --prune \
    --resolve-image "${SWARM_RESOLVE_IMAGE:-always}" \
    --with-registry-auth \
    "$SWARM_STACK_NAME"

health_url="http://127.0.0.1:${SWARM_HTTP_PORT:-8080}/healthz"
base_url="http://127.0.0.1:${SWARM_HTTP_PORT:-8080}"
services_converged() {
    local unconverged
    unconverged="$(docker stack services "$SWARM_STACK_NAME" --format '{{.Replicas}}' | awk -F/ '$1 != $2 { count++ } END { print count + 0 }')"
    [ "$unconverged" -eq 0 ]
}

for attempt in $(seq 1 120); do
    if readiness="$(curl -fsS "$health_url" 2>/dev/null)" &&
        [[ "$readiness" == *'"status":"UP"'* ]] &&
        services_converged; then
        root_status="$(curl -sS -o /dev/null -w '%{http_code}' "${base_url}/")"
        api_status="$(curl -sS -o /dev/null -w '%{http_code}' -H 'Accept: application/vnd.api.v1+json' "${base_url}/api/auth/me")"
        if [ "$root_status" != "200" ] || [ "$api_status" != "401" ]; then
            docker stack services "$SWARM_STACK_NAME"
            echo "Swarm HTTP smoke test failed: dashboard=${root_status} api=${api_status}."
            rollback_changed_application_services
            exit 1
        fi
        docker stack services "$SWARM_STACK_NAME"
        echo "Swarm deployment is ready: dashboard=200 api=401 health=UP services=converged."
        exit 0
    fi
    if [ "$attempt" -eq 120 ]; then
        docker stack services "$SWARM_STACK_NAME"
        echo "Swarm deployment did not become ready within 10 minutes."
        rollback_changed_application_services
        exit 1
    fi
    sleep 5
done
