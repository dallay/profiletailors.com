# Data Retention Framework — Acceptance Criteria Verification

> **Status:** Internal remediation plan — gap assessment, not an implemented framework
> **Date:** 2026-08-02
> **Classification:** Internal — Product
>
> **IMPORTANT:** The planned retention governance API (`/api/governance/retention/*`), the
> `retention_periods` table, and the purge/hold/tombstone orchestration described in earlier
> drafts of this document **do not exist in the codebase**. This file has been corrected to
> verify each criterion against what actually exists. The authoritative gap/plan register is
> [`docs/compliance/retention-and-erasure-control-plan.md`](compliance/retention-and-erasure-control-plan.md)
> and the per-activity status lives in
> [`docs/compliance/data-inventory.yaml`](compliance/data-inventory.yaml).

## What Actually Exists Today (verified 2026-08-02)

| Capability | Evidence | Status |
| ---------- | -------- | ------ |
| Retention control registered in the governance compliance model | `compliance_controls` row `ctrl-privacy-data-retention` (`PRIVACY.DATA_RETENTION`, "Data retention and deletion"), seeded by `server/smp/src/main/resources/db/changelog/governance/003-seed-compliance-controls.yaml`; applicability rule `ctrlrule-privacy-retention-001` required for RELEASE `mvp` + MARKET `EEA` | Implemented |
| Retention control evaluation / release gate | `POST /api/governance/compliance/evaluations`, `GET /api/governance/compliance/release-gate` (`ComplianceController`) | Implemented |
| Media orphan garbage collection | `BlobGarbageCollector` (hourly via `MediaReconcilerScheduler`): physically deletes orphaned storage objects past the 7-day retention period; `MediaAssetExpirationJob` (6-hourly) transitions stale `PENDING_UPLOAD`/`UPLOADING` assets to `FAILED` and schedules orphaned blobs for GC | Implemented (media only) |
| Data subject request expiry | `FindExpiredRequestsJob` (daily via `PrivacyScheduler`) discovers DSRs past their 30-day retention expiry and deletes them | Implemented (privacy only) |
| Password-reset token cleanup | `PasswordResetTokenCleanupScheduler` deletes expired password-reset tokens beyond the configured retention (default: 24 h interval, 5 m initial delay) | Implemented (identity only) |
| OAuth disconnect deletion with tombstone (slice 1, 2026-09-29) | `DisconnectProviderConnectionHandler` deletes accounts, credential, and connection transactionally and records `credential_deletion_tombstones`; replays are idempotent and restored rows are re-deleted | Implemented (OAuth disconnect scope) |
| Expired orphan-credential purge + dry-run (slice 1, 2026-09-29) | `CredentialRetentionJob` (per-provider thresholds from `publishing.credentials.retention`, batched `SKIP LOCKED`, skips live connections) with `dryRun` report mode; scheduler disabled by default | Implemented (orphan scope, opt-in) |
| Configurable retention-rule engine (rule registration, approvals, holds, purges, tombstones, evidence) | Backend and Liquibase validation on 2026-08-02 found no `RetentionPeriod`/`RetentionPurge`/`DefaultRetentionOrchestrator` code, no `POST /api/governance/retention/rules`, no `retention_periods`/`retention_purge_jobs`/`retention_holds`/`deletion_tombstones`/`retention_purge_evidence` tables, no `V100__retention_governance.xml`, and no `retention-governance.feature` | **Not implemented — planned** |

## Acceptance Criteria Checklist (target state vs. current state)

### Retention governance validation evidence

The 2026-08-02 validation found no shipped retention-rule API or retention-period table:

- `ComplianceController.kt:25-28` is mapped to `/api/governance/compliance`; its handlers at
  lines 68, 91, and 108 expose only `evaluations`, `release-gate`, and `ping`. The backend
  governance route search found no `/api/governance/retention/rules` mapping.
- `db.changelog-master.yaml:51-63` includes governance changelogs `001` through `007`; the
  Liquibase search found no retention governance changelog or `retention_periods` table definition.

### Retention rules are configuration-controlled and traceable to the data inventory

**Target:** Retention rules managed through configuration, linked to `data-inventory.yaml`.

**Current state:** PARTIAL since slice 1 (2026-09-29). `publishing.credentials.retention`
(`CredentialRetentionProperties` → `CredentialRetentionRule`) is configuration-controlled with
`activityId: pa-006`, defaults, and per-provider overrides, and is traceable to
`docs/compliance/data-inventory.yaml` pa-006. No central rule engine: other categories remain
unevaluated configuration, and there is still no rule-registration endpoint or
`retention_periods` table.

### Purges are tenant-safe, resumable and observable

**Current state:** PARTIAL since slice 1 (2026-09-29). The credential purge runs batched
with `FOR UPDATE SKIP LOCKED`, skips credentials referenced by live connections, resolves
thresholds per provider, and emits `credentials.purge.*` metrics; the disconnect flow is
transactional and idempotent via tombstone. The media GC and DSR expiry jobs are unchanged.
There is still no tenant-filterable job API, resume checkpoints beyond batching, or purge job
status endpoints.

### Backups do not silently reintroduce deleted active data

**Current state:** PARTIAL since slice 1 (2026-09-29) for the OAuth disconnect scope:
`credential_deletion_tombstones` records each disconnect deletion, replays are side-effect
free, and a restored-then-present row is re-deleted with a refreshed tombstone. There is
still no general deletion ledger, no backup-expiry job, and no restore-time replay for other
categories — full backup handling remains slice 2.

### Provider-specific cache/retention limits can override defaults

**Current state:** PARTIAL since slice 1 (2026-09-29). `CredentialRetentionRule.resolveFor`
applies `publishing.credentials.retention.providers.<name>` overrides over the defaults, and
the purge job computes one expiry threshold per provider. The pre-existing LinkedIn
social-content cache TTLs remain separately configured. No generic provider-override model
exists for other categories.

### Dry-run/report mode exists

**Current state:** PARTIAL since slice 1 (2026-09-29). `CredentialRetentionJob.run(dryRun=true)`
and `publishing.credentials.retention.dry-run=true` report counts plus non-sensitive sample
IDs without writing. The media GC still runs unconditionally against eligible orphaned blobs.

### Tests cover partial failure, retries and restore scenarios

**Current state:** PARTIAL since slice 1 (2026-09-29). Unit coverage exists for the rule,
properties binding, tombstone validation, idempotent disconnect (including provider mismatch,
unknown connection, and restored-row replay), and the purge job (dry-run, partial failure,
disabled providers, batch validation). `publishing-credential-retention.feature` adds the
HTTP idempotency scenario; the BDD suite result is recorded in the change plan once the run
completes. Restore coverage beyond the disconnect replay remains slice 2.

## Compliance Sign-Off

**Implementation Status:** PARTIAL — slice 1 (OAuth disconnect + orphan-credential purge/dry-run) implemented 2026-09-29; framework-wide rule engine, holds, backup handling, and remaining categories are still planned.

All acceptance criteria for the retention governance framework are **open** until the rule
engine, purge orchestration, holds, tombstones, and evidence paths are implemented and tested.
Nothing in this document should be represented as an operational or deployable retention
framework. Track progress through
[`retention-and-erasure-control-plan.md`](compliance/retention-and-erasure-control-plan.md).

---

**Document prepared by:** Architecture Team
**Date:** 2026-08-02 (corrected — original draft overstated the framework as complete)
**Classification:** Internal — Product & Compliance
