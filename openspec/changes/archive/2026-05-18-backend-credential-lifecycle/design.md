# Design: Backend API-Key Credential Replacement Lifecycle

## Technical Approach

Add one narrow command-side credential replacement path for API keys in `server/smp` while keeping
the existing proving slice, authentication adapters, and authorization flow intact.

The change does **not** introduce a generic credential-family model. Instead, it adds only the
minimum lineage metadata needed to express one explicit predecessor/successor cutover for
`api_key_credentials`, then reuses the current runtime authentication pipeline so that the old key
is denied and the new key is accepted on `GET /api/authorization/workspace-access/current`.

The design follows the proposal boundaries:

- one API-key replacement operation only
- no new protected endpoint for proof
- no service-account rotation
- no overlap window or dual-active semantics
- no inventory, lifecycle dashboards, or general credential redesign

## Architecture Decisions

### Decision: Model replacement locally on `api_key_credentials`

**Choice**: Extend `api_key_credentials` with additive self-referential lineage fields so one
credential row can point to the credential it replaced or the credential that replaced it.

**Alternatives considered**:

- Introduce a generic `credential_family` or `credential_lineage` table now.
- Reuse only `status` and `revoked_at` with no persisted link between rows.
- Add lifecycle linkage to a new cross-credential `credentials` super-table.

**Rationale**: This change needs explicit replacement semantics, but only for API keys. A generic
lineage platform would be architecture-first and too broad for this slice. Using status alone would
enforce denial, but would not make predecessor/successor intent explicit or easy to verify in tests
and future operations.

### Decision: Prefer additive lineage columns over a new mapping table

**Choice**: Add nullable local columns on `api_key_credentials` for replacement linkage and
timestamp, with the successor relationship treated as one-to-one for this change.

**Alternatives considered**:

- New `api_key_replacements` table.
- Only `successor_credential_id`.
- Only `replaced_credential_id` on the new row.

**Rationale**: A separate table would be extra machinery for a single cutover workflow. The smallest
practical model is to keep lineage next to the credential row. The implementation can use either one
directional link or both directional links, but the design recommends storing the canonical
predecessor reference on the successor row and optionally mirroring the successor reference on the
predecessor row for easier diagnostics. That keeps semantics explicit without becoming family
management.

### Decision: Replacement is a single atomic cutover, not a rotation window

**Choice**: Model replacement as one command that creates a successor credential and marks the
predecessor non-active within the same transactional operation.

**Alternatives considered**:

- Dual-active overlap window.
- Delayed revocation with grace period.
- “Create new key now, revoke old key later” two-step workflow.

**Rationale**: The proposal explicitly wants successor-valid / predecessor-denied semantics
immediately after completion. A single atomic cutover keeps runtime rules deterministic and testable
on the existing workspace-access proving slice.

### Decision: Keep runtime authentication contract shape unchanged

**Choice**: Preserve the current API-key authentication flow:

1. parse presented API key
2. look up persisted credential by `lookup_key`
3. verify secret against `secret_verifier`
4. evaluate authoritative credential state
5. materialize authenticated principal

**Alternatives considered**:

- Introduce a new lifecycle-aware authentication pipeline.
- Resolve “credential family” first, then determine active descendant.
- Add API-key replacement logic into authorization rather than authentication.

**Rationale**: The existing architecture already places credential validity in the authentication
phase. Keeping the same shape minimizes risk and limits the change to credential-state semantics,
not platform flow.

### Decision: Treat replaced predecessors as credential-state denial, not authorization denial

**Choice**: Once a key has been replaced, the predecessor must fail in the same category as
revoked/inactive credentials: unauthenticated for the protected slice, with runtime audit-ready
proof using existing governance seams.

**Alternatives considered**:

- Return 403 as an authorization outcome.
- Add a brand-new audit taxonomy just for replacement.
- Allow predecessor requests through and fail later.

