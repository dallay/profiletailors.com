# Private Beta Operator Checklist (DALLAY-557)

**Audience:** On-call operator for the private beta cohort.
**Companion:** [`private-beta-launch-readiness-runbook.md`](./private-beta-launch-readiness-runbook.md) (publishing controls in depth), [`production-docker-swarm.md`](./production-docker-swarm.md) (Swarm topology), [`private-beta-incident-response.md`](./private-beta-incident-response.md) (incident owner and escalation), [`private-beta-correlation-matrix.md`](./private-beta-correlation-matrix.md) (correlation identifiers and redaction policy).

The Swarm stack `profiletailors-smp-dz2yer` (or the equivalent target stack) deploys four
services. This checklist walks them top-to-bottom in the order an operator should verify
them. Each row lists the fastest authoritative check, the authoritative log/URL, and the
failure pattern that should escalate.

## Preconditions

- `STACK_NAME` and `BACKEND_SERVICE` are set (defaults: `profiletailors-smp-dz2yer` and
  `${STACK_NAME}_backend`).
- The operator has Tailscale SSH access to the manager node hosting the stack and read-only
  `docker service inspect` access. No mutation is required for this checklist.
- The current backend release is at least `smp@v0.4.x`. If the running image is `v0.4.0`
  or earlier, treat stale-visibility findings below as "endpoint not present" and escalate.

## Checklist

### 1. Stack health

| Check | Command | Healthy signal | Failure pattern |
|---|---|---|---|
| Service replicas | `docker stack ps "$STACK_NAME" --no-trunc \| awk 'NR==1 \|\| $4 ~ /backend\|postgresql\|cloudflared\|dashboard/'` | Every service shows `1/1` (or `2/2` for dashboard) and `Running` | `0/N`, `Shutdown`, or `Rejected` rows |
| Stack-level events | `docker stack services "$STACK_NAME" --format '{{.Name}} {{.Replicas}} {{.Image}}'` | Replicas match declared mode | `0/1` for a long-running service |
| Recent task restarts | `docker service ps "$BACKEND_SERVICE" --no-trunc \| head -10` | Most recent task has been `Running` for hours, not minutes | Two or more restarts within the last hour |

**Escalate when:** a backend service is at `0/1` for more than two minutes, or any service is
in `Rejected` state.

### 2. Backend readiness and liveness

| Check | Command | Healthy signal |
|---|---|---|
| Readiness | `curl -fsS http://127.0.0.1:9091/actuator/health/readiness` (against the manager via `ssh fenix` or the public `/api/health/ready` URL) | `{"status":"UP"}` |
| Liveness | `curl -fsS http://127.0.0.1:9091/actuator/health/liveness` | `{"status":"UP"}` |
| Startup probe | tail `journalctl` or `docker service logs "$BACKEND_SERVICE"` for `Started SmpApplication` | One line per recent restart, no `APPLICATION FAILED TO START` after it |
| Effective worker toggle | `docker service inspect "$BACKEND_SERVICE" --format '{{ .Spec.TaskTemplate.ContainerSpec.Env }}' \| tr ' ' '\n' \| grep SMP_PUBLISHING_WORKER_ENABLED` | `SMP_PUBLISHING_WORKER_ENABLED=true` for the active cohort, `false` for safe-off |

**Escalate when:** readiness is `DOWN`, the worker toggle silently disagrees with the
Swarm env source, or startup probe lines are missing after a redeploy.

### 3. Database health

| Check | Command | Healthy signal |
|---|---|---|
| PostgreSQL readiness | `docker exec "$(docker stack ps "$STACK_NAME" --format '{{.Name}}.{{.ID}}' \| grep postgresql \| head -1)" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"` | `accepting connections` |
| Schema version | `docker exec … psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c 'SELECT MAX(version) FROM databasechangelog'` | Matches the highest changeset shipped by the deployed image (see `application.yaml` `liquibase.changelog`) |
| Migrations context | Tail backend logs for `Liquibase: Successfully released change log lock` and `Update Summary` | Both present once per startup, no `Failed to release lock` entries |
| Stale-job schema presence | `docker exec … psql -c "\d publication_jobs"` shows `claimed_by_worker`, `claimed_at`, `lease_expires_at` | All three columns exist |

**Escalate when:** `pg_isready` returns `rejecting connections` for more than 30 seconds,
the databasechangelog max version lags behind the image, or `lease_expires_at` is missing.

### 4. Media storage

| Check | Command | Healthy signal |
|---|---|---|
| Mount visibility | `docker exec "$(docker stack ps "$STACK_NAME" --format '{{.Name}}.{{.ID}}' \| grep backend \| head -1)" ls -la /var/lib/profiletailors/media` | Directory exists and is writable by UID `1002` |
| Free space | `df -h /var/lib/profiletailors/media` on the labeled storage node | At least 20% free; trending document on the node label |
| Recent writes | Tail backend logs for `stored-media assetId=… bytes=…` | Steady stream during cohort activity; absence is fine if the cohort has not uploaded yet |

