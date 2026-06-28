# Archive Report: Media Asset Deduplication (Content-Addressed Storage)

**Change**: `media-asset-dedup`
**Archived**: 2026-06-25
**Archived to**: `openspec/changes/archive/2026-06-25-media-asset-dedup/`
**Verification verdict**: PASS
**Spec revision**: v3.2 (no spec delta — pure implementation defect repair)
**Authoritative spec**: `openspec/specs/media-asset-dedup/spec.md`

---

## Specs Synced

| Domain              | Action  | Details                                                                                                                                                                           |
|---------------------|---------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `media-asset-dedup` | Created | New canonical spec promoted from delta — 9 requirements, 29 scenarios, full data model + API schemas + Liquibase migration + TS frontend contract + idempotency matrix + glossary |

The delta spec was self-contained (no per-domain split under `changes/media-asset-dedup/specs/`),
so per OpenSpec convention it was promoted **verbatim** to
`openspec/specs/media-asset-dedup/spec.md`
as the authoritative source of truth. **No spec revision bump** was required — the verify-time
fix was a pure implementation defect repair (deleted dead `NoOpEventPublisher` class and dead
`testStorageApplicationService` factory), not a spec delta. Spec stays at **v3.2**.

---

## Archive Contents

- `proposal.md` ✅ (superseded marker; spec v3.2 is authoritative)
- `spec.md` ✅ (v3.2, 1186 lines)
- `design.md` ✅
- `tasks.md` ✅ (27/27 tasks complete)
- `apply-progress.md` ✅ (documents the class-collision fix RED→GREEN→REFACTOR)
- `verify-report.md` ✅ (re-verify #2, PASS)
- `state.yaml` ✅ (updated to `current_phase=archive`)
- `exploration.md` ✅

---

## Source of Truth Updated

The following spec now reflects the new behavior:

- `openspec/specs/media-asset-dedup/spec.md` (new, promoted from delta)

This spec governs the workspace-scoped content-addressed storage (CAS) deduplication model for
the media library: `workspace_file_blobs` dedup index, PUT/POST upload flow with streaming hash
verification, deferred GC with 7-day retention and `GARBAGE_COLLECTED` (UPDATE, not DELETE),
24-hour upload TTL on both `PENDING_UPLOAD` and `UPLOADING`, rate limiting (200 creations/hour
per workspace), and TypeScript streaming SHA-256 for files ≥ 100 MB.

---

## Implementation Summary

The Media Asset Deduplication change introduced:

- **CAS deduplication model**: workspace-scoped `(workspace_id, file_hash)` blob index in
  `workspace_file_blobs`; new `PENDING_UPLOAD` / `UPLOADING` / `READY` / `FAILED` asset
  states and `UPLOADING` / `READY` / `FAILED` / `READY_FOR_GC` / `GARBAGE_COLLECTED` blob
  states.
- **Server-authoritative upload flow**: PUT performs dedup check + rate limit; POST streams
  bytes to a temp key, computes SHA-256 server-side, counts actual bytes, verifies both hash
  and size, copies to the canonical key derived from **detected** MIME (not declared), then
  deletes the temp key.
- **Deferred garbage collection**: 7-day retention after a blob becomes orphaned; `FOR UPDATE
  SKIP LOCKED` clause prevents concurrent-worker races; outcome is an UPDATE to
  `GARBAGE_COLLECTED` (the row is preserved to satisfy FK from soft-deleted assets).
- **Asset expiration job**: 24-hour TTL on both `PENDING_UPLOAD` and `UPLOADING` assets;
  expired assets transition to `FAILED` and their blob is re-evaluated under the same
  active-reference rule used by DELETE.
- **Rate limiting**: 200 creations/hour per workspace, checked before any blob/asset insertion.
- **Frontend contract**: streaming SHA-256 (dependency-free, ≥ 100 MB) in `useFileHash.ts`;
  `sanitizeFilename` strips traversal and null bytes; PUT/POST wire format with declared
  MIME, original filename, and full idempotency matrix.
- **Liquibase migration**: single deployment — adds `file_hash`, `detected_media_type`,
  `failure_reason`, `updated_at` to `media_assets`; makes `storage_key` nullable on
  `media_assets`; creates `workspace_file_blobs` with status/hash/nullable-when-UPLOADING
  CHECK constraints.
- **Quality (re-verify #2)**: 677 backend fast tests + 36 Postgres integration tests all
  green with no `--tests` filter; detekt clean. 28/29 spec scenarios fully COMPLIANT;
  1/29 PARTIAL (GC concurrent-worker, structural SQL coverage only — unchanged limitation).

---

## Defect Repair Recorded

A **CRITICAL regression** was flagged by the prior verify (JVM class-name collision between
`NoopEventPublisher` — lowercase 'o' — in `MediaCasHandlersTest.kt` and `NoOpEventPublisher`
— capital 'O' — in `FakeStorageApplicationService.kt`). This was repaired in the apply phase
**without a spec delta** by:

- Deleting the dead public `class NoOpEventPublisher` (zero consumers repo-wide).
- Deleting the dead `testStorageApplicationService(...)` factory.
- Cleaning up six unused imports in `FakeStorageApplicationService.kt`.
- Leaving the canonical, actively-instantiated `private class NoopEventPublisher` in
  `MediaCasHandlersTest.kt` untouched.

Build-output inspection: `find server/smp/build -name 'NoOpEventPublisher*'` returns
**zero** matches; `find server/smp/build -name 'NoopEventPublisher*'` returns **one**
match (the canonical class). The full `:server:smp:test` task (no `--tests` filter) now
exits 0 with 677/0/0/0, and the full `:server:smp:postgresIntegrationTest` task exits 0
with 36/0/0/0. Both `just backend-test-fast` and `just backend-test-postgres` recipes
are UP-TO-DATE/BUILD SUCCESSFUL against the rerun cache.

A meta-evidence defect was also flagged and repaired: `apply-progress.md` previously
included only targeted `--tests` filter invocations in its Commands Run section, which
masked the class collision. `apply-progress.md` now documents both the full RED
(reproduced the `wrong name` error with the full task invocation) and the double full-suite
GREEN run. Future applies in this repo will include the full (no `--tests` filter) backend
task invocations in their Commands Run section by default.

---

## Warnings Carried Over (non-blocking)

1. **Legacy reserve/upload flow and `PROCESSING` status remain.** `CreateUploadedAssetHandler`,
   `UploadAssetHandler`, and `MediaAssetStatus.PROCESSING` are still present alongside the
   CAS flow. Intentional until the frontend's legacy `/v1` path is migrated; documented
   deviation, not a regression.
2. **GC concurrent-worker scenario is structurally proven but not end-to-end exercised.**
   `FOR UPDATE SKIP LOCKED` is verified by `R2dbcMediaRepositoriesPostgresTest` against
   real PostgreSQL, but a multi-worker integration race cannot be run in a single test
   process. Repository contract is the only locking boundary the spec mandates, so this
   remains PARTIAL rather than UNTESTED.
3. **Kotlin compiler warnings** about `java.lang.Long` / `java.lang.Boolean` in adjacent
   R2DBC repository code. Pre-existing, not detekt failures.

---

## SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived. Spec revision
remains **v3.2** (no spec delta required). The full SDD cycle is:

1. explore — `exploration.md`
2. propose — `proposal.md` (superseded by spec v3.2)
3. spec — `spec.md` (v3.2, authoritative)
4. design — `design.md`
5. tasks — `tasks.md` (27 tasks, all complete)
6. apply — `apply-progress.md` (with class-collision fix RED→GREEN→REFACTOR)
7. verify — `verify-report.md` (PASS, 28/29 COMPLIANT + 1/29 PARTIAL, no CRITICAL)
8. **archive — this report**

Ready for the next change.