**Rationale**: Replacement changes credential validity, not permissions. The protected use case must
not execute for a replaced key. Reusing the current revoked/inactive denial semantics keeps the
scope narrow and the behavior consistent.

## Data Flow

### Replacement command flow

```text
Caller / test seam
   │
   ▼
ReplaceApiKeyCredentialCommand
   │
   ▼
ReplaceApiKeyCredentialHandler
   │
   ├─ load predecessor credential by id/reference
   ├─ require predecessor status == ACTIVE
   ├─ require predecessor belongs to API_KEY principal
   ├─ generate successor lookup key + verifier + returned plaintext key
   ├─ insert successor credential row
   ├─ mark predecessor as replaced/non-active
   ├─ persist predecessor/successor linkage
   └─ return replacement result containing successor plaintext key
```

### Post-cutover authentication flow

```text
Request with old API key
   └─ parse lookup key + secret
      └─ load predecessor row
         ├─ status != ACTIVE or replacement semantics deny predecessor
         └─ 401 before principal materialization

Request with new API key
   └─ parse lookup key + secret
      └─ load successor row
         ├─ status == ACTIVE
         ├─ verifier matches
         └─ authenticated principal enters existing workspace authorization flow
```

### Existing proving-slice proof flow

```text
Before replacement
  old key -> 200 on /api/authorization/workspace-access/current

After replacement
  old key -> 401 before protected query executes
  new key -> 200 on /api/authorization/workspace-access/current
```

### Sequence diagram: single replacement cutover

```text
Caller            Handler/Use Case         DB/api_key_credentials        Runtime Auth
  |                      |                            |                        |
  | replace(old id)      |                            |                        |
  |--------------------->|                            |                        |
  |                      | load predecessor           |                        |
  |                      |--------------------------->|                        |
  |                      | predecessor ACTIVE         |                        |
  |                      |<---------------------------|                        |
  |                      | insert successor row       |                        |
  |                      |--------------------------->|                        |
  |                      | mark predecessor replaced  |                        |
  |                      |--------------------------->|                        |
  |                      | commit                     |                        |
  |<---------------------| successor plaintext key    |                        |
  |                      |                            |                        |
  | old key request      |                            | lookup predecessor     |
  |--------------------------------------------------------------->|           |
  |                      |                            | predecessor denied     |
  |<---------------------------------------------------------------| 401       |
  |                      |                            |                        |
  | new key request      |                            | lookup successor       |
  |--------------------------------------------------------------->|           |
  |                      |                            | successor ACTIVE       |
  |<---------------------------------------------------------------| 200       |
```

## File Changes

