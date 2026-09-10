# Apply Progress: DALLAY-567 — Accept Invitations in Registration

## Delivery

- Strategy: `single-pr`
- Base: `main`
- Intended layer: Complete DALLAY-567 acceptance-evidence follow-up
- Worktree branch: `feat/dallay-567-invitation-evidence`
- Layer position: Single PR; no chained-PR metadata applies. The worktree was reconciled with `main` and the intentional dirty changes were restored without conflicts.

## Completed

- [x] 1.1 Added and passed focused pre-mutation invitation validation tests.
- [x] 1.2 Added and passed gateway/context/workspace fallback tests.
- [x] 1.3 Verified matching authenticated identity, exact normalized-email validation, and no duplicate identity/credential creation.
- [x] 1.4 Added and passed PostgreSQL/Testcontainers registration rollback, concurrent one-winner, new-workspace, and existing-workspace membership tests.
- [x] 1.6 Added and passed generic Problem Details redaction/code coverage for invitation email mismatch.
- [x] 2.1 Added immutable `InvitationRegistrationContext`, source/target contracts, result contract, and split gateway methods.
- [x] 2.2 Revalidated identity and credential availability inside the registration transaction while retaining pre-transaction hashing and post-commit session/event behavior.
- [x] 2.3 Split coordinator preflight from locked completion, moved authenticated acceptance transaction ownership to `AcceptInvitationHandler`, and removed invitation-ID workspace fallback.
- [x] 2.4 Added the adapter contract implementation and platformadmin-owned invitation failure mapping; kept identity advice free of platformadmin dependencies.
- [x] 1.5 Added API and BDD coverage for invite-only success, safe failures, workspace override rejection, session response, and token redaction.
- [x] 1.7 Added Vitest and Playwright coverage for accepted, invalid, replay, and safe-error invitation flows.
- [x] 3.1 Added the accepted domain event, bounded invitation telemetry, and post-commit audit listener without raw token or email data.
- [x] 3.2 Made the RED integration, BDD, and E2E tests pass and completed the configured focused and broad verification lanes.
- [x] 3.3 Reviewed the final changed paths and contracts; the implementation remains scoped to DALLAY-567.

## Acceptance-Evidence Follow-up Completed

- [x] 1.1 Added local-auth Cucumber scenarios for expired, revoked, and normalized-email mismatch outcomes with exact status/code, no-mutation, lifecycle, and redaction assertions.
- [x] 1.2 Added platform-admin Cucumber coverage for authenticated matching-existing-identity acceptance with one identity, credential, workspace, and membership.
- [x] 1.3 Added isolated invitation Playwright coverage for expired, revoked, and email-mismatch Problem Details using canonical copy, no post-action session refresh, stable route, and DOM redaction assertions.
- [x] 2.1 Added unique expired and revoked invitation fixtures while keeping the raw token in scenario state only.
- [x] 2.2 Reused invite-only registration request and response assertions for normalized mismatch and failure-code coverage.
- [x] 2.3 Added BDD database counts for identities, credentials, invitations, workspaces, and memberships; existing reset hooks isolate scenarios.
- [x] 2.4 Seeded the existing principal identity, credential, and workspace membership and verified the authenticated acceptance path with `Bearer valid-token`.
- [x] 3.1 Ran fast and PostgreSQL Cucumber suites; both completed successfully, including the new local-auth and platform-admin scenarios.
- [x] 3.2 Ran the invitation Playwright suite with 36/36 passing across Chromium, Firefox, and Mobile Chrome; app lint and type-check passed.
- [x] 3.3 The existing QA-09 workspace-override scenario passed in both Cucumber suites with its 400/code/no-dispatch assertions.
- [x] 3.4 Refreshed `qa-report.md` with current local evidence; QA-16 remains a P2 warning and QA-21 remains BLOCKED.
- [x] 3.5 `git diff --check` passed; the follow-up additions contain no new production-code, API-contract, classifier, copy, or runtime-configuration changes.

## Verification

