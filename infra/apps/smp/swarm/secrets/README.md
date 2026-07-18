# Swarm Secret Sources

## Overview

These local files are source material for immutable Docker Swarm secrets. They must exist only on a
Swarm manager and must never be committed.

## Changes

`../prepare.sh` generates the application secrets and initializes optional integration credentials
with `unconfigured`. `../deploy.sh` creates the named Swarm secrets when they do not already exist.

## Usage

Replace `unconfigured` in `linkedin-client-secret` and `resend-api-key` before enabling those
integrations; Swarm does not accept zero-byte secrets. Rotate a secret by changing its versioned
name in `swarm/.env`, updating its source file, and redeploying.

## Troubleshooting

Swarm secrets are immutable. If a secret name already exists, changing its source file has no
effect. Use a new versioned name instead.

## References

- [Production secrets reference](../../../../../docs/production-secrets.md)
