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

**Current state:** NOT IMPLEMENTED. No rule-registration endpoint or `retention_periods` table
exists. The only configuration-controlled artifact is the seeded compliance control
`ctrl-privacy-data-retention`, which declares the *requirement* ("retained only as long as
necessary and deleted or anonymised per the data inventory retention schedule") but has no
rule engine behind it. The data inventory records retention targets as evidence, not enforced
configuration.

### Purges are tenant-safe, resumable and observable

**Current state:** NOT IMPLEMENTED as a framework. The only purge-like jobs are the media GC
(hourly, media scope only) and the DSR expiry job (daily, privacy scope only). Neither exposes
a tenant-filterable job API, resume checkpoints, or purge job status endpoints.

### Backups do not silently reintroduce deleted active data

**Current state:** NOT IMPLEMENTED. No deletion ledger, tombstone records, or backup
re-deletion job exists. Documented as a required control in
`retention-and-erasure-control-plan.md` ("Maintain a deletion ledger that can be replayed after
restoration"), not yet built.

### Provider-specific cache/retention limits can override defaults

**Current state:** NOT IMPLEMENTED. No provider-override model exists. The Resend/LinkedIn
retention interactions are handled by the providers' own policies, not by platform enforcement.

### Dry-run/report mode exists

**Current state:** NOT IMPLEMENTED. No dry-run flag, purge report endpoint, or evidence
generation exists. The media GC runs unconditionally against eligible orphaned blobs.

### Tests cover partial failure, retries and restore scenarios

**Current state:** NOT IMPLEMENTED. There is no `retention-governance.feature` BDD suite. The
media GC and DSR expiry jobs have unit coverage (e.g. `StaleAssetReconciler`-related tests),
but no retention-specific BDD scenarios exist.

## Compliance Sign-Off

**Implementation Status:** NOT COMPLETE — framework is planned.

All acceptance criteria for the retention governance framework are **open** until the rule
engine, purge orchestration, holds, tombstones, and evidence paths are implemented and tested.
Nothing in this document should be represented as an operational or deployable retention
framework. Track progress through
[`retention-and-erasure-control-plan.md`](compliance/retention-and-erasure-control-plan.md).

---

**Document prepared by:** Architecture Team
**Date:** 2026-08-02 (corrected — original draft overstated the framework as complete)
**Classification:** Internal — Product & Compliance
