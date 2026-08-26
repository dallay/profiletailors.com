# Tasks: Release Please Title Pattern Compatibility

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 1-5 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | ask-on-risk |
| Chain strategy | single-pr |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: single-pr
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Update and verify the Release Please title parser pattern | PR 1 | Base: `main`; includes the focused validation and configuration-only change |

## Phase 1: TDD Validation / RED

- [x] 1.1 Run a focused Node assertion against `release-please-config.json` for `${scope}`, `${component}`, `${version}`, valid JSON, and the unchanged grouped title; record the expected failure against the current pattern before editing.

## Phase 2: Configuration / GREEN

- [x] 2.1 Update only the root `pull-request-title-pattern` in `release-please-config.json` to `chore${scope}: release${component} ${version}`.
- [x] 2.2 Confirm `group-pull-request-title-pattern`, package components, versions, tags, and all unrelated configuration remain unchanged; do not modify `.release-please-manifest.json` or `.github/workflows/release-please.yml`.

## Phase 3: Focused Verification / REFACTOR

- [x] 3.1 Re-run the focused assertion and JSON parse against `release-please-config.json`; verify all three placeholders and `chore(release): prepare releases` pass.
- [x] 3.2 Verify the placeholder contract against the pinned Release Please 17.6.0 matcher behavior, including optional scope, component, and semantic-version tokens (confirmed by post-merge run [32711346079] per verify-report.md).
- [x] 3.3 Run `git diff --check` and inspect the focused diff to confirm only the intended configuration line changed and no package versions or release tags changed.

## Phase 4: Cleanup

- [x] 4.1 Remove any temporary local validation artifact if one was created; leave the repository with only the intended `release-please-config.json` change.
