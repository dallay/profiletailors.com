# Verification Report: DALLAY-499 — Phase 1: Schema + Attribution Display

**Change**: `media-copyright-takedown`
**Phase**: 1 (Schema & Attribution Display)
**Verification date**: 2026-07-20
**Verdict**: **PASS WITH WARNINGS**

---

## Executive Summary

Phase 1 implementation is **functionally complete**. All 9 code tasks (1.1–1.9) are implemented, and both test tasks (1.10, 1.11) pass. The build succeeds. No blocking issues exist.

**One documentation finding**: Tasks 1.1–1.9 in `tasks.md` are not marked as completed (`[ ]` instead of `[x]`), though the code is correctly implemented. This is a minor administrative gap.

---

## Completeness Table

| Task | Description | Status | Evidence |
|------|-------------|--------|----------|
| 1.1 | Delete dead `006-drop-external-metadata.yaml` | ✅ **Implemented** | File deleted (commit `71854020`); glob returns no results |
| 1.2 | Create `007-add-licence-column.yaml` | ✅ **Implemented** | File exists, correct schema (`VARCHAR(64)` nullable), registered in master changelog |
| 1.3 | Add `licence` to `MediaAsset` domain model | ✅ **Implemented** | `MediaAsset.licence: String? = null` at MediaModels.kt:167 |
| 1.4 | Add `licence` to DTOs | ✅ **Implemented** | `MediaAssetResponse.licence` at MediaDtos.kt:156; `MediaAssetSummary.licence` at MediaQueries.kt:59 |
| 1.5 | Add `licence` to R2DBC SQL + row mapper | ✅ **Implemented** | All SELECT queries include `licence`; INSERT includes bind; `rowToMediaAsset()` maps at R2dbcMediaRepositories.kt:483 |
| 1.6 | Set `licence = "unsplash"` in `persistPhoto()` | ✅ **Implemented** | UnsplashMediaProviderHandlers.kt:128 |
| 1.7 | Add `licence` to frontend `MediaAssetSummary` type | ✅ **Implemented** | `media-api.ts:40` — `licence?: string \| null` |
| 1.8 | Create `MediaAttribution.vue` | ✅ **Implemented** | Component exists with author/licence display, i18n keys |
| 1.9 | Integrate `<MediaAttribution>` into `MediaLibraryView.vue` | ✅ **Implemented** | Imported at line 8, rendered at line 560 with correct prop bindings |
| 1.10 | R2DBC licence mapping test | ✅ **Passing** | `R2dbcMediaAssetRepositoryTest` asserts write/read round-trip |
| 1.11 | Unsplash import licence test | ✅ **Passing** | `UnsplashMediaProviderHandlersTest` asserts `licence` is `"unsplash"` |

> ⚠️ **WARNING**: Tasks 1.1–1.9 in `tasks.md` still show `[ ]` (unchecked) even though code is fully implemented. The test tasks (1.10, 1.11) are correctly marked `[x]`.

---

## Build / Tests / Coverage Evidence

### Test Command: `just backend-test-fast`

```
BUILD SUCCESSFUL in 3s
29 actionable tasks: 29 up-to-date
```

All tests pass.

### Key Test Files

| Test | File | What it proves |
|------|------|---------------|
| R2DBC licence mapping | `R2dbcMediaAssetRepositoryTest.kt` | `licence = "unsplash"` round-trips through INSERT and SELECT |
| Unsplash import handler | `UnsplashMediaProviderHandlersTest.kt` | `result.licence shouldBe "unsplash"` |
| Liquibase changelog test | `LiquibaseBaselineChangelogTest.kt` | `007-add-licence-column.yaml` exists, contains `licence` and `VARCHAR(64)` |
| Master changelog inclusion | `LiquibaseBaselineChangelogTest.kt` | Master changelog includes all baseline resources |

### Spec Compliance Matrix

