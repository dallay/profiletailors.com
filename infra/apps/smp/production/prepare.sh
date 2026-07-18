#!/usr/bin/env bash

set -euo pipefail

production_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
secrets_dir="${production_dir}/secrets"
environment_file="${production_dir}/.env"

umask 077
mkdir -p "$secrets_dir"

if [ ! -f "$environment_file" ]; then
    cp "${production_dir}/.env.example" "$environment_file"
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

echo "Production configuration prepared in ${production_dir}."
echo "Set PUBLIC_ORIGIN and image names in ${environment_file}."
echo "Add the LinkedIn and Resend credentials under ${secrets_dir} before enabling those integrations."
