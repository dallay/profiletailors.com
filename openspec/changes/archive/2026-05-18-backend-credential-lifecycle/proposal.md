# Proposal: Backend API-Key Credential Replacement Lifecycle

## Intent

Introduce one narrow, high-value credential lifecycle capability for `server/smp`: replace an
existing active API key with a successor key in a single explicit cutover operation.

The problem today is that runtime credential validation and revocation already exist, but there is
no safe executable path to move from one valid API key to its replacement without falling back to
manual row mutation or broad lifecycle platform work. This change adds the smallest real lifecycle
behavior that proves the platform can evolve credentials over time while preserving deterministic
authentication rules on the existing workspace-access proving slice.

## Scope

### In Scope

- One application-level API-key replacement or rotation capability for an existing active API-key
  credential.
- Minimal persisted lifecycle metadata required to connect predecessor and successor API-key
  credentials if that linkage is needed to make replacement semantics explicit and testable.
- Explicit runtime rule that, once replacement completes, the successor API key is accepted and the
  predecessor API key is no longer accepted.
- End-to-end proof on the existing `GET /api/authorization/workspace-access/current` proving slice
  in both current workspace-backed integration harnesses.
- Narrow governance-ready runtime proof only as needed to explain the replacement outcome on the
  existing slice or command path.
- Rollback-safe implementation planning that preserves current authentication behavior if the change
  is reverted.

### Out of Scope

- Service-account credential rotation or replacement.
- Dual-active rollover windows, grace periods, overlap semantics, or delayed predecessor revocation.
- Inventory, list, detail, search, or operator-facing management APIs for credentials.
- Broad credential issuance/admin CRUD expansion beyond what is minimally necessary to execute one
  replacement path.
- Last-used tracking, expiry policy, scheduled lifecycle actions, labels/tags, ownership-management
  breadth, or audit persistence/reporting.
- Generalized credential-family management across all credential types.
- Identity taxonomy changes, new proving endpoints, or broad authorization redesign.

## Approach

Implement a single narrow credential-lifecycle mutation centered on API keys only.

At a high level:

- Add one application command/use case that targets an existing active API-key credential and
  produces a replacement credential plus deterministic predecessor invalidation.
- Extend persisted API-key state with only the minimal lineage metadata required to connect old and
  new credentials, preferably in a way that stays local to API-key storage instead of introducing a
  generic credential-family abstraction.
- Preserve the current authentication pipeline shape: lookup + verifier comparison + authoritative
  credential-state evaluation.
- Update authoritative API-key state semantics so a replaced predecessor is denied immediately after
  the replacement operation completes, while the successor becomes the only valid key for subsequent
  requests.
- Prove the lifecycle end to end on the existing `GET /api/authorization/workspace-access/current`
  slice with before/after integration scenarios in both H2 and PostgreSQL suites.

This change intentionally chooses explicit cutover over overlap semantics. That keeps the slice
small, avoids dual-validity complexity, and proves real lifecycle behavior without dragging the
platform into generalized credential management.

## Affected Areas

