#!/usr/bin/env bash

set -euo pipefail

swarm_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
environment_file="${swarm_dir}/.env"

if [ ! -f "$environment_file" ]; then
    echo "Missing ${environment_file}."
    exit 1
fi

set -a
# shellcheck source=/dev/null
source "$environment_file"
set +a

: "${SWARM_STACK_NAME:?Set SWARM_STACK_NAME in swarm/.env}"
docker stack rm "$SWARM_STACK_NAME"

echo "Stack removal requested. Swarm secrets and persistent volumes were preserved."