| File                                                                                                                     | Action           | Description                                                                                                                                   |
|--------------------------------------------------------------------------------------------------------------------------|------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `openspec/changes/backend-credential-lifecycle/design.md`                                                                | Create           | Technical design artifact for this change.                                                                                                    |
| `server/smp/src/main/resources/db/changelog/credentials/002-create-api-key-credentials.yaml`                             | Modify           | Add minimal replacement lineage columns to API-key credential storage.                                                                        |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/application/ApiKeyCredentialStateLookup.kt`               | Modify           | Extend failure/state semantics so replaced predecessor denial is representable alongside current active/inactive/revoked behavior.            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/infrastructure/R2dbcApiKeyCredentialStateLookup.kt`       | Modify           | Keep lookup-by-key behavior but enforce post-replacement predecessor denial and successor acceptance.                                         |
| `server/smp/src/main/kotlin/com/profiletailors/smp/credentials/`                                                         | Create/Modify    | Add one narrow replacement command/handler plus a small persistence seam for replacement.                                                     |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/infrastructure/security/IdentitySecurityConfiguration.kt`    | Modify minimally | Reuse existing revoked/inactive audit path or extend it minimally so replacement denial is observable on the proving slice.                   |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointIntegrationTest.kt`         | Modify           | Add H2 before/after proof: predecessor allowed before replacement, predecessor denied after replacement, successor allowed after replacement. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/integration/WorkspaceAccessSummaryEndpointPostgresIntegrationTest.kt` | Modify           | Add PostgreSQL equivalent proof for the same cutover semantics.                                                                               |

## Interfaces / Contracts

### Minimal schema additions

Recommended narrow shape:

```sql
api_key_credentials (
  id varchar(64) primary key,
  principal_id varchar(64) not null references principals(id),
  lookup_key varchar(255) not null unique,
  key_prefix varchar(64) not null,
  secret_verifier varchar(255) not null,
  status varchar(32) not null,
  revoked_at timestamp with time zone null,
  created_at timestamp with time zone not null default CURRENT_TIMESTAMP,

  replaced_by_credential_id varchar(64) null references api_key_credentials(id),
  replaced_credential_id varchar(64) null references api_key_credentials(id),
  replaced_at timestamp with time zone null
)
```

### Schema notes

- `replaced_credential_id`: canonical pointer from the successor row to the predecessor it replaced.
- `replaced_by_credential_id`: optional convenience pointer from predecessor to successor for direct
  diagnostics and test assertions.
- `replaced_at`: explicit lifecycle timestamp for the cutover event.

If implementation wants the absolute smallest schema, it MAY persist only:

- `replaced_credential_id` on successor rows, and
- reuse predecessor `status` + `revoked_at` + `replaced_at` to mark the predecessor invalid.

That reduced variant is still compliant with this design because it preserves explicit replacement
semantics without a family table.

### Replacement operation contract

```kotlin
data class ReplaceApiKeyCredentialCommand(
    val predecessorCredentialReference: String,
)