| Area                                                                                                                     | Impact   | Description                                                                                                                                 |
|--------------------------------------------------------------------------------------------------------------------------|----------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `openspec/specs/credentials/spec.md`                                                                                     | Modified | Narrow credential scope changes from “rotation deferred” to “one API-key replacement capability supported.”                                 |
| `openspec/specs/platform/spec.md`                                                                                        | Modified | Proving-slice boundaries updated so one credential replacement path is allowed without broadening into inventory or admin platform breadth. |
| `openspec/specs/governance/spec.md`                                                                                      | Modified | Runtime proof expectations may be extended only as needed to make predecessor/successor denial vs acceptance explainable.                   |
| `server/smp/src/main/resources/db/changelog/credentials/002-create-api-key-credentials.yaml`                             | Modified | Add minimal persisted replacement lineage metadata for API-key predecessor/successor relationship if required.                              |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ApiKeyCredentialStateLookup.kt`               | Modified | Credential-state contract may need to recognize replacement semantics beyond active/revoked/inactive.                                       |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcApiKeyCredentialStateLookup.kt`       | Modified | R2DBC lookup/query logic updated to enforce authoritative post-replacement validity rules.                                                  |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/`                                                         | Modified | Add one narrow application command/use case and supporting domain/application types for API-key replacement.                                |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`         | Modified | Add H2 end-to-end proof for old-key allow before replacement and successor-only allow after replacement.                                    |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt` | Modified | Add PostgreSQL end-to-end proof for the same replacement lifecycle semantics.                                                               |

## Scope Boundaries

- This change is about **replacement cutover**, not full lifecycle management.
- The replacement operation MUST stay local to API-key credentials and MUST NOT force a
  cross-credential abstraction pass.
- Any persisted linkage MUST exist only to explain and enforce predecessor/successor semantics for
  this API-key slice.
- If implementation pressure reveals a need for overlap windows, inventory APIs, or generalized
  family semantics, that work is deferred and must not be pulled into this change.
- Existing USER and SERVICE_ACCOUNT proving behavior remains unchanged.

## Non-Goals

- Building a reusable credential-family platform.
- Solving operator UX for credential administration.
- Introducing reveal/reissue flows for plaintext secrets beyond what is strictly necessary for
  replacement output.
- Adding service-account token issuance or service-account JWT lifecycle semantics.
- Designing future-safe abstractions for all credential forms before proving this API-key slice.

## Risks

| Risk                                                                               | Likelihood | Mitigation                                                                                                            |
|------------------------------------------------------------------------------------|------------|-----------------------------------------------------------------------------------------------------------------------|
| Replacement scope expands into issuance/admin CRUD breadth                         | Medium     | Keep proposal, specs, and tasks anchored to one mutation path and one proving endpoint only.                          |
| Minimal lineage metadata grows into generic credential-family modeling             | Medium     | Store only API-key-local predecessor/successor linkage required for deterministic cutover; defer shared abstractions. |
| Cutover semantics are underspecified and create ambiguous runtime behavior         | Medium     | Define one explicit rule: successor valid after completion, predecessor denied after completion, no overlap window.   |
| Existing authentication proof harness misses lifecycle regression paths            | Low        | Add before/after integration proof in both H2 and PostgreSQL suites on the existing workspace-access slice.           |
| Rollback becomes risky if schema/state changes are entangled with broader behavior | Low        | Keep persistence change additive and localized so the old runtime model can be restored cleanly.                      |

## Rollback Plan

If the replacement lifecycle introduces regressions, revert the application command/use case, remove
the runtime replacement-state enforcement, and roll back the additive API-key lifecycle metadata
introduced for this change. Then restore prior API-key behavior where authentication depends only on
lookup key, verifier match, and active/revoked/inactive state.

Operationally, rollback is safe because this change does not broaden protected endpoints or alter
USER/SERVICE_ACCOUNT authentication paths. If replacement records were created during validation,
they can be handled by restoring predecessor rows to their prior active state only if needed for
local recovery, but the primary rollback strategy is code-and-schema reversion to the previous
runtime contract.

## Dependencies

- Existing `backend-api-key-support` proving slice and runtime API-key validation path in
  `server/smp`.
- Existing H2 and PostgreSQL workspace-access integration suites.
- Existing credential-state enforcement and audit-ready runtime proof seams.

## Success Criteria

- [ ] One narrow API-key replacement capability is defined for `server/smp` without broadening into
  service-account rotation or general credential management.
- [ ] Persisted state includes only the minimal metadata needed to connect predecessor and successor
  API keys if required for explicit cutover semantics.
- [ ] Runtime behavior explicitly guarantees that, after replacement completes, the successor key
  authenticates and the predecessor key is denied.
- [ ] End-to-end proof is defined on `GET /api/authorization/workspace-access/current` for both H2
  and PostgreSQL suites.
- [ ] Proposal explicitly defers service-account rotation, dual-active rollover windows,
  inventory/list/detail APIs, issuance breadth, last-used tracking, and generalized
  credential-family management.
