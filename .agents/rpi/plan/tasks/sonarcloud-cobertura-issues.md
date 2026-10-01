# Plan RPI: SonarCloud quality and coverage remediation

## Route

Delegated direct. Remediate the vulnerability, bugs, and CRITICAL issue first, then group code smells by surface. No SDD cycle.

## Tasks

- [x] RPI-001 — Diagnose the SonarCloud/Codecov coverage-path mismatch. The 410 warnings are all for `apps/web/app/src/components/ui/**`; Vitest LCOV includes these production UI files while Sonar and Codecov intentionally exclude them. User chose to preserve LCOV visibility and keep Sonar's existing analysis boundary. A temporary Vitest exclusion was reverted because it removed coverage data for the entire UI subtree. The difference is documented as an accepted limitation; a fresh PR analysis is still required to confirm its effect.
- [x] RPI-002 — Inventory open issues on `main` using the SonarCloud API; prioritize vulnerability, bugs, and CRITICAL code smell.
- [x] RPI-003 — Fix the false-positive `AaDtHlWrMxOa4ntidY_W` (`typescript:S2068`) on the admin password label.
- [x] RPI-004 — Apply local fixes or semantic adjudication to the seven initial bugs and local fix for the CRITICAL smell. Local suites/checks are recorded below; a new SonarCloud analysis is still required.
- [ ] RPI-005 — Review fixes against a new SonarCloud analysis and record each issue status. A PR-triggered analysis remains blocked until this branch is pushed and a PR is opened.
- [x] RPI-004a — Fixed three accessibility findings in `GovernanceView.vue`; admin suite 121/121 passed, including SFC compilation.
- [x] RPI-004c — Replaced the mouse-only backdrop control in `RevokeInvitationDialog.vue` with backdrop-only dismissal on the dialog surface; the explicit Cancel control remains the keyboard-accessible dismissal action. Regression tests, admin type-check and Biome passed.
- [x] RPI-006a — Fixed two S5906 code smells in `DirectInvitationsView.spec.ts`; admin suite 121/121, `just admin-check`, and Biome passed.
- [x] RPI-006e — Fixed three Kotlin publishing smells S6517/S6532/S6516. `just backend-lint` and later full `just backend-check` passed; focused `ThreadsPublishingPropertiesTest` and `AuthRateLimitWebFilterTest` passed. Remote Sonar status awaits new analysis.
- [x] RPI-006b — `shared/vue-ui` `UiInput` now supports an associated label and has tests; the input's prior no-label/no-id behavior and explicit IDs remain unchanged; `Table` remains compositional because consumers render `<th>`; package suite 6/6.
- [x] RPI-006c — Removed five unnecessary `async` modifiers from `media-api.ts`; Media API tests 43/43 and app type-check passed. Biome reports a pre-existing warning in unrelated `SchedulerTimelineBody.vue`.
- [x] RPI-006d — Replaced five nested ternaries in publishing calendar code; three suites/144 tests, app type-check, scoped Biome, and diff check passed.
- [x] RPI-006f — Parameterized consent version/source tests, resolving three S5976 smells; shared-web 69 tests, Biome, and diff check passed.
- [x] RPI-006g — Removed a redundant alias and nested ternary in `useLinkedInCallback.ts`; 16 tests, app type-check, and Biome passed.
- [x] RPI-006h — Fixed `AaDtHlTMMxOa4ntidY-2` in `WaitlistView.vue`; status result uses `<output>`. Admin suite 122, `just admin-check`, lint, and diff check passed.
- [x] RPI-006i — Reviewed `AaDzjqNcPBpttGiJzM4a` in `CreatePostModal.vue`; retained `role="status"` because it announces an accessible state, not the result of an action. No code change; semantic adjudication awaits the next Sonar analysis.
- [x] RPI-006j — Fixed `AaDEoxAZZ72LaJoMrgji` (`typescript:S8786`) by replacing the trailing-slash regex with a loop and adding a regression test; Vitest 7/7, Biome, and Astro check passed.
- [x] RPI-004b — `hero-animations.ts` now awaits the fade promise and cleanup; focused tests 9/9, Biome, and Astro check passed.
- [ ] RPI-006 — Continue grouped, evidence-backed cleanup of remaining code smells. Local S7503 fixes include `AaDt_ojBgJtAKIpaD7VO` (SSE callback), `AaDt_ojBgJtAKIpaD7VP/VQ/VR` (bulk API Promise forwards; `async` retained to preserve rejected-Promise behavior), `AaDt_ojBgJtAKIpaD7VS` (publication API promise forward), `AaDt_ohCgJtAKIpaD7VN` (hashtag request), `AaDt_oXMgJtAKIpaD7VM` (template CSV forwarder), and `AaDt_pBqgJtAKIpaD7Vo` (admin route guard). Dashboard `S7503` keys `AaDt_onYgJtAKIpaD7VT`, `AaDt_onvgJtAKIpaD7VU`, and `AaDt_ooHgJtAKIpaD7VV` were also simplified in the worktree. Bulk validation/job/template fetch retain async Promise-rejection semantics for missing workspace. Focused app publishing/useBulkImport/SSE/hashtag tests 149/149, admin router test 15/15, app/admin type-check, scoped Biome and diff check passed. The current main snapshot still reports old findings OPEN; only a new PR analysis can confirm closure. Other S7503 findings in unrelated modules are deliberately out of scope.

