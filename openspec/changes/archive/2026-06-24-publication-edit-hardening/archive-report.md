# Archive Report: Publication Edit Hardening

**Change**: `publication-edit-hardening`
**Archived**: 2026-06-24
**Mode**: openspec
**Status at archive**: PASS

---

## Change Summary

Hardening follow-up to the `edit-publication-composer` change. This change:

1. Refactored `R2dbcPublishingRepositories.kt` to clear pre-existing Detekt `LargeClass` and
   `LongMethod` violations without changing behavior.
2. Added 5 dedicated `CreatePostModal` edit-mode unit tests.
3. Added 3 Playwright E2E specs for the scheduler edit flow (TC-17, TC-17A, TC-17B).

---

## Spec Delta Merged

| Field        | Value                                                                                          |
|--------------|------------------------------------------------------------------------------------------------|
| Delta spec   | `openspec/changes/publication-edit-hardening/spec.md`                                          |
| Main spec    | `openspec/specs/publishing/spec.md`                                                            |
| Merge action | Added new `### Requirement: Publication Edit Hardening Quality Gates` section with 3 scenarios |

**Merged scenarios:**

1. Backend publishing quality gate passes after persistence hardening
2. Composer edit mode has focused regression coverage
3. Scheduler publication edit flow is protected end-to-end

---

## Verification Summary

| Metric                                     | Result                               |
|--------------------------------------------|--------------------------------------|
| Tasks total                                | 16                                   |
| Tasks complete                             | 16                                   |
| Verification verdict                       | PASS                                 |
| CRITICAL issues                            | 0                                    |
| `./gradlew build`                          | ✅ Passed                             |
| `./gradlew :server:smp:check` (Detekt)     | ✅ Passed (0 violations)              |
| Frontend lint (Biome)                      | ✅ 0 issues                           |
| Frontend unit tests (Vitest)               | ✅ 589/589 passed                     |
| Frontend E2E (scheduler-edit-post.spec.ts) | ✅ 3/3 passed (TC-17, TC-17A, TC-17B) |
| Spec scenarios compliant                   | ✅ 10/10                              |

---

## Archive Contents

| Artifact       | Path                                                                               |
|----------------|------------------------------------------------------------------------------------|
| Proposal       | `openspec/changes/archive/2026-06-24-publication-edit-hardening/proposal.md`       |
| Spec (delta)   | `openspec/changes/archive/2026-06-24-publication-edit-hardening/spec.md`           |
| Design         | `openspec/changes/archive/2026-06-24-publication-edit-hardening/design.md`         |
| Tasks          | `openspec/changes/archive/2026-06-24-publication-edit-hardening/tasks.md`          |
| Apply progress | `openspec/changes/archive/2026-06-24-publication-edit-hardening/apply-progress.md` |
| Verify Report  | `openspec/changes/archive/2026-06-24-publication-edit-hardening/verify-report.md`  |
| State          | `openspec/changes/archive/2026-06-24-publication-edit-hardening/state.yaml`        |

---

## Source of Truth Updated

- `openspec/specs/publishing/spec.md` — new
  `### Requirement: Publication Edit Hardening Quality Gates` section merged from delta spec

---

## Key Implementation Artifacts

### Backend

- `R2dbcPublishingRepositories.kt` — refactored: `insertOrUpdate` extracted to private helpers (
  `PUBLICATION_WRITE_COLUMNS`, `bindPublicationUpdateParams`, `bindPublicationInsertParams`,
  `bindPublicationWriteParams`). Class reduced from ~506 to ~476 lines. Detekt violations resolved.

### Frontend

- `CreatePostModal.test.ts` — 5 new edit-mode unit tests covering prefill, channel lock, updatePost
  branch, updated emission, and error surfacing
- `e2e/specs/scheduler-edit-post.spec.ts` — 3 Playwright specs: TC-17 (full flow), TC-17A (priority
  pre-fill), TC-17B (channel lock in E2E)
- `e2e/fixtures/scheduler-mocks.ts` — PATCH mock for `updatePost` (lines 222–257)

---

## Suggestions on Record (not blocking archive)

1. Replace `waitForTimeout(300)` in E2E tests with explicit `expect(...).toBeVisible()` waits for
   more deterministic behavior
2. Add E2E test for edit-mode error path (failed PATCH response)
3. Consider snapshot tests for `CreatePostModal.vue`

---

## SDD Cycle Complete

This change has been fully planned, implemented, verified, and archived.
Ready for the next change.