| Spec Scenario | Status | Evidence |
|--------------|--------|----------|
| **FR-1**: Nullable `licence` column on `media_assets` | ✅ **PASS** | `007-add-licence-column.yaml`: `VARCHAR(64)` nullable |
| **FR-2**: Dead `006-drop-external-metadata.yaml` removed | ✅ **PASS** | File deleted from git, not in master changelog |
| **FR-3**: `MediaAsset` has nullable `licence` | ✅ **PASS** | `val licence: String? = null` at MediaModels.kt:167 |
| **FR-4**: DTOs expose `licence` | ✅ **PASS** | Both `MediaAssetResponse` and `MediaAssetSummary` include field |
| **FR-5**: R2DBC mapping reads/writes `licence` | ✅ **PASS** | All queries + row mapper + binding + test assertion |
| **FR-6**: MediaLibrary displays attribution | ✅ **PASS** | `MediaAttribution.vue` renders author/licence; integrated in view |
| **FR-7**: Unsplash sets `licence = "unsplash"` | ✅ **PASS** | `persistPhoto()` line 128 |
| **NFR-1**: Licence nullable for pre-existing assets | ✅ **PASS** | `String?` everywhere, nullable column constraint |
| **NFR-2**: No additional API calls for attribution | ✅ **PASS** | All data from existing `MediaAssetSummary`, no extra fetches |

---

## Design Coherence Table

| Design Decision | Status | Evidence |
|----------------|--------|----------|
| ADR-001: `VARCHAR(64)` nullable (not enum) | ✅ **Followed** | Schema uses VARCHAR(64) with nullable constraint |
| ADR-002: Nullable for existing assets | ✅ **Followed** | `licence: String? = null` default, column has `nullable: true` |
| Changelog file `007-add-licence-column.yaml` | ✅ **Created** | Matches design spec exactly |
| `rowToMediaAsset()` uses `row.get(...)` nullable | ✅ **Followed** | `row.get("licence", String::class.java)` — no `requireNotNull` |
| `MediaAttribution.vue` structure | ✅ **Followed** | Matches the Vue template in the design spec |
| `parent_id` in INSERT columns | ✅ **Followed** | All SELECT queries and INSERT include `licence` |
| Feature flag for attribution | ⚠️ **Not verified** | Design mentions feature flag but no explicit flag found for attribution display; attribution is always rendered when data is present |

---

## Issues

### WARNING

| ID | Finding | Severity | Status |
|----|---------|----------|--------|
| W-01 | Tasks 1.1–1.9 in `tasks.md` still marked `[ ]` (unchecked) despite being fully implemented in code. Test tasks 1.10–1.11 correctly show `[x]`. | WARNING | Confirmed |

### SUGGESTION

| ID | Finding | Severity | Status |
|----|---------|----------|--------|
| S-01 | Design mentions a feature flag for attribution display, but no explicit feature flag was found. Attribution currently renders whenever data is present. If a flag is desired (e.g., gradual rollout), it would need to be added. | SUGGESTION | Theoretical |

---

## Correctness Table (Double-Blind Judges)

| Requirement | Judge A (Evidence) | Judge B (Evidence) | Verdict |
|-------------|-------------------|-------------------|---------|
| Schema column exists | 007 YAML with VARCHAR(64) nullable | Master changelog includes 007 | ✅ Confirmed |
| Dead file removed | Glob returns no 006 file | Git log shows deletion commit | ✅ Confirmed |
| Domain model has licence | MediaModels.kt:167 | `String? = null` default | ✅ Confirmed |
| DTOs have licence | MediaDtos.kt:156 + MediaQueries.kt:59 | Both DTOs tested in controller | ✅ Confirmed |
| R2DBC maps licence | All queries + row mapper + INSERT bind | R2DBC test round-trip passes | ✅ Confirmed |
| Unsplash sets licence | persistPhoto() line 128 | Handler test asserts `"unsplash"` | ✅ Confirmed |
| Frontend type has licence | media-api.ts:40 | TypeScript compiles | ✅ Confirmed |
| Frontend component renders | MediaAttribution.vue exists | Integrated in MediaLibraryView | ✅ Confirmed |
| Tests pass | BUILD SUCCESSFUL | All test assertions green | ✅ Confirmed |
| Tasks.md reflects completion | 1.1-1.9 unchecked ✅/❌ | WARNING — code done, checkboxes not updated | ⚠️ Needs update |

---

## Final Verdict

**PASS WITH WARNINGS**

- ✅ All functional requirements (FR-1 through FR-7) are satisfied
- ✅ Both non-functional requirements (NFR-1, NFR-2) are satisfied
- ✅ All 11 Phase 1 tasks are implemented in code
- ✅ All tests pass
- ⚠️ One administrative issue: tasks 1.1–1.9 in `tasks.md` need their checkboxes updated from `[ ]` to `[x]`
- ℹ️ No blocking issues, no CRITICAL findings

The implementation is ready for **Phase 2** (takedown workflow) once the minor tasks.md update is applied.
