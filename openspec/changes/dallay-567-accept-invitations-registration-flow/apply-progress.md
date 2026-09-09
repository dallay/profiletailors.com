# Apply Progress: DALLAY-567 — Accept Invitations in Registration

## Delivery

- Strategy: `github-stacked-prs`
- Trunk: `main`
- Parent branch: `feat/dallay-567-invitation-contracts`
- Intended layer: atomic registration acceptance and shared transaction ownership
- Worktree branch: `feat/dallay-567-invitation-transaction`
- Layer position: PR 2, immediately above the contracts layer; local stack metadata is initialized through PR 2, with no remote state or PR created.

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

## Verification

- `./gradlew :server:smp:test --tests ...` focused identity/platformadmin suite: PASS, 79 tests completed in the first run and the final focused run completed successfully.
- `just backend-lint`: PASS.
- `just backend-test-fast`: PASS.
- `just backend-test-postgres`: timed out after 300 seconds and was terminated with signal 15; the generated `R2dbcInvitationRepositoryTest` report shows 16 tests passed, including concurrent acceptance.
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

## Remaining

- [ ] 1.5 API/BDD and frontend acceptance evidence.
- [ ] 3.1–3.3 Acceptance telemetry, broad contract verification, and final scope review.

## Risks

- The branch is the approved PR 2 layer with the contracts branch as its parent; local stack metadata is initialized, while remote state remains unchanged.
- The new gateway contract now requires all implementations to support both `prepare` and `complete`; full repository verification beyond the fast backend lane is still pending.
- The original design placed invitation failure mapping in identity advice, which introduced an identity ↔ platformadmin module cycle. The platformadmin global advice now owns that mapping; this is an intentional architecture-preserving deviation.
- The first full backend-check attempt failed because the repository PostgreSQL environment was unavailable; rerunning after `just infra-up` passed, and the focused PostgreSQL evidence also passes.
- The registration transaction integration test now injects a failure after invitation completion to verify invitation consumption, membership, identity, consent, and verification-token writes roll back together.
