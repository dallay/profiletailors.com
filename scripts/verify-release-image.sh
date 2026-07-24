#!/usr/bin/env bash

set -euo pipefail

image_name="${1:?Usage: verify-release-image.sh <image-name>}"
run_id="$$"
network_name="pt-release-${run_id}"
database_container="pt-release-db-${run_id}"
application_container="pt-release-app-${run_id}"
postgres_image="postgres@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15"
database_name="profiletailors_release"
database_user="profiletailors"
database_password="$(openssl rand -base64 32)"
jwt_secret="$(openssl rand -base64 32)"
publishing_key="$(openssl rand -base64 32)"
media_signing_secret="$(openssl rand -base64 32)"
linkedin_state_signing_secret="$(openssl rand -base64 32)"

cleanup() {
    docker rm --force "$application_container" >/dev/null 2>&1 || true
    docker rm --force "$database_container" >/dev/null 2>&1 || true
    docker network rm "$network_name" >/dev/null 2>&1 || true
}

trap cleanup EXIT INT TERM

docker network create "$network_name" >/dev/null
docker run \
    --detach \
    --rm \
    --name "$database_container" \
    --network "$network_name" \
    --env POSTGRES_DB="$database_name" \
    --env POSTGRES_USER="$database_user" \
    --env POSTGRES_PASSWORD="$database_password" \
    "$postgres_image" >/dev/null

for attempt in $(seq 1 60); do
    if docker exec "$database_container" pg_isready -U "$database_user" -d "$database_name" >/dev/null 2>&1; then
        break
    fi
    if [ "$attempt" -eq 60 ]; then
        docker logs "$database_container"
        exit 1
    fi
    sleep 1
done

docker run \
    --detach \
    --rm \
    --name "$application_container" \
    --network "$network_name" \
    --publish 127.0.0.1::9091 \
    --env SPRING_PROFILES_ACTIVE=prod \
    --env SPRING_DOCKER_COMPOSE_ENABLED=false \
    --env SMP_R2DBC_URL="r2dbc:postgresql://${database_container}:5432/${database_name}" \
    --env SMP_LIQUIBASE_JDBC_URL="jdbc:postgresql://${database_container}:5432/${database_name}" \
    --env SMP_LIQUIBASE_CONTEXTS=prod \
    --env SMP_DB_USERNAME="$database_user" \
    --env SMP_DB_PASSWORD="$database_password" \
    --env SMP_LOCAL_JWT_SECRET="$jwt_secret" \
    --env SMP_LOCAL_JWT_ISSUER=http://localhost/profiletailors-release \
    --env SMP_JWT_ISSUER_URI=http://localhost/profiletailors-release \
    --env PUBLISHING_CREDENTIALS_ENCRYPTION_KEY="$publishing_key" \
    --env SMP_MEDIA_PREVIEW_SIGNING_SECRET="$media_signing_secret" \
    --env SMP_LINKEDIN_STATE_SIGNING_SECRET="$linkedin_state_signing_secret" \
    --env SMP_PUBLISHING_WORKER_ENABLED=false \
    --env SMP_STORAGE_PROVIDER_TYPE=local \
    --env SMP_STORAGE_LOCAL_BASE_PATH=/tmp/profiletailors-storage \
    --env SMP_CORS_ALLOWED_ORIGINS=https://app.example.invalid \
    "$image_name" >/dev/null

management_address="$(docker port "$application_container" 9091/tcp)"
management_port="${management_address##*:}"
readiness_url="http://127.0.0.1:${management_port}/actuator/health/readiness"
liveness_url="http://127.0.0.1:${management_port}/actuator/health/liveness"

for attempt in $(seq 1 90); do
    if readiness="$(curl -fsS "$readiness_url" 2>/dev/null)"; then
        break
    fi
    if [ "$attempt" -eq 90 ]; then
        docker logs "$application_container"
        exit 1
    fi
    sleep 1
done

liveness="$(curl -fsS "$liveness_url")"
migration_count="$(docker exec "$database_container" psql -U "$database_user" -d "$database_name" -tAc 'SELECT count(*) FROM databasechangelog')"
dev_seed_count="$(docker exec "$database_container" psql -U "$database_user" -d "$database_name" -tAc "SELECT count(*) FROM databasechangelog WHERE id = 'dev-001-seed-test-data'")"

if [[ "$readiness" != *'"status":"UP"'* ]]; then
    echo "Readiness did not report UP: $readiness"
    exit 1
fi
if [[ "$liveness" != *'"status":"UP"'* ]]; then
    echo "Liveness did not report UP: $liveness"
    exit 1
fi
if [ "$migration_count" -le 0 ]; then
    echo "Liquibase did not record any migrations."
    exit 1
fi
if [ "$dev_seed_count" -ne 0 ]; then
    echo "Development seed data was applied during production verification."
    exit 1
fi

image_id="$(docker image inspect "$image_name" --format '{{.Id}}')"
echo "image=${image_name}"
echo "image_id=${image_id}"
echo "migrations=${migration_count}"
echo "development_seed_changesets=${dev_seed_count}"
echo "readiness=UP"
echo "liveness=UP"
