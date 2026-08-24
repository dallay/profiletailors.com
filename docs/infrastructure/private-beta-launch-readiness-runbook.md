# Private Beta Launch Readiness Runbook (DALLAY-555/557)

**Date:** 2026-08-22
**Status:** Draft — Phase 2.3 GREEN deliverable
**Scope:** Publishing controls for the private beta cohort
**Audience:** Release manager and on-call operator

## Overview

This runbook covers the operator-facing procedures for the publishing controls that
back DALLAY-555 ("Publishing Beta Operations Are Observable and Reversible") and
DALLAY-557 ("Managed Environment Ready for Private Beta"). It documents how to
safe-off and re-enable the publishing worker, how to observe stale work, how stale
leases are recovered automatically, and which environment variables drive each
behavior.

Preconditions (assumed already in place from Phase 4.1 — managed VPS stack):

- Docker Swarm cluster with the `edge` and `data` overlay networks.
- PostgreSQL 18 service backed by a named volume and externalized `db_password` secret.
- Mailpit service for outbound SMTP capture.
- LinkedIn WireMock stack for non-production publishing endpoint simulation.
- `infra/apps/smp/swarm/stack.yaml` deployed with `SMP_PUBLISHING_WORKER_ENABLED`
  according to the cohort's operator policy.

### Operator policy (confirmed for the private beta)

- **Default for managed-VPS rollouts: `SMP_PUBLISHING_WORKER_ENABLED="true"`** (worker
  is **active by default** during the private beta so the cohort exercises the real
  publishing path against the LinkedIn WireMock / production endpoint stack).
- The application-layer default in `application.yaml:129` stays `false` so a fresh
  image without the Swarm env override boots in safe-off and never schedules a poll.
- Safe-off remains a one-line env-var flip + rolling restart; it is the
  incident-response path, not the steady state. Operators MUST rehearse safe-off at
  least once before the first cohort and capture the rehearsal under the
  `safe-off` scope in the Phase 4 evidence ledger.
- Re-enable is the inverse procedure; jobs released via `releaseExpiredClaims` are
  picked up by the next `pollOnce` after the restart, so no persisted work is lost
  when toggling the master switch.

Scope limits: this runbook governs publishing controls only. Activation and
invitation procedures live with the platform-admin runbook; invitee-journey and
E2E rehearsal evidence live with the Phase 3 change record.

## Changes

Phase 2 adds a safe-off worker gate, lease fencing and stale-claim recovery,
global stale-job visibility for authorized platform operators, and reversible
managed-Swarm configuration. The deployment file supplies environment values;
the procedures and evidence requirements remain here in the operator runbook.

## Usage

### Operational controls

#### Safe-off

The publishing worker is safe-off by default at the application layer:
`PublishingWorkerProperties.enabled` defaults to `false` and is rendered from
`application.yaml:129` (`enabled: ${SMP_PUBLISHING_WORKER_ENABLED:false}`).
`PublishingWorkerLifecycle.start()` early-returns when `enabled` is `false`
(`PublishingWorker.kt:704`) so the polling loop is never scheduled.

The managed-VPS deployment overrides that default through `SMP_PUBLISHING_WORKER_ENABLED`
in `infra/apps/smp/swarm/stack.yaml`. Safe-off at the managed layer is therefore a
single-line change followed by a rolling restart.

Before running the commands below, set the deployed stack and backend service names.
The defaults reflect the stack observed during the read-only QA inspection; use the
actual names for the target environment:

```sh
STACK_NAME="${STACK_NAME:-profiletailors-smp-dz2yer}"
BACKEND_SERVICE="${BACKEND_SERVICE:-${STACK_NAME}_backend}"
```

Procedure:

1. Confirm the worker is currently enabled by reading
   `SMP_PUBLISHING_WORKER_ENABLED` from the deployed service:
   `docker service inspect "$BACKEND_SERVICE" --format '{{ .Spec.TaskTemplate.ContainerSpec.Env }}' | tr ' ' '\n' | grep SMP_PUBLISHING_WORKER_ENABLED`.
