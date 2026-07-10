# Verification Report: Publication Edit Asset Preservation

## Summary

| Field | Result |
|---|---|
| Change | `2026-07-02-publication-edit-assets-fix` |
| Mode | OpenSpec, standard verification (`tdd: false`) |
| Verdict | **PASS** |
| Reason | Every spec scenario has passing runtime coverage; backend regressions, app-specific full Vitest, and type-check are green. |

## Completeness

| Area | Complete | Evidence |
|---|---:|---|
| Proposal/spec/design implementation | Yes | Source inspection plus focused runtime tests |
| Implementation tasks 1.1–5.2 | 19/19 | `tasks.md` |
| Verification tasks 5.3/6.1 | Yes | This report and executed commands |
| Archive readiness | Yes | PASS with no critical findings |

## Execution Evidence

| Command | Result |
|---|---|
| `./gradlew :server:smp:test --tests '*PublishingControllersTest' --tests '*PublishingApiTest' --tests '*PublishingHandlersTest' -PexcludeTags=modularity,postgres` | PASS; prior verification, `BUILD SUCCESSFUL` |
| `./gradlew :server:smp:test --tests '*PublishingWorkspaceIsolationIntegrationTest' --tests '*PublishingProblemDetailsHandlerTest' -PexcludeTags=modularity,postgres` | PASS; prior verification, `BUILD SUCCESSFUL` |
| `pnpm --filter app exec vitest run src/components/CreatePostModal.test.ts src/stores/publishing.test.ts` | PASS; prior verification, 2 files and 104 tests |
| `pnpm --filter app run type-check` | PASS; freshly rerun, `vue-tsc --build` exited 0 |
| `pnpm --filter app run test:run` | PASS; freshly rerun, 70 files and 732 tests |
| `PostDetailModal.test.ts` within full app suite | PASS; 32/32 tests |
| Coverage | Not required (`coverage_threshold: 0`); not run |

The prior blocker was a deterministic-test defect: reschedule tests used a date that had become historical. The test-only correction scopes `vi.useFakeTimers()` and `vi.setSystemTime(...)` to the `reschedule` describe block and restores real timers in `afterEach`, including failure paths. No production behavior was modified by that correction.

The originally documented `pnpm --filter app vitest run ...` is not a valid pnpm lifecycle invocation because the package has no `vitest` script. Verification used `pnpm --filter app exec vitest run ...` for focused tests and `pnpm --filter app run test:run` for the full app suite.

## Spec Compliance Matrix

| Requirement / Scenario | Implementation evidence | Passing runtime evidence | Status |
|---|---|---|---|
| PATCH absent preserves assets | Nullable request/command; handler uses `command.assetIds ?: current.assetIds` | Focused backend suite | COMPLIANT |
| PATCH null preserves assets | Nullable DTO maps null through edit command | Focused backend suite | COMPLIANT |
| PATCH `[]` clears assets | Handler replaces with supplied empty list | Focused backend suite | COMPLIANT |
| PATCH list replaces exactly and in order | Handler copies supplied list directly | Focused backend suite | COMPLIANT |
| CREATE omitted/default remains empty | Create boundary maps `request.assetIds ?: emptyList()`; command remains non-null/default-empty | Focused backend suite | COMPLIANT |
| CREATE provided list persists | Create mapping passes supplied list | Focused backend suite | COMPLIANT |
| Workspace isolation (#224) | Workspace-scoped lookup/update remains intact | Workspace isolation integration test | COMPLIANT |
| Not-found 404 mapping (#225) | Missing scoped publication throws `PublicationNotFoundException` | Problem-details and focused backend tests | COMPLIANT |
| Edit hydrates and previews existing assets | `initEditMode` awaits `mediaStore.loadAsset` and selects resolved IDs | Component tests and full app suite | COMPLIANT |
| Missing asset is graceful | 404 is skipped while valid resolved assets remain selected | Component tests and full app suite | COMPLIANT |
| Untouched save omits `assetIds` | Conditional payload spread guarded by `assetsTouched` | Component/store tests | COMPLIANT |
| Explicit clear sends `[]` | User removal marks touched; empty selection serializes | Component/store tests | COMPLIANT |
| Replacement sends selected IDs | User selection marks touched and serializes selected IDs | Component/store tests | COMPLIANT |
| Strong typing/no new `any` | Typed `PublicationUpdate` and `loadAsset`; no `any` in changed production code | Fresh Vue type-check | COMPLIANT |
| App regression suite passes | App-specific Vitest executed directly | 70/70 files, 732/732 tests | COMPLIANT |

## Correctness

| Check | Result |
|---|---|
| PATCH tri-state boundary-to-handler flow | PASS |
| CREATE compatibility | PASS |
| UI hydration and payload behavior | PASS |
| Workspace isolation | PASS |
| 404 mapping | PASS |
| Strong typing/type-check | PASS |
| App-wide unit regression | PASS |

## Design Coherence

| Decision | Result | Notes |
|---|---|---|
| PATCH-only nullable semantics | Aligned | Domain asset list remains non-null |
| Separate create/edit mapping | Aligned | Create coerces null; edit preserves null |
| Edit-only touched tracking | Aligned | Programmatic initialization is suppressed |
| Hydrate before dangling load | Aligned | Composer awaits `initEditMode` |
| Graceful missing assets | Aligned | Missing IDs do not crash or clear valid selections |
| Strong typed PATCH input | Aligned | No casts or `any` introduced |
| Deterministic calendar tests | Safe | Fake clock is scoped and real timers restore in `afterEach` |

## Issues

### CRITICAL

None.

### WARNING

None.

### SUGGESTION

1. Correct future SDD command examples to use `pnpm --filter app exec vitest run ...` for focused execution.

## Verdict Table

| Finding | Judge A | Judge B | Severity | Status |
|---|---|---|---|---|
| Prior calendar-dependent failures resolved with scoped fake time | ✅ test diff is test-only | ✅ full suite passes and `afterEach` restores real timers | INFO | Confirmed |
| All change-specific scenarios pass | ✅ source inspection | ✅ focused backend/frontend runtime tests | INFO | Confirmed |
| Full app regression suite passes | ✅ 70 files | ✅ 732 tests | INFO | Confirmed |

## Final Verdict

**PASS** — all required publication asset semantics, composer behavior, workspace isolation, 404 mapping, and typing constraints are supported by passing runtime evidence. The previous verification blocker was corrected safely in test code, and the complete app-specific Vitest suite now passes.