data class ReplaceApiKeyCredentialResult(
    val predecessorCredentialReference: String,
    val successorCredentialReference: String,
    val successorPlaintextApiKey: String,
)
```

Contract rules:

- the command targets exactly one existing active API-key credential
- the handler creates exactly one successor credential
- the predecessor becomes invalid before the command completes successfully
- no overlap window is produced
- no list/inventory behavior is implied

### Credential-state contract evolution

Current contract:

```kotlin
interface ApiKeyCredentialStateLookup {
    suspend fun requireActive(presentedApiKey: String): ActiveApiKeyCredential
}
```

This remains the primary shape. The narrow change is in the failure semantics, not the public
authentication entry point.

Recommended failure expansion:

```kotlin
enum class ApiKeyCredentialFailureReason {
    MISSING,
    INVALID,
    INACTIVE,
    REVOKED,
    REPLACED,
}
```

The runtime MAY map `REPLACED` into the same audit reason code currently used for revoked/inactive
credential denial in order to avoid broad governance redesign.

### Persistence seam for replacement

```kotlin
interface ApiKeyCredentialReplacementGateway {
    suspend fun replaceActiveCredential(command: ReplaceApiKeyCredentialCommand): ReplaceApiKeyCredentialResult
}
```

This keeps the mutation logic out of controllers and allows the design to stay aligned with the
repo’s command/handler seams.

## Runtime Authentication Rules

### Authoritative enforcement after replacement

After the replacement operation commits:

1. the successor credential row is the only row that MUST authenticate for the new plaintext key
2. the predecessor row MUST NOT authenticate anymore
3. predecessor denial MUST occur before principal materialization and before authorization runs
4. authorization semantics for the bound principal remain unchanged

### How `R2dbcApiKeyCredentialStateLookup` stays narrow

The lookup logic should remain keyed by the presented `lookup_key`. It does **not** need to resolve
credential families or search across related rows.

That means the runtime rule is simple:

- when the old key is presented, the predecessor row is loaded by its old `lookup_key`, and its
  non-active/replaced state causes denial
- when the new key is presented, the successor row is loaded by its new `lookup_key`, and normal
  active verifier validation succeeds

This is important: replacement semantics are enforced by **state transition on the predecessor row
**, not by adding a runtime family-resolution engine.

### Audit-ready denial semantics

For the current proving slice, replaced predecessor denial should use the same narrow runtime proof
seam already used for revoked/inactive credential denial on
`/api/authorization/workspace-access/current`.

Recommended behavior:

- HTTP: `401 Unauthorized`
- protected query execution: does not occur
- audit fact: existing `AuthorizationReasonCode.REVOKED_CREDENTIAL` may be reused
- optional internal exception reason: `REPLACED`

This keeps governance scope narrow while still making the replacement transition explainable in code
and tests.

## Before/After Proof on `/api/authorization/workspace-access/current`

The existing integration tests already prove:

- allowed API-key access
- authorization-controlled deny
- revoked/inactive credential deny

This change extends that exact slice with one new lifecycle scenario family in both H2 and
PostgreSQL suites.

### Required proving sequence

#### Scenario A: predecessor works before replacement

- seed an active API-key credential
- call `/api/authorization/workspace-access/current` with the old API key
- assert `200 OK`

#### Scenario B: replacement executes

- run the replacement command/use case inside the test arrangement
- capture returned successor plaintext API key
- assert persisted predecessor/successor linkage as needed for the database flavor

#### Scenario C: predecessor is denied after replacement

- call the same endpoint with the old API key
- assert `401 Unauthorized`
- assert runtime audit-ready proof consistent with revoked credential handling

#### Scenario D: successor works after replacement

- call the same endpoint with the returned successor API key
- assert `200 OK`
- assert the principal and authorization outcome remain correct for the same workspace

### Why this slice is enough

This endpoint already exercises the full path:

- API-key parsing
- credential lookup
- verifier comparison
- principal materialization
- workspace context resolution
- authorization
- audit-ready proof

So it is the right before/after demonstration point without adding new endpoints or management APIs.

## Testing Strategy

| Layer       | What to Test                     | Approach                                                                                                            |
|-------------|----------------------------------|---------------------------------------------------------------------------------------------------------------------|
| Unit        | Replacement handler rules        | Verify active predecessor required, successor created once, predecessor invalidated, and linkage persisted.         |
| Unit        | Credential-state failure mapping | Verify replaced predecessor yields the expected failure reason and remains unauthenticated.                         |
| Integration | H2 proving slice                 | Use existing `WorkspaceAccessSummaryEndpointIntegrationTest` to prove before/after cutover on the current endpoint. |
| Integration | PostgreSQL proving slice         | Mirror the same lifecycle proof in `WorkspaceAccessSummaryEndpointPostgresIntegrationTest`.                         |
| E2E         | Not required for this change     | Existing backend integration slice is sufficient because no new HTTP management surface is introduced.              |

## Migration / Rollout

Additive schema change only.

Rollout plan:

1. extend `api_key_credentials` with nullable replacement lineage fields
2. add the replacement command/use case and persistence implementation
3. update runtime credential-state semantics to deny replaced predecessors
4. extend H2 and PostgreSQL proving-slice tests

No public migration choreography, no feature flag, and no staggered cutover window are required for
this narrow change.

## Explicit Deferrals

The following remain out of scope and must not be pulled into this design:

- service-account credential replacement or JWT rotation
- dual-active overlap windows or grace periods
- inventory/list/detail/search views for credentials
- broad issuance/admin CRUD
- last-used tracking
- expiry-policy framework or scheduled lifecycle actions
- labels, tags, ownership expansion, or dashboards
- generic credential-family management across credential types
- platform-wide lifecycle redesign

## Open Questions

- [ ] What test-only caller or application seam should invoke `ReplaceApiKeyCredentialCommand`
  first, given there is intentionally no new operator HTTP API in scope?
- [ ] Should predecessor denial use a distinct internal `REPLACED` reason while still mapping to
  existing `REVOKED_CREDENTIAL` audit proof, or should it reuse `REVOKED` end-to-end for absolute
  minimum change?
