# Verification Report: Composer Media Picker Shell

## Summary

- **Change:** `composer-media-picker-shell`
- **Mode:** OpenSpec, strict TDD verification (`openspec/config.yaml` sets `apply.tdd: true` and a
  runner exists)
- **Verdict:** **PASS WITH WARNINGS**
- **Reason:** All 12 tasks are complete; 65 focused runtime tests, SPA lint, type-check, and
  production build pass. Every spec scenario has passing behavioral coverage, with limited locale
  assertions and no quantitative coverage report remaining as warnings.

## Completeness

| Measure                                       |                          Result |
|-----------------------------------------------|--------------------------------:|
| Tasks completed                               |                           12/12 |
| Tasks incomplete                              |                               0 |
| Proposal success criteria checked in artifact | 0/4 (artifact bookkeeping only) |

All implementation tasks are marked complete. Task completion does not override failed verification
evidence.

## Execution Evidence

| Check                             | Command                                                                                                                                                              | Result                                                                                                               |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| Focused component tests           | `pnpm --dir apps/web/app exec vitest run src/components/composer/ComposerMediaPickerShell.test.ts src/components/CreatePostModal.test.ts src/i18n/i18n-keys.test.ts` | **PASS** — 3 files, 65 tests, including real-dialog keyboard focus containment and Escape dismissal                  |
| App lint                          | `pnpm --dir apps/web/app lint`                                                                                                                                       | **PASS** — 614 files                                                                                                 |
| App production build + type-check | `pnpm --dir apps/web/app build`                                                                                                                                      | **PASS** — `vue-tsc --build` and Vite production build completed; only dependency annotation and chunk-size warnings |
| Repository frontend lint alias    | `just frontend-lint`                                                                                                                                                 | **PASS**, but currently targets `apps/web/marketing`, not the changed SPA                                            |
| Repository frontend build alias   | `just frontend-build`                                                                                                                                                | **PASS**, but currently targets `apps/web/marketing`, not the changed SPA                                            |
| Coverage                          | Not configured/run for this focused verification                                                                                                                     | No percentage evidence                                                                                               |

The expected stderr from the existing `surfaces update errors in edit mode` test did not fail the
Vitest run.

## Spec Compliance Matrix

| Requirement / scenario                                        | Implementation evidence                                                              | Runtime test evidence                                                                                 | Status                 |
|---------------------------------------------------------------|--------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------|------------------------|
| Open picker without losing composer state                     | Parent-owned open state and trigger in `CreatePostModal.vue`                         | Passing integration tests for opening and preserving text                                             | COMPLIANT              |
| Dismiss picker with keyboard and return focus                 | Escape handling plus parent `nextTick` focus restoration                             | Passing real-dialog Escape test and passing parent focus-return integration test                      | COMPLIANT              |
| Render localized controls (EN/ES)                             | `composer.mediaPicker.*` keys in locale objects; shell uses `t()`                    | Key parity and accessible-name component assertions pass; actual locale switching is not exercised    | COMPLIANT WITH WARNING |
| Keyboard reachability and modal focus containment             | Existing Reka/shadcn dialog primitives and native controls                           | Passing real-dialog test traverses controls, verifies focus remains inside, and dismisses with Escape | COMPLIANT              |
| Loading and empty states                                      | Explicit state branches                                                              | Passing shell tests                                                                                   | COMPLIANT              |
| Error and disabled states; disabled emits suppressed          | Explicit branches and disabled guards                                                | Passing shell tests                                                                                   | COMPLIANT              |
| Typed search/filter/close contract; no shell API/store access | Strict types module and typed emits; shell imports only UI/i18n/types                | Passing emission tests plus source inspection                                                         | COMPLIANT              |
| Preserve shell-only scope                                     | Asset cards are non-interactive; no selection/upload/delete/import behavior in shell | Passing rendering tests plus source inspection                                                        | COMPLIANT              |
| Ready asset region                                            | Labeled grid and parent-provided asset cards                                         | Passing shell tests                                                                                   | COMPLIANT              |
| Ready state without attachment behavior                       | Non-actionable cards                                                                 | Passing parent content-preservation and shell rendering tests                                         | COMPLIANT              |
| Testable accessible open/close behavior                       | Observable events and DOM hooks                                                      | Close events, real-dialog focus containment, Escape dismissal, and parent focus return pass           | COMPLIANT              |
| State and interaction emissions                               | Deterministic props and typed events                                                 | Passing shell tests                                                                                   | COMPLIANT              |

## Correctness

| Area                 | Result | Evidence                                                                                    |
|----------------------|--------|---------------------------------------------------------------------------------------------|
| Typed contract       | PASS   | Strict unions/interfaces; `MediaAssetSummary` reused; no `any`                              |
| Deterministic states | PASS   | Loading, empty, error, ready, and disabled tests pass                                       |
| Parent-owned state   | PASS   | Parent receives search/filter and owns open state                                           |
| Scope boundaries     | PASS   | Shell has no store/API/fetch/mutation logic                                                 |
| Compile correctness  | PASS   | SPA type-check and production build complete successfully                                   |
| Behavioral coverage  | PASS   | All 12 spec scenarios map to passing runtime tests, including real-dialog focus containment |

## Design Coherence

| Decision                                       | Result | Notes                                                                                                  |
|------------------------------------------------|--------|--------------------------------------------------------------------------------------------------------|
| Reuse existing Dialog wrapper                  | PASS   | `ComposerMediaPickerShell.vue` uses `@/components/ui/dialog`                                           |
| Controlled props + emits                       | PASS   | Parent owns state; shell remains presentational                                                        |
| Reuse `MediaAssetSummary`                      | PASS   | Types module imports the existing API type                                                             |
| No API/store coupling in shell                 | PASS   | Source inspection confirms boundary                                                                    |
| Focus behavior delegated to established dialog | PASS   | A dedicated test imports the real dialog primitives and proves focus containment plus Escape dismissal |

## Issues

### CRITICAL

None.

### WARNING

1. Locale parity passes, but the shell tests assert translation keys through a mock rather than
   switching between real English and Spanish locale output.
2. `just frontend-lint` and `just frontend-build` validate only the marketing app in the current
   command hub; direct SPA commands were required for meaningful verification.
3. No coverage report was produced, so quantitative coverage cannot be claimed.

### SUGGESTION

1. Add a real-locale render assertion (not only key parity and mocked `t`) if translated copy
   regressions need direct component-level protection.

## Finding Verdicts

| Finding                                              | Judge A           | Judge B                               | Severity | Status    |
|------------------------------------------------------|-------------------|---------------------------------------|----------|-----------|
| SPA build/type-check succeeds after test corrections | ✅ runtime build   | ✅ type-check included by build script | —        | Resolved  |
| Real-dialog focus containment has runtime coverage   | ✅ runtime test    | ✅ spec-to-test mapping                | —        | Resolved  |
| Real EN/ES rendering is not directly exercised       | ✅ test inspection | ✅ parity evidence limits impact       | WARNING  | Confirmed |
| Command-hub aliases do not validate changed SPA      | ✅ command output  | ✅ direct SPA commands                 | WARNING  | Confirmed |

## Final Verdict

**PASS WITH WARNINGS** — The change is eligible for archive from the verification gate: no CRITICAL
issues remain, all focused runtime tests pass, and the changed SPA passes lint plus production
build/type-check. Warnings are limited to test-depth/command-hub caveats and do not block archive.