- `./gradlew :server:smp:test --tests ...` focused identity/platformadmin suite: PASS, 79 tests completed in the first run and the final focused run completed successfully.
- `just backend-lint`: PASS.
- `just backend-test-fast`: PASS.
- An initial `just backend-test-postgres` attempt timed out after 300 seconds and was terminated with signal 15; the final `just backend-test-postgres` run passed, including the invitation repository concurrency coverage.
- `./gradlew :server:smp:test --tests com.profiletailors.smp.identity.application.LocalAuthHandlersTest`: PASS after adding in-transaction identity revalidation coverage.
- `./gradlew :server:smp:test --tests com.profiletailors.smp.integration.LocalAuthEndpointIntegrationTest`: PASS; existing rollback and invitation-registration integration coverage remains green.
- `./gradlew :server:smp:test --tests com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationRepositoryTest`: PASS; concurrent acceptance coverage remains green after transaction ownership moved to the caller.
- `./gradlew :server:smp:test --tests com.profiletailors.smp.platformadmin.application.InvitationActivationCoordinatorTest --tests com.profiletailors.smp.platformadmin.application.AcceptInvitationHandlerTest --tests com.profiletailors.smp.platformadmin.infrastructure.http.InvitationAcceptanceControllerTest`: PASS.
- `./gradlew :server:smp:test --tests com.profiletailors.smp.identity.integration.LocalAuthHandlersTransactionPostgresIntegrationTest`: PASS; failure-after-completion rollback, concurrent one-winner, new-workspace, and existing-workspace membership scenarios pass.
- `just infra-up && ./gradlew :server:smp:test --tests com.profiletailors.smp.identity.integration.LocalAuthHandlersTransactionPostgresIntegrationTest --tests com.profiletailors.smp.platformadmin.infrastructure.persistence.R2dbcInvitationRepositoryTest`: PASS; focused PostgreSQL evidence completed in 43s, followed by `just infra-down`.
- `./gradlew :server:smp:spotlessKotlinCheck`: PASS after formatter application.
- `./gradlew :server:smp:test --tests ...ModularStructureTest`: PASS after moving invitation Problem Details mapping out of identity advice.
- `git diff --check`: PASS.
- `just infra-up`: PASS; started the repository PostgreSQL, Mailpit, and WireMock services.
- `just backend-check`: PASS after infrastructure startup and the new registration transaction tests; `:server:smp:check` completed in 2m 11s, including tests, PostgreSQL integration tests, Detekt, Spotless, security-version verification, and Kover verification.
- `just infra-down`: PASS; stopped the temporary verification services after the check.
- `just backend-bdd-fast`: PASS; fast Cucumber suite completed successfully.
- `just infra-up && just backend-bdd-postgres && just infra-down`: PASS; PostgreSQL Cucumber suite completed successfully and temporary services were stopped.
- `just backend-test-postgres`: PASS; PostgreSQL integration suite completed successfully.
- `pnpm --filter app test:run`: PASS; 147 test files and 1,728 tests passed.
- `pnpm --filter app lint`, `pnpm --filter app type-check`, and `pnpm --filter app build`: PASS.
- Relevant invitation Playwright E2E: PASS; 9 tests passed.
- `git diff --check`: PASS after the final feature-file formatting correction.
- `:server:smp:compileTestKotlin`: PASS after adding the BDD database dependency to direct-invitation steps.
- `just backend-bdd-fast`: PASS; local-auth XML reports 24 tests with 0 failures and platform-admin XML reports 20 tests with 0 failures, including QA-06 through QA-10.
- `just infra-up && just backend-bdd-postgres && just infra-down`: PASS; PostgreSQL infrastructure was started for this worktree, the BDD suite passed, and cleanup completed in a separate explicit `just infra-down` command after the shell cleanup variable conflicted with zsh.
- `cd apps/web/app && node ../../../scripts/run-playwright.mjs -c e2e/playwright.config.ts e2e/specs/invitee-private-beta.spec.ts`: PASS; 36 tests passed across Chromium, Firefox, and Mobile Chrome.
- `pnpm --filter app lint`: PASS; Biome checked 830 files without errors.
- `pnpm --filter app type-check`: PASS; `vue-tsc --build` completed without errors.
- `git diff --check`: PASS after the acceptance-evidence additions.

## Remaining

- All acceptance-evidence apply tasks are complete. Technical conformance and product acceptance remain with `sdd-verify` and `sdd-qa`.

## Risks

- The branch is the approved single-PR worktree based on `main`; no remote state or PR was created.
- The new gateway contract now requires all implementations to support both `prepare` and `complete`; the configured backend, PostgreSQL, BDD, frontend, and E2E verification lanes now pass.
- The original design placed invitation failure mapping in identity advice, which introduced an identity ↔ platformadmin module cycle. The platformadmin global advice now owns that mapping; this is an intentional architecture-preserving deviation.
- An intermediate `just backend-check` attempt failed because Spotless had not yet applied touched-file formatting and repository PostgreSQL services were unavailable to unrelated publishing tests; after `spotlessApply`, the focused PostgreSQL suite and final `just backend-check` passed.
- The registration transaction integration test now injects a failure after invitation completion to verify invitation consumption, membership, identity, consent, and verification-token writes roll back together.
- QA-16 raw-token registration URL/history/referrer exposure remains an explicit P2 warning and was intentionally not addressed in this test-only follow-up.
- Deployed/manual acceptance remains blocked because no target, credentials, or controlled operator fixture was supplied.
