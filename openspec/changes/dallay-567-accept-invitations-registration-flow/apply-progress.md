# Apply Progress: DALLAY-567 — Accept Invitations in Registration

## Delivery

- Strategy: `github-stacked-prs`
- Trunk: `main`
- Intended layer: invitation registration contracts/preflight with the minimum completion seam required by the layer tests
- Worktree branch: `feat/direct-invitation-admin-commands`
- Branch note: the current branch has no live upstream and is not named as the suggested DALLAY-567 layer branch; no branch or stack mutation was performed.

## Completed

- [x] 1.1 Added and passed focused pre-mutation invitation validation tests.
- [x] 1.2 Added and passed gateway/context/workspace fallback tests.
- [x] 1.6 Added and passed generic Problem Details redaction/code coverage for invitation email mismatch.
- [x] 2.1 Added immutable `InvitationRegistrationContext`, source/target contracts, result contract, and split gateway methods.
- [x] 2.3 Split coordinator preflight from locked completion, preserved the legacy authenticated acceptance wrapper, and removed invitation-ID workspace fallback.
- [x] 2.4 Added the adapter contract implementation and platformadmin-owned invitation failure mapping; kept identity advice free of platformadmin dependencies.

## Verification

- `./gradlew :server:smp:test --tests ...` focused identity/platformadmin suite: PASS, 79 tests completed in the first run and the final focused run completed successfully.
- `just backend-lint`: PASS.
- `just backend-test-fast`: PASS.
- `just backend-test-postgres`: timed out after 300 seconds and was terminated with signal 15; the generated `R2dbcInvitationRepositoryTest` report shows 16 tests passed, including concurrent acceptance.
- `./gradlew :server:smp:test --tests ...ModularStructureTest`: PASS after moving invitation Problem Details mapping out of identity advice.
- `./gradlew :server:smp:spotlessKotlinCheck`: PASS after formatter application.
- `git diff --check`: PASS.
- `just infra-up`: PASS; started the repository PostgreSQL, Mailpit, and WireMock services.
- `just backend-check`: PASS after infrastructure startup; `:server:smp:check` completed in 2m 7s, including tests, PostgreSQL integration tests, Detekt, Spotless, security-version verification, and Kover verification.
- `just infra-down`: PASS; stopped the temporary verification services after the check.

## Remaining

- [ ] 1.3 Existing-identity authenticated matching/no-duplicate behavior.
- [ ] 1.4 PostgreSQL rollback and concurrent registration evidence.
- [ ] 1.5 API/BDD and frontend acceptance evidence.
- [ ] 2.2 Complete registration orchestration for all existing-identity and policy cases.
- [ ] 3.1–3.3 Acceptance telemetry, broad contract verification, and final scope review.

## Risks

- The worktree branch does not match the suggested DALLAY-567 layer branch and its upstream is gone. Preserve this state for the orchestrator to resolve before creating or stacking a PR.
- The new gateway contract now requires all implementations to support both `prepare` and `complete`; full repository verification beyond the fast backend lane is still pending.
- The original design placed invitation failure mapping in identity advice, which introduced an identity ↔ platformadmin module cycle. The platformadmin global advice now owns that mapping; this is an intentional architecture-preserving deviation.
- The first full backend-check attempt failed because the repository PostgreSQL environment was unavailable; rerunning after `just infra-up` passed.