2. Set `SMP_PUBLISHING_WORKER_ENABLED="false"` in the Swarm env source
   (`infra/apps/smp/swarm/.env` or the equivalent secrets-backed config).
3. Re-render and re-deploy the stack:
   `docker stack deploy -c infra/apps/smp/swarm/stack.yaml "$STACK_NAME"`.
4. Wait for `docker service ps "$BACKEND_SERVICE"` to show the new task as `Running` with no
   startup errors.
5. Confirm the worker is no longer polling by tailing the backend logs and confirming
   absence of `"Polling for next due publication job"` debug entries and absence of
   the `"Released expired publication-job claims"` info line.

Effect of safe-off:

- No new provider delivery calls are initiated.
- Persisted jobs remain in `publication_jobs` and are fully recoverable; status values
  (`PENDING`, `CLAIMED`, `BLOCKED`, `FAILED`) and claim columns (`claimed_by_worker`,
  `claimed_at`, `lease_expires_at`) are preserved.
- The lifecycle early-return at `PublishingWorker.kt:704` means no `pollOnce` invocation
  is scheduled; therefore `releaseExpiredClaims` does not run while safe-off is in effect.

#### Re-enable

Procedure:

1. Set `SMP_PUBLISHING_WORKER_ENABLED="true"` in the Swarm env source.
2. Re-render and re-deploy the stack as in safe-off steps 3 and 4.
3. Confirm the worker resumes polling by tailing the backend logs for
   `"Polling for next due publication job"` and the structured
   `"Released expired publication-job claims released={} leaseThresholdSeconds={}"`
   info line on the first poll.

Effect of re-enable:

- The first poll calls `releaseExpiredClaims(now, staleGrace)`. Any `CLAIMED` row whose
  `lease_expires_at` is earlier than `now - staleGrace` is reset to `PENDING` with the claim
  columns cleared (`R2dbcPublishingRepositories.kt:931-937`). The number of released
  rows is logged without PII.
- After release, `claimNextDue` picks up due `PENDING` jobs and the worker resumes
  normal processing.
- Jobs that were released via `releaseExpiredClaims` immediately before the safe-off
  stay at `PENDING` and are eligible for the next claim — no jobs are silently treated
  as published.

#### Stale visibility

The admin stale-jobs endpoint surfaces stale claims for a platform operator or diagnostic
check without exposing raw provider diagnostics:

`GET /api/admin/publishing/stale-jobs`

The caller must have the `PUBLISHING_STALE_READ` permission. Unauthenticated callers receive
`401`; authenticated principals without the permission receive `403` with the
`PLATFORM_ACCESS_DENIED` problem code.

- Query parameters:
    - `leaseStaleThreshold: string` — ISO-8601 duration string representing the minimum
      age of the expired lease before a row is reported stale. Default/example: `PT5M`
      (`PublishingStaleJobsController.kt:127`).
    - `limit: Int` — cap on returned items. Default `50` (`PublishingApi.kt:261`).
- Response fields per `StaleJobItem` (`PublishingApi.kt:266-276`):
    - `jobId`, `publicationId`, `workspaceId` — identifiers.
    - `claimedByWorker` — opaque UUID in the form `worker-<UUID>`.
    - `claimedAt`, `leaseExpiresAt` — timestamps.
    - `ageSeconds` — seconds elapsed from `claimedAt` to the read instant, clamped to
      `>= 0L` (`PublishingQueryHandlers.kt:195`).
    - `attemptNumber` — current attempt count for the job.
    - `suggestedAction` — canonical literal `"RELEASE_AND_RETRY"`
      (`PublishingQueryHandlers.kt:201`, `const val STALE_JOB_SUGGESTED_ACTION`).

The controller validates the threshold and limit, then dispatches `ListStaleJobsQuery`
through the Mediator bus. Invalid threshold or limit values return `400` with the
`VALIDATION_ERROR` problem code. The handler calls
`PublicationJobRepository.findStaleClaims(now, leaseStaleThreshold)` and applies the
bounded result limit before returning the safe response.

