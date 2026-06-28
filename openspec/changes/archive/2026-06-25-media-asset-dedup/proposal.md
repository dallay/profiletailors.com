# Proposal: Media Asset Deduplication — Content-Addressed Storage

> **Status:** Superseded by spec.md v3.2
> **Author:** Kerrigan (Architecture)
> **Date:** 2026-06-24
> **Revision:** v3.1-clean → superseded
>
> **This document is historical.** It was the proposal that launched the SDD cycle. The
> authoritative source of truth is now `spec.md` (revision v3.2). Do not use this
> document for implementation decisions.

---

## Why this is superseded

The approved implementation follows `spec.md` v3.2, which introduced critical corrections
not reflected here:

| Topic                   | This proposal says  | spec.md v3.2 says            |
|-------------------------|---------------------|------------------------------|
| GC on blob rows         | DELETEs the row     | UPDATEs to GARBAGE_COLLECTED |
| detected_media_type     | NOT NULL            | nullable until READY         |
| file_size_bytes         | NOT NULL            | nullable until READY         |
| GarbageCollector status | DELETE row          | UPDATE to GARBAGE_COLLECTED  |
| Expiration              | PENDING_UPLOAD only | PENDING_UPLOAD + UPLOADING   |
| Canonical extension     | from declared MIME  | from detected MIME           |
| Byte count validation   | not specified       | required in upload           |

Key new states and behaviors in v3.2:

- `GARBAGE_COLLECTED` blob status (GC never deletes rows — preserves FK safety with soft-deleted
  assets)
- `detected_media_type` and `file_size_bytes` nullable on blob rows until READY
- UPLOADING assets also expire (>24h → FAILED, orphan blob → READY_FOR_GC)
- Canonical storage key uses detected MIME, not declared
- Upload validates actual byte count against declared size
- DELETE and expiration use SELECT FOR UPDATE to prevent races

---

## Original Content (outdated — do not use)

The sections below are preserved for historical reference only. They do not reflect the
approved implementation.

### Original Blob Lifecycle (OUTDATED)

> **OUTDATED:** GC deleted blob rows. This caused FK failures because soft-deleted assets
> still reference the blob row. Fixed in spec.md v3.2: GC UPDATE → GARBAGE_COLLECTED.

```
READY blobs are not deleted synchronously on asset deletion. Instead, they are eligible
for garbage collection once no active asset references them.

When an asset is deleted:
1. media_assets.status → DELETED
2. Check if blob has more active assets: SELECT COUNT(*) FROM media_assets
   WHERE workspace_id=? AND file_hash=? AND status NOT IN ('DELETED','FAILED')
3. If COUNT == 0: workspace_file_blobs.status → READY_FOR_GC, orphaned_at → now()
4. If COUNT > 0: blob row unchanged

BlobGarbageCollector job (every hour):
- Selects blobs with status = 'READY_FOR_GC' AND orphaned_at < now() - 7 days
- FOR UPDATE SKIP LOCKED for concurrent workers
- Deletes storage object, then deletes blob row  ← OUTDATED: now UPDATE → GARBAGE_COLLECTED
- On failure: increments gc_failure_count, updates last_gc_attempt_at
```

### Original Blob Statuses (OUTDATED)

> **OUTDATED:** Did not include GARBAGE_COLLECTED.

```
UPLOADING, READY, FAILED, READY_FOR_GC
```

### Original workspace_file_blobs schema (OUTDATED)

> **OUTDATED:** detected_media_type and file_size_bytes were NOT NULL. This broke the
> flow because both are only known after upload (blob is created at PUT time, before upload).

```sql
CREATE TABLE workspace_file_blobs (
    workspace_id        VARCHAR(64)    NOT NULL,
    file_hash          CHAR(64)       NOT NULL,
    storage_key        VARCHAR(255)   NOT NULL,  ← OUTDATED: nullable until READY
    file_size_bytes   BIGINT         NOT NULL,  ← OUTDATED: nullable until READY
    detected_media_type VARCHAR(64)   NOT NULL,  ← OUTDATED: nullable until READY
    status             VARCHAR(20)   NOT NULL DEFAULT 'UPLOADING',
    failure_reason     TEXT,
    orphaned_at        TIMESTAMPTZ,
    gc_failure_count   INT          NOT NULL DEFAULT 0,
    last_gc_attempt_at TIMESTAMPTZ,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (workspace_id, file_hash),

    CONSTRAINT chk_blob_status CHECK (status IN (
        'UPLOADING','READY','FAILED','READY_FOR_GC'  ← OUTDATED: missing GARBAGE_COLLECTED
    )),
    CONSTRAINT chk_blob_hash_format CHECK (
        file_hash ~ '^[a-f0-9]{64}$'
    )
);
```

### Original Migration (OUTDATED)

> **OUTDATED:** Did not mention nullable strategy for blob fields.

```
1. Schema deployment (single Liquibase changeset):
   - Add file_hash, detected_media_type, failure_reason, updated_at columns to media_assets
   - Make storage_key nullable on media_assets
   - Create workspace_file_blobs table with all fields
   - Add FK constraint
   - Add CHECK constraints for status and hash format
   - file_hash is NOT NULL (clean DB — nothing to backfill)

2. Ship to production — app + schema together, no feature flag, no backfill
```

### Original v3.1 → v3.1-clean Changes (OUTDATED)

| # | Change                                                               | Status in v3.2                                |
|---|----------------------------------------------------------------------|-----------------------------------------------|
| 1 | Kept READY_FOR_GC, orphaned_at, gc_failure_count, last_gc_attempt_at | ✅ kept                                        |
| 2 | Kept BlobGarbageCollector                                            | ✅ kept, but GC → GARBAGE_COLLECTED not DELETE |
| 3 | Removed MediaAssetBackfillJob                                        | ✅ kept                                        |
| 4 | Removed feature flag phases                                          | ✅ kept                                        |
| 5 | Simplified migration to single schema deploy                         | ✅ kept                                        |
| 6 | Kept deferred GC instead of immortal blobs                           | ✅ kept                                        |
| 7 | Retention policy fixed at 7 days                                     | ✅ kept                                        |

---

## Use spec.md v3.2

All implementation decisions must reference:

- **`spec.md`** (revision v3.2) — authoritative specification
- **`DESIGN.md`** — technical implementation guide
- **`tasks.md`** — ordered implementation task checklist
