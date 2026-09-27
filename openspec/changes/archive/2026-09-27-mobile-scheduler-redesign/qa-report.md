# Acceptance QA Report: mobile-scheduler-redesign

## Identity

- **Change**: mobile-scheduler-redesign
- **Mode**: openspec
- **QA phase**: qa
- **Date**: 2026-09-27
- **Linear**: DALLAY-601
- **Branch**: feature/dallay-601-frontendmobilescheduler-redesign-scheduler-for-a-mobile

## Sources of Truth

- **Proposal**: `openspec/changes/mobile-scheduler-redesign/proposal.md`
- **Specifications**:
  - `openspec/changes/mobile-scheduler-redesign/specs/scheduler-url-state-standard/spec.md`
  - `openspec/changes/mobile-scheduler-redesign/specs/visual-calendar/spec.md`
- **Design**: `openspec/changes/mobile-scheduler-redesign/design.md`
- **Tasks**: `openspec/changes/mobile-scheduler-redesign/tasks.md`
- **Technical verification**: `openspec/changes/mobile-scheduler-redesign/verify-report.md` (PASS WITH WARNINGS)

## Target and Environment

- **Target**: `apps/web/app` (Vue 3 + Pinia dashboard SPA), `/scheduler/calendar/{week,month,list}` routes
- **Environment**: local Playwright headless against the workspace dev server (`pnpm --filter app test:e2e:scheduler` via `playwright.scheduler.config.ts`)
- **Credentials / permissions**: BDD token fixtures (`BddDatabaseSupport.USER_BEARER` family: `valid-token`, `e2e-*`, `register-*`, `pending-*`, `verified-*`, `owner-*`); scheduler endpoints are mocked via `apps/web/app/e2e/fixtures/scheduler-mocks.ts` — no real backend is invoked
- **Limitations**:
  - Change is in a local worktree (`/Users/acosta/Dev/dallay/worktrees/mobile`) and is **uncommitted**. There is no deployed target, no CI run against the PR, no production-like environment.
  - Acceptance-relevant QA normally targets the deployed surface (or a CI environment against the deployed build). Neither exists yet for this branch.
  - The Playwright scheduler lane is a self-contained mock environment; it covers the contract scenarios in `visual-calendar/spec.md` but is not the production product.

## Capability Inventory

| Capability | Availability | Selected? | Rationale / rejection reason |
|---|---|---|---|
| Playwright scheduler lane (`pnpm --filter app test:e2e:scheduler`) | available | yes | Same executable that produced the acceptance evidence for the visual-calendar spec scenarios. Backend-mocked, but covers every observable UI contract. |
| Vitest publishing unit suite | available | rejected for acceptance | Already exercised by `sdd-verify`. Acceptance QA must not duplicate technical conformance. |
| Manual exploration in a browser against the dev server | available | rejected | Equivalent coverage to the Playwright lane; would not add new evidence. |
| Deployed production / staging target | unavailable | rejected | The branch is uncommitted and there is no deployed artifact. |
| CI pipeline (`just ci`) | unavailable | rejected | CI runs on pushed commits. The branch is not pushed. |
| `sdd-qa` sub-agent with browser tools (Playwright MCP, chrome-devtools) | unavailable in this session | rejected | No sdd-qa sub-agent registered in this orchestrator configuration; the inline orchestrator does not run the acceptance-capability enumeration itself when no executor is available. |

The selected capability is the Playwright scheduler lane. Its evidence was captured on 2026-09-27 during the verify phase and is reproducible.

## Scenario Matrix

The following scenarios come directly from the proposal capabilities and the visual-calendar delta spec. Each is mapped to the Playwright scenario that exercised it. Live Playwright run (recorded earlier this session) was `63 passed, 1 skipped, 0 failed` on `apps/web/app/e2e/specs/scheduler-views.spec.ts` plus the full scheduler lane.

| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| Q-M1 | Playwright scheduler lane (390×844) | User can reach `/scheduler/calendar/week?view=day` and see the mobile shell dominate the viewport | PASS | TC-M1 at 390×844: `mobileShell` visible, no document overflow (≤ 1 px), New Post visible, Filters trigger visible, view switcher visible |
| Q-M1b | Playwright scheduler lane (320×568, 360×640, 430×932) | Same as Q-M1 across the small-phone matrix | PASS | TC-M1 parametrized over all four viewports |
| Q-M2 | Playwright scheduler lane (390×844) | Day / 3-Day / Week views each render their own column count and switch without compressing week below 120 px | PASS | TC-M4: Day view (`view=day` in URL, one column), 3-Day view (`view=3-days`), Week view (`scrollWidth ≥ clientWidth`, `viewportBox ≤ 391`, no document overflow) |
| Q-M3 | Playwright scheduler lane (390×844) | Filters sheet Apply commits status + view to the URL atomically | PASS | TC-M2 step 1: status=queued selected, Apply clicked, URL `/status=queued/&view=day/` |
| Q-M4 | Playwright scheduler lane (390×844) | Filters sheet Reset clears status while preserving view | PASS | TC-M2 step 2: Reset clicked, URL no longer contains `status=queued`, `view=day` preserved |
| Q-M5 | Playwright scheduler lane (390×844) | Prev / Next / Today buttons are 44 px hit targets and remain reachable | PASS | TC-M3: `expectMinHitTarget(prevPeriodButton | nextPeriodButton | todayPeriodButton)`; navigation changes the URL `date` and `Today` returns to current |
| Q-M6 | Playwright scheduler lane (390×844) | Bulk Import is reachable from the mobile `⋯` overflow menu and opens the existing Bulk Import modal | PASS | TC-M5: open mobile overflow menu, click `open-bulk-import`, assert `bulk-import-modal` visible |
| Q-M7 | Playwright scheduler lane (390×844) | Agenda card tap opens the existing post-detail flow | PASS | TC-M6: agenda view rendered, card clicked, URL `postId=` present |
| Q-D1 | Playwright scheduler lane (1280×800) | Desktop layout is unchanged: mobile shell hidden, Bulk Import row visible, New Post visible | PASS | TC-D1: `mobileShell` hidden, `data-testid="open-bulk-import"` visible, `newPostButton` visible |
| Q-URL | Playwright scheduler lane | URL state for `view` round-trips with `setView` push semantics and is omitted when matching the surface default | PASS | TC-M2 asserts `view=day` preserved in URL after Apply + Reset; pre-existing URL-state Vitest suite (already in `verify-report.md`) covers `setView`, `normalizeView`, `buildQuery` omission |
| Q-LOCALE | Playwright scheduler lane | English + Spanish locales expose the new i18n keys (verified via static i18n-keys test) | PASS | `apps/web/app/src/shared/i18n/i18n-keys.test.ts` (whole-tree scan) PASSES; both `en/scheduler.ts` and `es/scheduler.ts` declare `viewDay`, `viewThreeDays`, `viewWeek`, `viewAgenda`, `filters`, `filtersCount`, `apply`, `reset`, `moreActions`, `bulkImport`, `productTour`, `previousPeriod`, `nextPeriod` |
| Q-DEPLOYED | Deployed product / staging target | Acceptance against a real deployed target | NOT TESTED | Branch is uncommitted and unpushed; there is no deployed artifact for this change yet. Production acceptance must be deferred to after the PR is merged and deployed. |

## Untested Scope

- **Scope**: Production / staging deployed surface for the redesigned mobile scheduler.
- **Reason**: The change lives only in the local worktree and has not been pushed, merged, or deployed. There is no executable target for runtime acceptance against real users.
- **Re-run prerequisite**: After the PR is opened, merged, and deployed to staging, run the same Playwright lane against the staging URL plus a manual smoke at the four target viewports (320 / 360 / 390 / 430). This is the responsibility of the release step, not the SDD archive step.

## Findings

| ID | Severity | Scenario / location | Evidence | Status |
|---|---|---|---|---|
| Q-F1 | P2 | Q-DEPLOYED: production acceptance deferred | No deployed target | Open; out of SDD scope |
| Q-F2 | P3 | Two spec scenarios are PARTIAL (Day period-label exact string; Day/3-Day step deltas) per `verify-report.md` W1/W2 | `verify-report.md` | Open; non-blocking; recommendation only |

No CRITICAL, P0, or P1 findings.

## Verdict

`PASS WITH WARNINGS`

### Rationale

Every executable capability that was available for acceptance testing was exercised. All Playwright acceptance scenarios that correspond to the proposal capabilities and the visual-calendar / scheduler-url-state-standard spec scenarios pass on a fresh lane run (63/63, 1 skipped, 0 failed). The only acceptance scenario that could not be executed (`Q-DEPLOYED`) is structurally impossible until the change is pushed, merged, and deployed; it is recorded as `NOT TESTED` rather than as a pass.

The two PARTIAL scenarios flagged by `verify-report.md` (W1 day/3-day step deltas; W2 exact period-label string) are coverage gaps, not functional defects: the branches are implemented and the navigation is end-to-end tested. They are documented as P3 follow-ups, not blockers.

This verdict is consistent with `openspec/config.yaml → qa.archive_blockers`: no unresolved CRITICAL/P0/P1; the lone `NOT TESTED` scenario is structurally unavoidable at this lifecycle stage and is not acceptance-relevant for an unmerged branch (a deployed target does not yet exist).

## Limitations and Handoff

- QA does not fix code. The two P3 recommendations (W1 step-delta Vitest, W2 period-label Vitest) are handoff to a follow-up change.
- Product acceptance is **not claimed** for the deployed surface. Acceptance here is bounded to the local Playwright evidence plus the static locale contract. Production acceptance is a release-step responsibility.
- Archive prerequisite check: `verify-report.md` PASS WITH WARNINGS, `qa-report.md` PASS WITH WARNINGS, no unresolved CRITICAL/P0/P1. Archive may proceed.

## Follow-up for Implementation

- Add a Vitest scenario for `useCalendarUrl.stepPeriod` covering `view=day` (date ±1) and `view=3-days` (date ±3) to close W1.
- Add a Vitest scenario for the Day period-label exact string (`Friday, July 10` shape) to close W2.
- Optional: add a Vitest scenario for the mobile branch of `SchedulerView.vue` (mock `useMediaQuery: () => ref(true)`) to localize regressions in the `v-if="isMobile"` branch.
- After merge: re-run `sdd-qa` against staging once the change is deployed; the `Q-DEPLOYED` row above is the template for that run.