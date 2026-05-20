## Exploration: backend-credential-lifecycle

### Current State

`server/smp` now has three executable credential paths on the single proving slice `GET /api/authorization/workspace-access/current`:

- `USER` via JWT bearer auth.
- `SERVICE_ACCOUNT` via JWT bearer auth plus authoritative backend credential-state lookup.
- `API_KEY` via lookup + secret verifier comparison plus authoritative backend credential-state lookup.

What is already real in code:

- Credential taxonomy is canonical and executable: `CredentialType.JWT`, `SERVICE_ACCOUNT`, `API_KEY`.
- Service-account runtime credential state exists in `service_account_credentials` with `status`, `revoked_at`, and `created_at`.
- API-key runtime credential state exists in `api_key_credentials` with `lookup_key`, `secret_verifier`, `status`, `revoked_at`, and `created_at`.
- Runtime enforcement exists only for **active vs revoked/inactive** decisions at authentication time.
- `issuedCredentialReference` already flows into authenticated principal context for USER, SERVICE_ACCOUNT, and API_KEY requests.
- Governance already proves revoked/inactive denials through `AuthorizationReasonCode.REVOKED_CREDENTIAL` on both H2 and PostgreSQL integration suites.

What remains seam-only or explicitly deferred:

- No issuance or mutation use cases exist for credentials after persistence.
- No rotation or rollover command path exists for service accounts or API keys.
- No credential family / lineage model exists to relate an old credential to a replacement credential.
- No dual-active overlap window exists for safe cutover.
- No lifecycle timestamps beyond `created_at` and `revoked_at` exist.
- No operator-facing or API-facing inventory/list/detail surfaces exist.
- No expiry, scheduled deactivation, last-used tracking, or ownership-management workflows exist.

So the repo is past “credential concept only” and now has **runtime credential validation with revocation**, but lifecycle control stops at “seed a row with a status” rather than “change a credential safely over time.”

### Affected Areas

- `openspec/specs/credentials/spec.md` — current main spec explicitly defers rotation workflows, credential families, dual-active rollover, and broad lifecycle automation.
- `openspec/specs/platform/spec.md` — already requires authoritative credential validity and explicitly defers rotation and broad credential management.
- `openspec/specs/identity/spec.md` — principal materialization is in place, so lifecycle work should not reopen principal taxonomy.
- `openspec/specs/governance/spec.md` — revoked/inactive runtime proof already exists; lifecycle work should extend governance only if the new transition needs audit-ready proof.
- `server/smp/src/main/resources/db/changelog/credentials/001-create-service-account-credentials.yaml` — current service-account credential table has no family or successor semantics.
- `server/smp/src/main/resources/db/changelog/credentials/002-create-api-key-credentials.yaml` — current API-key credential table has runtime-only state, not lifecycle linkage.
- `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ServiceAccountCredentialStateLookup.kt` — supports active/revoked checks only.
- `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ApiKeyCredentialStateLookup.kt` — supports active/inactive/revoked checks only.
- `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcServiceAccountCredentialStateLookup.kt` — current lookup contract assumes one credential row is presented and checked, not rotated.
- `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcApiKeyCredentialStateLookup.kt` — same runtime-only assumption for API keys.
- `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt` — existing H2 proof harness can validate lifecycle transition outcomes once a rotation path exists.
- `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt` — same for PostgreSQL proof.

### Approaches

1. **Full credential lifecycle platform** — add issuance, inventory, rotation, lineage, expiry, last-used, bulk revocation, and management APIs together.
   - Pros: Comprehensive lifecycle story.
   - Cons: Massive scope explosion; crosses credentials, governance, identity operations, and admin surfaces all at once.
   - Effort: High

2. **API-key-only rotation workflow** — add one rotation path just for API keys, including overlap semantics.
   - Pros: API keys are the strongest immediate fit for rotation because they are long-lived managed secrets.
   - Cons: Risks introducing API-key-special lifecycle semantics before the shared credential model is clarified; may overfit one credential form too early.
   - Effort: Medium

3. **Service-account + API-key generic lifecycle framework first** — introduce credential families / lifecycle abstractions before any executable rotation behavior.
   - Pros: Cleaner theoretical model.
   - Cons: Too seam-heavy again; weak immediate value; repeats the old trap of architecture ahead of proof.
   - Effort: Medium/High

4. **Minimal API-key replacement lifecycle with explicit old/new cutover** — add one narrow executable lifecycle capability for API keys only: rotate an existing active API key by creating a successor credential and revoking the predecessor, without introducing broad inventory or family management.
   - Pros: Smallest real lifecycle value; fits managed-secret reality; builds on current API-key table and verification path; avoids reopening service-account JWT semantics prematurely.
   - Cons: Still needs a deliberate mutation path and a small amount of lineage/state design; must avoid drifting into issuance/admin CRUD.
   - Effort: Medium

### Recommendation

Recommend **Approach 4: minimal API-key replacement lifecycle with explicit old/new cutover**.

This is the smallest coherent next change because it adds **real credential lifecycle value** without collapsing into full management sprawl:

- API keys are the only current credential form in `server/smp` that already behaves like a platform-managed long-lived secret.
- Service-account auth currently rides JWT bearer semantics; broad rotation there risks dragging the repo into issuer/token-minting design too early.
- Revocation already exists, so the natural next step is controlled **replacement** of an API key, not a universal lifecycle engine.
- The change can stay focused on one credential mutation capability and one proving story: after rotation, the replacement credential authenticates and the predecessor no longer does.

#### What belongs in this change

- A narrow application command/use case for **rotating an existing active API key**.
- Minimal persisted lifecycle metadata needed to connect predecessor and successor credentials, only if required to prove the rotation semantics cleanly.
- Explicit runtime rule that the replacement credential becomes the valid path and the predecessor is no longer accepted after rotation completes.
- H2 and PostgreSQL verification that proves:
  - old API key authenticates before rotation,
  - rotated/new API key authenticates after rotation,
  - predecessor key is denied after rotation,
  - existing authorization behavior on the proving slice remains unchanged.
- Audit-ready runtime proof only as needed to make the lifecycle transition explainable on the existing proving slice or command path.

#### What should remain deferred

- Rotation support for service-account bearer credentials.
- Dual-active rollover windows or overlap-period semantics.
- Generic credential families spanning every credential type.
- Inventory/list/detail APIs for credentials.
- Issuance/admin CRUD breadth, operator consoles, secret reveal flows, labels, tags, expiration policy frameworks, and last-used tracking.
- Bulk lifecycle automation, scheduled expiry, and organization-wide credential management.

### Risks

- Even a “small” rotation change can sprawl if issuance and inventory are pulled in with it.
- If lineage modeling is over-generalized now, the change could become a generic credential-platform redesign.
- If the new lifecycle semantics are defined only for API keys, later service-account lifecycle work may need a careful abstraction pass.
- If rotation semantics require overlap/rollover guarantees, scope could widen quickly beyond the smallest coherent change.
- The current repo has runtime authentication proof but no management surface yet, so proposal/design must be explicit about who or what invokes rotation in this phase.

### Ready for Proposal

Yes — propose `backend-credential-lifecycle` as a **narrow API-key rotation/replacement change** that adds one real lifecycle capability on top of the existing runtime credential model. Keep service-account rotation, dual-active rollover, inventory, issuance breadth, and generalized credential-family management explicitly deferred.