#### Stale recovery

Stale recovery is automatic and does not require a manual reclaim command.

- The worker calls `releaseExpiredClaims` at the start of every poll
  (`PublishingWorker.kt:619`), immediately before `claimNextDue` (`:628`).
- A single structured info log line records the count:
  `"Released expired publication-job claims released={} staleGraceSeconds={}"`
  (`PublishingWorker.kt:621-625`). The log carries no tokens, secrets, URLs, or
  worker UUIDs.
- If `releaseExpiredClaims` itself fails, the exception is propagated and the poll
  does not attempt to claim a new job; the next lifecycle tick will retry the
  release (`PublishingWorkerTest.kt:942-980` asserts this).

Operator implication: under normal conditions the operator does **not** need to run
a manual reclaim command. The first check for a stale-claim investigation should be
the `publication_jobs.lease_expires_at` SQL snapshot (see Troubleshooting) and the
matching `PublishingWorker` info log line.

#### Rollback-safe config

Publishing behavior is fully controlled by the env vars consumed by
`server/smp/src/main/resources/application.yaml`. Each var has an operator-readable
default and is overridable through the Swarm stack.

| Env var                                    | Default | Behavior                                                                                                       |
|--------------------------------------------|---------|----------------------------------------------------------------------------------------------------------------|
| `SMP_PUBLISHING_WORKER_ENABLED`            | `false` | Master switch. `false` short-circuits `PublishingWorkerLifecycle.start()` so no poll is ever scheduled.        |
| `SMP_PUBLISHING_WORKER_POLL_INTERVAL`      | `PT30S` | Cadence at which the lifecycle calls `pollOnce`.                                                               |
| `SMP_PUBLISHING_WORKER_CLAIM_LEASE`        | `PT2M`  | In-flight lease written as `lease_expires_at = now + claimLease` when a job is claimed.                        |
| `SMP_PUBLISHING_WORKER_STALE_GRACE`        | `PT5M`  | Additional age required after lease expiry; recovery releases only when `lease_expires_at < now - staleGrace`. |
| `SMP_PUBLISHING_BLOCKED_RECOVERY_INTERVAL` | `PT5M`  | Cadence at which `BLOCKED` jobs are promoted back to `PENDING`.                                                |
| `SMP_PUBLISHING_MAX_RETRIES`               | `3`     | Capped delivery attempts before a job moves to `BLOCKED`.                                                      |
| `SMP_PUBLISHING_RETRY_BACKOFF`             | `PT5M`  | Delay between retryable delivery attempts.                                                                     |

Lease-aware claim contract (per `publication_jobs` schema):

- `claimed_by_worker` — opaque UUID in the form `worker-<UUID>`. Never a hostname or
  a secret.
- `claimed_at` — `Instant` the row was claimed by a worker.
- `lease_expires_at` — `Instant` after which the claim can become stale; recovery
  releases it only when `lease_expires_at < now - staleGrace`.

Rollback safety:

- Any of the env vars above may be flipped in the Swarm env source and re-deployed
  without rebuilding the image. The defaults baked into `application.yaml` are the
  last-known-good values and remain effective if the env vars are removed.
- Safe-off through `SMP_PUBLISHING_WORKER_ENABLED="false"` is reversible without data
  loss because `publication_jobs` is the source of truth and rows are never deleted
  by the worker.

### Evidence capture (Phase 4)

Every operator-observed entry that supports the DALLAY-555 / DALLAY-557 acceptance
record MUST carry the following fields. A row missing any of them blocks acceptance.