**Escalate when:** mount is read-only, free space falls below 10%, or backend errors with
`MediaStorageUnavailableException`.

### 5. Authentication

| Check | Command | Healthy signal |
|---|---|---|
| Local login | `curl -fsS -X POST https://api.profiletailors.com/api/auth/login -H 'Content-Type: application/json' -H 'Accept: application/vnd.api.v1+json' -d '{"email":"…","password":"…"}'` (only with a test fixture) | `200 OK` with `access_token` and `refresh_token`; no `401`/`403`/network error |
| Refresh cookie issuance | Tail backend logs for `Issued refresh token …` (no body) | One line per successful login |
| JWT issuer resolves | `curl -fsS https://api.profiletailors.com/.well-known/jwks.json` | `200 OK` with `keys` array |

**Escalate when:** login fails for any test account with a `401` and no recent deploy,
`jwks.json` returns non-200, or refresh-token issuance silently stops.

### 6. Waitlist flow

| Check | Command | Healthy signal |
|---|---|---|
| Public join endpoint reachable | `curl -fsS -X POST https://profiletailors.com/api/waitlists/{key}/entries -H 'Content-Type: application/json' -H 'Accept: application/vnd.api.v1+json' -d '{"email":"smoke@example.com","consent":{"version":"…","categories":{"necessary":true}}}'` (test only, then delete the entry) | `201 Created` or `409 Conflict` (duplicate), never `5xx` |
| Welcome email | Check Mailpit capture (`infra/postgres/mailpit.yaml`) or the configured transactional provider log | One email rendered with the welcome template |
| Admin list | `GET /api/admin/waitlist-entries` with an operator token (already documented in `platform-admin.feature`) | `200 OK` with paginated entries |

**Escalate when:** join returns `5xx`, no email is rendered, or the admin list returns
`401`/`403` for an operator that has `WAITLIST_READ`.

### 7. Invitation acceptance

| Check | Command | Healthy signal |
|---|---|---|
| Token issuance (operator) | `POST /api/admin/waitlist-entries/{id}/invitations` with `WAITLIST_INVITE` | `201 Created`; audit event recorded under `WAITLIST_ENTRY_INVITED` |
| Acceptance endpoint reachable | `POST /api/invitations/accept` with a valid `invitationToken` | `200 OK`; workspace and membership visible in the response; token NEVER echoed in the response body |
| Audit integrity | Tail backend logs for `Revoked token leaked into response body` (this should never appear) | Absent |

**Escalate when:** acceptance returns `5xx` for a freshly minted invitation, the response
body contains the raw `invitationToken`, or no audit event is recorded for an invite.

### 8. Publishing worker

The detailed procedure is in
[`private-beta-launch-readiness-runbook.md`](./private-beta-launch-readiness-runbook.md#safe-off).
Quick checks:

| Check | Command | Healthy signal |
|---|---|---|
| Polling loop alive | Tail backend logs for `Polling for next due publication job` | At least one line every `SMP_PUBLISHING_WORKER_POLL_INTERVAL` (default `PT30S`) |
| Stale-claim recovery | Tail backend logs for `Released expired publication-job claims released={N}` | At least one line per poll cycle, `N=0` is healthy |
| Stale visibility | `GET /api/admin/publishing/stale-jobs` with `PUBLISHING_STALE_READ` | `200 OK` with bounded `items[]`; **only available once the deployed backend image is at the Phase 2 release** (see release-provenance notes in `private-beta-launch-readiness/qa-report.md`) |
| Provider delivery category | Tail backend logs for `publication.delivery.{state}.attempt` structured lines | Each line carries `jobId`, `attemptNumber`, `workspaceId`, `category`; no raw provider payload |

**Escalate when:** the polling line is missing for more than `3 × SMP_PUBLISHING_WORKER_POLL_INTERVAL`,
the stale-visibility endpoint returns `404` (means the current release predates Phase 2 —
log the finding, but do **not** assume it is a regression; cross-check the deployed image
digest against `smp@v0.4.1` and the post-Phase-2 release), or the release line appears with a
non-zero `released` count but no operator action has triggered a release.

## What this checklist is NOT

- It is **not** a substitute for the publishing-specific runbook; safe-off and re-enable
  stay governed by [`private-beta-launch-readiness-runbook.md`](./private-beta-launch-readiness-runbook.md).
- It is **not** a security incident response plan; that lives in
  [`private-beta-incident-response.md`](./private-beta-incident-response.md).
- It is **not** a provider-verified or multi-user-verified acceptance record. Every entry
  recorded against this checklist is `USER_REPORTED_OPERATIONAL` until promoted by an
  explicit acceptance run.
