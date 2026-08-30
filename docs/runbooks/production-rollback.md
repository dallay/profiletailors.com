# Production Rollback Runbook

**Last Updated:** 2026-08-30
**Status:** Active
**Scope:** Production Docker Swarm, Docker Compose, Database, and Worker Rollback
**Audience:** Release Manager, On-Call Operator, SRE

---

## Overview

This runbook defines the emergency operational procedure for rolling back Profile Tailors production services. Invoke this procedure immediately when a deployment introduces critical regressions, database locking/corruption, publishing worker anomalies, or elevated error rates that cannot be resolved through rapid hotfixing.

Always prioritize user data integrity and platform availability over new feature delivery.

---

## Changes

### 🎯 Rollback Decision Criteria

Initiate a production rollback if any of the following triggers are met:

1. **Service Unavailability:** Public API ingress (`api.profiletailors.com`) or Web Dashboard returns `>= 5%` 5xx HTTP responses over a 5-minute window.
2. **Health Probe Failures:** Spring Boot Actuator `/actuator/health/readiness` fails for more than 3 consecutive probe intervals (30s).
3. **Publishing Anomaly:** The publishing worker executes duplicate social media posts, ignores lease fencing, or experiences unhandled provider token exceptions affecting multiple workspace accounts.
4. **Database Migration Failure:** Liquibase migrations lock schema tables or fail during container startup without automatic recovery.
5. **Security / Secret Exposure:** Unintended secret leaks or privilege escalation risks identified post-deploy.

---

## Usage

### 🛠️ Step-by-Step Rollback Procedures

#### 1. Emergency Worker Safe-Off (Immediate Circuit Breaker)

If the incident involves automated publishing worker errors (duplicate posting, runaway queue processing):

```bash
# 1. Inspect current worker status
docker service inspect profiletailors-smp_backend --format '{{ .Spec.TaskTemplate.ContainerSpec.Env }}' | tr ' ' '\n' | grep SMP_PUBLISHING_WORKER_ENABLED

# 2. Disable worker in and load the Swarm environment source
# Set SMP_PUBLISHING_WORKER_ENABLED="false" in infra/apps/smp/swarm/.env
set -a
. infra/apps/smp/swarm/.env
set +a

# 3. Apply safe-off rolling update
docker stack deploy --detach=false -c infra/apps/smp/swarm/stack.yaml profiletailors-smp

# 4. Identify the replacement task and verify it is running
REPLACEMENT_TASK_ID="$(docker service ps profiletailors-smp_backend \
  --filter desired-state=running --format '{{.ID}}' | head -n 1)"
docker inspect "$REPLACEMENT_TASK_ID" --format '{{.Status.State}}'

# 5. Verify the replacement task has emitted no polling log entries
docker service logs "$REPLACEMENT_TASK_ID" 2>&1 \
  | grep -E 'Polling for next due publication job|Released expired publication-job claims'
```

Do not continue until the replacement task's state is `running` and the final
command returns no matches (exit status `1`).

> **Note:** Disabling the worker halts poll execution safely. Queued publication jobs remain in `publication_jobs` with status `PENDING` or `CLAIMED` without data loss.

---

#### 2. Service Rollback in Docker Swarm

Swarm maintains previous task definitions for automatic or manual rollback.

##### Option A: Rollback via `just` helper

```bash
# Rollback backend service
just swarm-rollback backend

# Rollback dashboard frontend service
just swarm-rollback dashboard
```

##### Option B: Rollback via Docker CLI

```bash
# Rollback backend service to previous revision
docker service rollback profiletailors-smp_backend

# Rollback dashboard service
docker service rollback profiletailors-smp_dashboard
```

##### Option C: Redeploy Last-Known-Good Image Revision

If a full stack reset to a known release tag/digest is required:

```bash
# 1. Update image tags/digests in infra/apps/smp/swarm/.env
# Example: SMP_IMAGE="ghcr.io/dallay/profiletailors-smp:v2026.08.22-rc1@sha256:..."

# 2. Re-render and deploy stack
just swarm-deploy
```

---

#### 3. Service Rollback in Docker Compose (Single-VPS / Staging)

For deployments operating on single-host Docker Compose (`infra/apps/smp/production/compose.yaml`):

