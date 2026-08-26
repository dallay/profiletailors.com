# Private Beta Backup and Restore Status (DALLAY-557)

**Audience:** On-call operator, release manager.
**Sister documents:** [`production-docker-swarm.md`](./production-docker-swarm.md#backups), [`private-beta-incident-response.md`](./private-beta-incident-response.md).

This document records what is currently rehearsed, what is documented-only, and what is
explicitly not yet rehearsed for backup and restore of the private beta on the
single-VPS topology. It deliberately distinguishes the three so an operator does not
mistake a documented procedure for an exercised one.

## Single-VPS storage topology (reference)

The Swarm stack `profiletailors-smp-dz2yer` (or the equivalent target stack) places
stateful services on the node labelled `profiletailors.storage=true`:

- `postgresql` — Postgres 18 with data persisted in a named Docker volume
  `postgres_data`.
- `backend` — host bind mount `/var/lib/profiletailors/media` (configurable through
  `SWARM_MEDIA_PATH`) holding media assets.

Both are anchored on the same labelled node. Losing that node means losing access to the
data unless a recent backup has been copied off-host. This is an explicit limitation of
the initial topology; full HA requires external PostgreSQL and shared object storage
before increasing backend replicas (see [`production-docker-swarm.md`](./production-docker-swarm.md)).

## Rehearsed (verified)

- **PostgreSQL backup command documented.** The full procedure is described in
  `docs/infrastructure/production-docker-swarm.md#backups`: a `pg_dump` against the running
  `postgresql` task, or a filesystem-aware backup of `/var/lib/postgresql`. The
  documentation is current as of 2026-08.
- **Media backup command documented.** The same section documents a copy of
  `/var/lib/profiletailors/media` with a filesystem-aware backup tool. The
  documentation is current as of 2026-08.
- **Last-known-good revision retrievable.** Every Release Please release tag and the
  corresponding image digest are stored in the GitHub repository
  (`ghcr.io/dallay/profiletailors-smp:vYYYY.MM.DD[-rcN]@sha256:…`). Rolling back to a
  previous revision is a `docker stack deploy` with the previous image tag, gated by the
  Swarm `update_config.failure_action: rollback` declared in
  `infra/apps/smp/swarm/stack.yaml`.
- **Rollback procedure rehearsed at the application layer.** The publishing safe-off and
  re-enable procedure has been exercised against a development stack (see the
  `private-beta-launch-readiness-runbook.md` Git history). The reverse operation has been
  verified by the focused publishing test suite
  (`PublishingWorkerTest.release_expired_claims_propagates_failure_before_claim`
  and related tests in
  `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/`).

## Documented but not yet exercised in production

- **End-to-end PostgreSQL restore drill.** No automated restore drill has been run against
  the Fenix `profiletailors-smp-dz2yer` stack. The procedure is documented but its
  real-world timing, recovery point, and any operator friction have not been measured.
- **End-to-end media restore drill.** Same as above. The bind-mount copy and restore
  procedure is documented; a full recovery has not been timed against the labelled
  storage node.
- **Cross-node restore simulation.** No procedure has been rehearsed for the
  "labeled storage node is lost" scenario. The full HA fix requires external
  PostgreSQL and shared object storage; until that is in place, the documented answer
  is to recover the labelled node and its volumes, or restore backups onto a
  replacement node (see `production-docker-swarm.md#stateful-services-are-unavailable-after-losing-the-storage-node`).

## Explicitly not yet rehearsed (and why)

- **Provider-backed live rollback.** The publishing path has been validated against the
  WireMock LinkedIn stack and against the `USER_REPORTED_OPERATIONAL` production path
  (publish-now + scheduled + multiline text + image; owner-validated). A formal rollback
  while a real LinkedIn delivery is in flight has not been exercised.
- **Cohort-impact rollback.** No rollback has been performed while a real beta cohort
  is mid-publish. The "real cohort" rehearsal is gated on the invitee journey
  acceptance (DALLAY-558).

## Recovery targets and policy

| Asset | RPO target | RTO target | Owner |
|---|---|---|---|
| PostgreSQL data | 24 hours | 4 hours | Yuniel |
| Media assets | 24 hours | 4 hours | Yuniel |
| Audit ledger (append-only) | 24 hours | 4 hours | Yuniel |
| Invitation tokens (HMAC) | Last successful request | N/A — invalidated at the source | Yuniel |

These targets are aspirational and not yet measured against the single-VPS topology.
They MUST be revised after the first live restore drill.

## What this document is NOT

- It is **not** a replacement for the corporate backup-and-restore runbook. The
  private beta runs on a single VPS and the recovery story is "restore from the
  most recent off-host backup"; production-equivalent cross-region DR is out of
  scope until the topology changes.
- It is **not** an automated restore drill. The "Rehearsed" section lists what has
  been verified; the "Documented but not yet exercised" section lists what is
  written down but unverified; the "Explicitly not yet rehearsed" section lists
  what is not even documented in runbook form yet.
- It is **not** a ticket to close the DoD item for backup and restore. The item
  closes when the "Documented but not yet exercised" section is empty, which
  requires a live restore drill against Fenix. That drill is the next action item
  in [`private-beta-incident-response.md`](./private-beta-incident-response.md#manual-review-cadence-and-alert-thresholds)'s
  weekly engineering review cadence.
