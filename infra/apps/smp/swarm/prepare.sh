#!/usr/bin/env bash

set -euo pipefail

swarm_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
secrets_dir="${swarm_dir}/secrets"
environment_file="${swarm_dir}/.env"

umask 077
mkdir -p "$secrets_dir"

if [ ! -f "$environment_file" ]; then
    cp "${swarm_dir}/.env.example" "$environment_file"
fi

generate_secret() {
    local target="$1"
    if [ ! -s "$target" ]; then
        openssl rand -base64 32 >"$target"
    fi
}

generate_secret "${secrets_dir}/db-password"
generate_secret "${secrets_dir}/local-jwt-secret"
generate_secret "${secrets_dir}/publishing-credentials-key"
generate_secret "${secrets_dir}/media-preview-signing-secret"
generate_secret "${secrets_dir}/linkedin-state-signing-secret"

touch "${secrets_dir}/linkedin-client-secret"
touch "${secrets_dir}/resend-api-key"
chmod 600 \
    "${secrets_dir}/db-password" \
    "${secrets_dir}/local-jwt-secret" \
    "${secrets_dir}/publishing-credentials-key" \
    "${secrets_dir}/media-preview-signing-secret" \
    "${secrets_dir}/linkedin-state-signing-secret" \
    "${secrets_dir}/linkedin-client-secret" \
    "${secrets_dir}/resend-api-key"

echo "Swarm configuration prepared in ${swarm_dir}."
echo "Configure ${environment_file}, label one storage node, and populate LinkedIn and Resend secrets before deployment."