```bash
# 1. Navigate to production stack directory
cd infra/apps/smp/production

# 2. Update .env file with the previous image tags/digests
# Example: BACKEND_IMAGE=profiletailors/smp:v0.1.0

# 3. Pull previous images and restart containers
docker compose --env-file .env -f compose.yaml up -d --wait

# 4. Verify status
docker compose --env-file .env -f compose.yaml ps
```

---

#### 4. Database Migration Rollback Strategy

Profile Tailors uses Liquibase for reactive PostgreSQL schema migrations.

##### Backward Compatibility First (Expand/Contract Pattern)

- DDL changes MUST be backward-compatible (adding nullable columns or new tables).
- Rolling back application container images while keeping new database columns is the preferred and safest path.

##### Handling Destructive or Incompatible DDL

If a migration changed table structures incompatibly:

1. **Stop Application Traffic:**

   ```bash
   # Scale backend replicas to 0 during database recovery
   docker service scale profiletailors-smp_backend=0
   ```

2. **Restore to a Defined Recovery Point:**
   Before restoring PostgreSQL, record the exact target recovery timestamp. Verify what recovery
   point the available backup actually provides; the current single-VPS capability restores the
   most recent verified off-host backup, not an exact PITR timestamp. Explicitly decide whether
   writes after the selected recovery point will be discarded, replayed, or reconciled. Refer to
   [`docs/infrastructure/private-beta-backup-restore-status.md`](../infrastructure/private-beta-backup-restore-status.md)
   for the available backup and restore capability.

3. **Re-apply Safe Revision:**

   ```bash
   # Deploy the documented last-known-good image while the backend remains scaled to 0
   KNOWN_GOOD_SMP_IMAGE="ghcr.io/dallay/profiletailors-smp:v2026.08.22-rc1@sha256:..."
   docker service update --with-registry-auth --image "$KNOWN_GOOD_SMP_IMAGE" profiletailors-smp_backend

   # Scale backend replicas back to 1 after restoration and safe-revision deployment
   docker service scale profiletailors-smp_backend=1
   ```

   Verify that Liquibase `DATABASECHANGELOG` matches the expected state for the known-good image
   and that startup logs show a successful update with no changelog-lock error. Restore application
   traffic only after those checks pass.

---

### 🔍 Verification & Post-Rollback Health Checks

Perform the following verification steps before marking the rollback complete:

1. **Verify Readiness & Liveness Endpoints:**

   ```bash
   # Actuator Health
   curl -f http://localhost:9091/actuator/health
   curl -f http://localhost:9091/actuator/health/readiness
   ```

2. **Check Container Logs for Errors:**

   ```bash
   just swarm-logs backend
   ```

3. **Verify Database Connectivity & Migration Status:**
   Ensure Liquibase `DATABASECHANGELOG` table reflects the expected state and no connection pool exhaustion exists.

4. **Verify Frontend Application Routing:**
   Confirm Web Dashboard (`apps/web/app`) loads and successfully authenticates users without CORS or 5xx errors.

5. **Re-enable Publishing Worker (If Disabled):**
   Once backend stability is confirmed, set `SMP_PUBLISHING_WORKER_ENABLED="true"` and redeploy. Monitor `publication_jobs` for stale claims recovery and normal processing.

---

## Troubleshooting

### 📢 Incident Escalation & Communication

1. **Declare Incident Resolution:** Update internal status channels and log incident timeline.
2. **Notify Affected Users:** If user-facing downtime or publication delays occurred, issue communication per [`docs/infrastructure/private-beta-incident-response.md`](../infrastructure/private-beta-incident-response.md).
3. **Post-Mortem Requirement:** A blameless post-mortem MUST be completed within 48 hours for any production rollback, identifying root cause, prevention measures, and action items.

---

## References

- [`docs/infrastructure/production-docker-swarm.md`](../infrastructure/production-docker-swarm.md)
- [`docs/infrastructure/private-beta-launch-readiness-runbook.md`](../infrastructure/private-beta-launch-readiness-runbook.md)
- [`docs/infrastructure/private-beta-incident-response.md`](../infrastructure/private-beta-incident-response.md)
- [`docs/infrastructure/private-beta-backup-restore-status.md`](../infrastructure/private-beta-backup-restore-status.md)