## Acceptance criteria

- Do not manipulate coverage to inflate indicators; reconcile actual reports and remote status.
- For each fixed issue, record key/rule, file, affected behavior, and verifying test.
- Do not suppress rules, vulnerabilities, or analysis, and do not weaken quality gates.
- Recheck duplicate or stale findings against current analysis before changing code.
- Distinguish local results, remote results, and pending evidence.

## Evidence

- SonarCloud project: `dallay_profiletailors.com`, branch `main`; project key matches the supplied URL.
- Initial public API snapshot: 124 OPEN issues (116 code smells, 7 bugs, 1 vulnerability), coverage 82.5%. An earlier 0% report was inconsistent with this snapshot.
- The latest public issue API still returns the old snapshot (123 OPEN: 115 code smells, 7 bugs, 1 vulnerability), including issues fixed locally. No branch analysis has confirmed closures.
- False-positive vulnerability `AaDtHlWrMxOa4ntidY_W`, `typescript:S2068`, `apps/web/admin/src/i18n/index.ts`: the English/Spanish password label key was renamed to `passwordLabel` across locale types and consumers, with regression coverage. Admin login tests 3/3 and suite 121/121, type-check, and scoped Biome passed.
- Initial seven BUG findings affected `GovernanceView.vue` (two unlabeled inputs and a mouse-only table row), `shared/vue-ui/Table.vue`, `UiInput.vue`, `RevokeInvitationDialog.vue`, and `hero-animations.ts`. UI changes were reviewed against consumers; no rule suppression was added.
- CRITICAL issue `AaDTN_2k1r_3rS6EUuL9`, `kotlin:S3776`, `AuthRateLimitWebFilter.kt`: extracted admission-window logic to reduce Cognitive Complexity. Focused test passed and full backend check passed.
- Quality-gate run `36742206482` imported 12 Kover reports and reported 410 unresolved frontend LCOV paths. Downloaded the frontend coverage artifact: every unresolved path was under `apps/web/app/src/components/ui/**`, which is excluded by Sonar analysis and Codecov. The matching Vitest exclusion was tested locally and later reverted after independent review because it also removes all UI source entries from LCOV; therefore 410 unresolved paths remain unremediated pending a path-level solution or approved coverage-boundary decision. App coverage run previously passed 1,867 tests: 85.54% statements, 78.03% branches, 82.48% functions, 87.47% lines. Remote reanalysis is pending.
- `just backend-check` initially failed once with `NoSuchFileException` for Gradle `in-progress-results-generic.bin`; isolated rerun passed (`BUILD SUCCESSFUL`, 8m56s), including backend tests, PostgreSQL integration tests, Detekt, Spotless, and Kover verification. Third-party JVM/ByteBuddy/Netty warnings were emitted.
- Frontend verification (fresh 2026-10-01): admin 14 files/123 tests and admin build passed; app 166 files/1,871 tests, lint/type-check/build passed (Biome reports the pre-existing unused `viewport` warning in unchanged `SchedulerTimelineBody.vue`; Vite emits dependency-originated Zod comment warnings); marketing 17 files/153 tests and lint/Astro check/build passed; `shared/web` 4 files/69 tests passed; `shared/vue-ui` 3 files/6 tests passed. Focused publishing/admin/component checks listed above also passed.
- Previous PR analysis `36742206482` was for PR 1257, not this worktree. It reported new coverage 80.4% and Sonar quality gate OK for that PR. It cannot prove current fixes or the new LCOV filtering behavior remotely.
- Legacy `plan/tasks/` migration: 23 original files were compared byte-for-byte with `.agents/rpi/plan/tasks/`; 23 matched, 0 missing/different. Additional Sonar remediation plan is the only new task file.

## Status

PR opened: [#1267](https://github.com/dallay/profiletailors.com/pull/1267). The branch includes the reconciled fixes and the 23 byte-identical task migrations. Local checks passed as recorded above; the app lint reports a pre-existing unused `viewport` warning in unchanged `SchedulerTimelineBody.vue`. SonarCloud still needs a new analysis; no finding is considered remotely closed until that analysis completes.

## Next step

Monitor CI and the PR-triggered SonarCloud analysis, then record each issue status in RPI-005. Review any actionable comments and address them before declaring the remediation complete.
