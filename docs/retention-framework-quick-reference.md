# Data Retention — Quick Reference (Current State)

> **For:** On-call operators, compliance officers
> **Last Updated:** 2026-08-30
>
> **Note:** The `/api/governance/retention/*` governance API is **planned, not implemented**.
> Do not script against it. This card covers only what runs today.

## What Runs Today

| Job | Cadence | What it deletes | Retention period |
| --- | ------- | --------------- | ---------------- |
| Media blob GC (`BlobGarbageCollector`) | Hourly | Orphaned media storage objects | 7 days |
| Media asset expiration (`MediaAssetExpirationJob`) | Every 6 h | Stale `PENDING_UPLOAD`/`UPLOADING` assets → `FAILED` | — |
| DSR expiry (`FindExpiredRequestsJob`) | Daily | Data subject requests past expiry | 30 days |
| Password-reset token cleanup | 24 h | Expired password-reset tokens | Configurable |

## Quick Checks

1. **Is retention enforced for a data class?** Look it up in
   `docs/compliance/data-inventory.yaml` → `retention-and-erasure-control-plan.md`. `Partial` /
   `Not implemented` means no enforced end-to-end control.
2. **Are media blobs piling up?** Confirm the GC scheduler is enabled and check job logs for
   failures (`BlobGarbageCollector`). A blob with a live database reference is not orphaned and
   will not be collected.
3. **Are old DSRs still present?** Verify `PrivacyScheduler` runs and the request type is in
   scope of `FindExpiredRequestsJob`.
4. **Retention control status:** `GET /api/governance/compliance/release-gate` shows whether the
   retention control (`ctrl-privacy-data-retention`) passes for the configured scope.

## NOT Available (Planned)

 - `POST /api/governance/retention/rules` — planned rule registration (not implemented; no `retention_periods` table)
- `/api/governance/retention/purges` — purge job scheduling/status/resume
- `/api/governance/retention/holds` — legal/operational holds
- `/api/governance/retention/status` — framework health endpoint
- Deletion tombstones and purge evidence endpoints

## Common Errors & Reality

| Belief | Reality |
| ------ | ------- |
| "Retention rules are config-controlled" | Only the compliance control is registered; no rule engine exists |
| "Purge jobs are resumable/tenant-safe" | Only the four fixed jobs above run; no job API |
| "There is a dry-run purge mode" | No dry-run exists |
| "`retention-governance.feature` covers this" | That BDD suite does not exist |

## Key Metrics to Watch

- GC failures / orphan counts (media job logs)
- DSR expiry job runs (privacy job logs)
- `data-inventory.yaml` evidence states (update on any implementation change)

---

**Last updated:** 2026-08-29
**Version:** 2.0 (corrected — v1.0 described a not-yet-implemented framework)
**For questions:** [retention-and-erasure-control-plan.md](compliance/retention-and-erasure-control-plan.md)
