# Production Secrets

[![Docker](https://img.shields.io/badge/Docker-Secrets-2d3748?style=flat-square&logo=docker&logoColor=2496ed)](https://docs.docker.com/compose/use-secrets/)

## Overview

This directory is mounted into the backend as Docker Compose secrets. Secret values are local to
the server and must never be committed.

## Changes

Run `../prepare.sh` to generate the database, JWT, publishing-encryption, media-preview, and
LinkedIn state-signing secrets. The script also creates empty integration credential files.

## Usage

Populate these files before starting the corresponding integration:

- `linkedin-client-secret`
- `resend-api-key`

Keep every file readable only by the deployment operator.

## Troubleshooting

If Compose reports a missing secret, rerun `../prepare.sh`. If the backend rejects a secret, do not
replace it with a placeholder; generate a new value with `openssl rand -base64 32`.

## References

- [Production secrets reference](../../../../../docs/production-secrets.md)
