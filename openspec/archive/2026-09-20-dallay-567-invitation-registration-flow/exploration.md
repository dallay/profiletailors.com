## Exploration: Missing invitation acceptance QA journeys

### Current State
The implementation routes new-user registration through `RegisterUserHandler`, which performs a read-only invitation preflight and then completes invitation activation inside the registration transaction. Authenticated existing users use `/api/invitations/accept`, `AcceptInvitationHandler`, and the same `InvitationActivationCoordinator`. The coordinator validates the opaque token, lifecycle, expiry, normalized email, principal type, and principal email before resolving the invitation-owned workspace and reconciling membership.

The current QA report has acceptance evidence for valid, invalid, replayed, workspace, atomicity, and concurrency paths. Expired, revoked, email-mismatch, and successful matching-existing-identity journeys remain `NOT TESTED`; deployed/manual acceptance is `BLOCKED` because no target or credentials were supplied. Unit and classifier tests cover several of these outcomes but cannot be promoted to product acceptance under `openspec/config.yaml`.

The repository already has the necessary local seams. `DirectInvitationBddSteps` seeds an active invitation and raw test token. `PlatformAdminBddSteps` resets platform-admin state and seeds `principal-1` with `jwt-user@example.com`; `valid-token` authenticates that principal. `BddDatabaseSupport.seedLocalAccountWithPassword()` and the existing count helpers cover local-registration mutation checks. `local-auth.feature` covers active and invalid invite-only registration but not expired, revoked, mismatch, or existing-identity success. `invitee-private-beta.spec.ts` covers generic invalid and replay browser errors through route mocks but not the specific 410/403 outcomes. `playwright.config.ts` starts only the Vite server and relies on HAR replay plus route overrides; the app package has no dedicated invitation script, so the config must be invoked directly.

### Affected Areas
- `server/smp/src/test/resources/features/local-auth.feature` — add registration scenarios for expired, revoked, and mismatched invitations.
- `server/smp/src/test/resources/features/platformadmin/invitations-direct.feature` — existing admin lifecycle scenarios provide the revoke behavior that can be reused or paired with an acceptance scenario.
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/DirectInvitationBddSteps.kt` — extend test-only fixtures/assertions for lifecycle states and authenticated acceptance; preserve raw-token state only inside the fixture.
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/PlatformAdminBddSteps.kt` — existing `@platform-admin` hook seeds the authenticated existing identity needed by acceptance scenarios.
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/LocalAuthCapabilitiesBddSteps.kt` — add registration outcome and no-mutation assertions for the missing cases.
- `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/BddDatabaseSupport.kt` — reuse seeded matching identity and existing count helpers for no-duplicate assertions.
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/application/InvitationActivationCoordinatorTest.kt` — existing unit coverage confirms expiry and email mismatch classification; it is supporting evidence, not acceptance evidence.
- `apps/web/app/e2e/specs/invitee-private-beta.spec.ts` — add browser-facing 410 expired, 410 revoked, and 403 mismatch scenarios with explicit response codes.
- `apps/web/app/src/modules/invitation/infrastructure/invitation-api.ts` — status-only 410 maps to expired, so revoked browser fixtures must include `code: INVITATION_REVOKED`.
- `openspec/changes/dallay-567-accept-invitations-registration-flow/qa-report.md` — rerun and update the currently blocked scenario rows after evidence exists.

### Approaches
1. **Targeted backend Cucumber acceptance matrix** — add focused PostgreSQL-backed scenarios for expired, revoked, and mismatched invite-only registration, plus an authenticated matching-identity acceptance scenario in a feature tagged `@platform-admin` so the existing principal fixture is available.
   - Pros: exercises real HTTP, Problem Details codes, database lifecycle, authentication, workspace membership, and no-mutation/no-duplicate guarantees; uses existing fixtures and command hub.
   - Cons: requires small test-only fixture and assertion additions; deployed behavior remains unverified.
   - Effort: Medium

2. **Frontend Playwright error matrix** — extend the existing invitee journey with mocked `410 INVITATION_EXPIRED`, `410 INVITATION_REVOKED`, and `403 INVITATION_EMAIL_MISMATCH` responses, then run the app Playwright config directly.
   - Pros: verifies user-visible copy and browser classification; fast and backend-independent; catches the distinction between code-driven and status-only classification.
   - Cons: route mocks do not prove backend persistence or authentication; the existing raw-token registration URL exposure remains a separate P2 warning.
   - Effort: Low

3. **Deployed/manual exploratory session** — execute the same matrix against a permissioned deployed app/API using real invitation tokens and test identities.
   - Pros: validates deployment wiring, cookies, email/token handoff, and real browser behavior.
   - Cons: cannot run without a target, credentials, invitation issuance/revocation access, and a controlled identity/email setup; it is not a replacement for deterministic local coverage.
   - Effort: High

### Recommendation
Use Approach 1 as the primary acceptance evidence, followed by Approach 2 for browser copy and classification. The existing BDD setup can cover all four missing local journeys without production changes: add an expired timestamp or terminal `EXPIRED` state, revoke an active invitation through the existing direct-revoke step, submit a different registration email to exercise `prepare`, and use the `@platform-admin`-seeded `principal-1`/`jwt-user@example.com` identity for authenticated acceptance. Assert status, stable code, invitation state, and no unexpected identity/password-credential/membership duplication. Run `just backend-bdd-fast` and `just backend-bdd-postgres`, then the focused app Playwright command through `node ../../../scripts/run-playwright.mjs -c e2e/playwright.config.ts` from `apps/web/app`.

Keep Approach 3 explicitly separate. It is blocked until an owner supplies a deployed target and permissioned test setup. Do not claim deployed acceptance from local BDD, unit tests, HAR replay, or route mocks. Keep the raw-token URL exposure as the existing P2 security/product decision rather than silently expanding this QA follow-up.

### Risks
- `DirectInvitationBddSteps` currently creates only active invitations; missing lifecycle fixture steps must be test-only and must not alter production invitation behavior.
- A 410 response without an explicit `INVITATION_REVOKED` payload code is classified by the frontend as expired, so revoked browser evidence must include the canonical code.
- Matching-existing-identity acceptance is authenticated acceptance, not fresh registration; the scenario must use the `@platform-admin`-seeded `principal-1` identity and prove no new identity or local credential was inserted.
- Cucumber database reset hooks and shared scenario state must be preserved so the invitation token, principal, and workspace are not accidentally reused across scenarios.
- The deployed/manual journey remains unavailable without an external target, credentials, and controlled invitation lifecycle access.

### Ready for Proposal
Yes — the investigation supports a focused acceptance-evidence follow-up with no production design change. The orchestrator should approve targeted BDD and Playwright test additions, and separately request deployed target details only if manual acceptance is required.