| Field                       | Required format                                                                                    | Notes                                                                                                                    |
|-----------------------------|----------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| UTC time                    | ISO-8601 with `Z`                                                                                  | E.g. `2026-08-22T18:30:00Z`. No local-time entries.                                                                      |
| Hostname                    | FQDN or Swarm node label                                                                           | E.g. `prod-1.eu-west.internal`.                                                                                          |
| Namespace                   | Swarm stack name                                                                                   | `STACK_NAME` (the observed QA default was `profiletailors-smp-dz2yer`).                                                  |
| Release                     | Image tag or digest                                                                                | E.g. `profiletailors/smp:v2026.08.22-rc1` or `@sha256:...`.                                                              |
| Operator                    | Operator handle                                                                                    | First-name + role; no email addresses.                                                                                   |
| Scope                       | One of `safe-off`, `re-enable`, `stale-visibility`, `stale-recovery`, `rollback`, `backup-restore` |                                                                                                                          |
| Result                      | `PASS` / `FAIL` / `OBSERVED`                                                                       | Match the operation class.                                                                                               |
| Classification              | `USER_REPORTED_OPERATIONAL` ONLY                                                                   | MUST NOT be `provider-verified`, `MULTI_USER_VERIFIED`, or any variant that implies provider or multi-user confirmation. |
| Retention                   | Days or policy reference                                                                           | E.g. `365d` or `compliance:evidence-retention`.                                                                          |
| Safe-off state              | `ENABLED` / `DISABLED`                                                                             | Read from the deployed service at the same instant as the observation.                                                   |
| Backup/restore confirmation | `PASS` / `N/A`                                                                                     | Required for any `rollback` or `safe-off` entry.                                                                         |
| Rollback status             | `READY` / `BLOCKED`                                                                                | Required for any `safe-off` or `rollback` entry; link to the runbook section that was rehearsed.                         |

## Troubleshooting

### Symptom: worker is not polling after deploy

1. Confirm `SMP_PUBLISHING_WORKER_ENABLED` on the running task — it must be `"true"`.
2. Confirm the lifecycle early-return did not fire — look for any startup exception in
   the backend logs.
3. Confirm the Liquibase migration context `prod` ran to completion; an early poll
   against a partially-migrated schema will fail. The lifecycle uses `initialDelay =
   pollInterval` precisely to avoid this race (`PublishingWorker.kt:705-707`).

### Symptom: stale jobs accumulating

1. Compare `claim-lease` against the actual poll interval. The poll interval must be
   at least an order of magnitude shorter than `claim-lease`, otherwise `pollOnce`
   cannot catch the lease before it visibly ages out.
2. Confirm `releaseExpiredClaims` ran on the most recent poll — check for the
   `"Released expired publication-job claims released={} leaseThresholdSeconds={}"`
   info line in the backend logs.
3. Run the SQL snapshot below to read the live state of the column.

### Symptom: release failing

`pollOnce` propagates release errors before attempting to claim a new job
(`PublishingWorker.kt:619` and `PublishingWorkerTest.kt:942-980`). When this
happens the operator should:

1. Read the persisted lease state directly:

   ```sql
   SELECT id, publication_id, workspace_id, claimed_by_worker, claimed_at, lease_expires_at
   FROM publication_jobs
   WHERE status = 'CLAIMED'
   ORDER BY lease_expires_at ASC
   LIMIT 50;
   ```
2. Compare against the most recent `claim-lease` config to confirm the threshold is
   being honored.
3. If the schema migration for `publication_jobs` (
   `020-add-publishing-claim-fencing-and-idempotency.yaml`)
   has not been applied against the target database, the claim columns are missing
   and `releaseExpiredClaims` will fail with a SQL error. Re-run Liquibase against
   the `prod` context.

### Symptom: LinkedIn OAuth flow breaks after safe-off / re-enable

Safe-off and re-enable do not touch OAuth state. If OAuth tokens stop refreshing
after a deploy, the issue is independent of publishing controls and lives in the
LinkedIn integration runbook.

## References

- [
  `openspec/changes/private-beta-launch-readiness/specs/publishing/spec.md`](../../openspec/changes/private-beta-launch-readiness/specs/publishing/spec.md)
- [`docs/README.md`](../README.md)
- [`docs/architecture/adr/README.md`](../architecture/adr/README.md)
- [`docs/infrastructure/production-docker-swarm.md`](./production-docker-swarm.md)
