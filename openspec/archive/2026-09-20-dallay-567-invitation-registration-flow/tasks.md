# Tasks: DALLAY-567 — Acceptance Evidence Follow-up

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 320–390 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | One focused test/fixture PR |
| Delivery strategy | single-pr (resolved by maintainer) |
| Chain strategy | single-pr |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: single-pr
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|---|---|---|---|
| 1 | Add and run missing QA-06..QA-10 evidence | PR 1 | Base `main`; test fixtures, Cucumber/Playwright coverage, focused commands, and `qa-report.md` only. |

## Phase 1: TDD RED — Acceptance Cases

- [x] 1.1 Add `server/smp/src/test/resources/features/local-auth.feature` scenarios for QA-06 expired, QA-07 revoked, and QA-08 normalized-email mismatch with exact status/code, no-mutation, and redaction assertions.
- [x] 1.2 Add `server/smp/src/test/resources/features/platform-admin.feature` QA-10 matching-existing-identity acceptance using `principal-1` / `jwt-user@example.com`; require one existing identity, credential, workspace membership, and invitation outcome.
- [x] 1.3 Add isolated `apps/web/app/e2e/specs/invitee-private-beta.spec.ts` scenarios for QA-06/07/08 using `410`/`403` mocked Problem Details; assert canonical classification, safe copy, no redirect/session, and no token or full email in the DOM.

## Phase 2: Cucumber Fixtures and Steps

- [x] 2.1 Extend `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/DirectInvitationBddSteps.kt` with unique expired/revoked lifecycle fixtures, token-in-state-only handling, and invitation status plus `410` Problem Details assertions.
- [x] 2.2 Extend `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/LocalAuthCapabilitiesBddSteps.kt` with normalized mismatch submission, explicit failure-code checks, and no-registration-mutation assertions for QA-06..QA-08.
- [x] 2.3 Extend `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/BddDatabaseSupport.kt` with focused counts for identity, local credential, membership, workspace, and invitation rows; use reset hooks so each scenario starts clean.
- [x] 2.4 In `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/DirectInvitationBddSteps.kt`, wire QA-10 through existing `Bearer valid-token` support and assert invitation-derived workspace and exactly one membership without duplicate identity or credential rows.

## Phase 3: Focused Verification and QA Handoff

- [x] 3.1 Run `just backend-bdd-fast`, then `just infra-up && just backend-bdd-postgres && just infra-down`; retain exact QA-06..QA-10 status/code and persistence results.
- [x] 3.2 Run `cd apps/web/app && node ../../../scripts/run-playwright.mjs -c e2e/playwright.config.ts e2e/specs/invitee-private-beta.spec.ts`, `pnpm --filter app lint`, and `pnpm --filter app type-check`.
- [x] 3.3 Re-run the existing QA-09 workspace-override scenario in `server/smp/src/test/resources/features/local-auth.feature` and retain its 400/code/no-dispatch evidence with QA-06..QA-08 and QA-10.
- [x] 3.4 Update `openspec/changes/dallay-567-accept-invitations-registration-flow/qa-report.md` rows QA-06..QA-10 with observed local evidence and runner provenance; keep QA-16 as a P2 warning and QA-21 as `BLOCKED`.
- [x] 3.5 Run `git diff --check` and verify the follow-up additions contain no production-code, contract, classifier, copy, or runtime-configuration changes; pre-existing DALLAY-567 implementation changes remain preserved in the worktree.
