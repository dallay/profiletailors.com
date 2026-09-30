# Data Retention — Quick Reference (Current State)

**Last Updated: 2026-09-29

> **For:** On-call operators, compliance officers
> **Updated:** 2026-09-29
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
| OAuth disconnect (`DisconnectProviderConnectionHandler`) | On request | Credential + connection + content, tombstoned | Immediate |
| Expired credential purge (`CredentialRetentionJob`) | 6 h, disabled by default | Orphaned expired OAuth credentials | P30D default, per-provider override |
| Credential dry-run | On demand (`dry-run=true`) | Report only, writes nothing | — |

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
| "Retention rules are config-controlled" | Only `publishing.credentials.retention` (pa-006) plus the pre-existing media/password-reset settings; no central rule engine |
| "Purge jobs are resumable/tenant-safe" | True for the credential purge (batched, `SKIP LOCKED`, skips live connections) and media GC; no generic job API |
| "There is a dry-run purge mode" | Yes for the credential purge (`dry-run=true`); media GC has none |
| "`retention-governance.feature` covers this" | That BDD suite does not exist |

## Key Metrics to Watch

- GC failures / orphan counts (media job logs)
- DSR expiry job runs (privacy job logs)
- `data-inventory.yaml` evidence states (update on any implementation change)

---

**Last Updated: 2026-09-29
**Version:** 2.1 (slice 1: OAuth disconnect tombstone + credential purge/dry-run; framework API still planned)
**For questions:** [retention-and-erasure-control-plan.md](compliance/retention-and-erasure-control-plan.md)
