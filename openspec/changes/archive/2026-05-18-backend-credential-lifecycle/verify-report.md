## Verification Report

**Change**: backend-credential-lifecycle
**Version**: N/A

---

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 10 |
| Tasks complete | 10 |
| Tasks incomplete | 0 |

All checklist items in `openspec/changes/backend-credential-lifecycle/tasks.md` are now marked complete.

---

### Build & Tests Execution

**Build**: ✅ Passed

Command: `./gradlew build` in `server/smp`
Result: `BUILD SUCCESSFUL`

**Tests**: ✅ Passed

Command: `./gradlew test` in `server/smp`
Result: `BUILD SUCCESSFUL`
Observed execution state: Gradle reported tasks as `UP-TO-DATE`; no failing tests or build steps were observed during this verify run.

**Coverage**: ➖ Not configured as a blocking threshold (`coverage_threshold: 0`)

---

### Spec Compliance Matrix

| Requirement | Scenario | Test | Result |
|-------------|----------|------|--------|
| Credentials | Predecessor API key is accepted before replacement completes | `WorkspaceAccessSummaryEndpointIntegrationTest > allows old api key before replacement and new api key after replacement on h2`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > allows old api key before replacement and new api key after replacement on postgres` | ✅ COMPLIANT |
| Credentials | Successor API key is accepted after replacement completes | `WorkspaceAccessSummaryEndpointIntegrationTest > allows old api key before replacement and new api key after replacement on h2`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > allows old api key before replacement and new api key after replacement on postgres` | ✅ COMPLIANT |
| Credentials | Predecessor API key is denied after replacement completes | `WorkspaceAccessSummaryEndpointIntegrationTest > allows old api key before replacement and new api key after replacement on h2`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > allows old api key before replacement and new api key after replacement on postgres` | ✅ COMPLIANT |
| Credentials | Dual-active overlap is out of scope for replacement cutover | Same H2/PostgreSQL cutover tests plus `R2dbcApiKeyCredentialReplacementGatewayTest > replaces one active api key credential with one successor and invalidates predecessor` | ✅ COMPLIANT |
| Credentials | Service-account rotation remains deferred | Code inspection only; no service-account lifecycle expansion found | ✅ COMPLIANT |
| Credentials | Inventory and generalized family management remain deferred | Code inspection only; no inventory/detail/search/family-management surface found | ✅ COMPLIANT |
| Platform | Old API key allows access before replacement | `WorkspaceAccessSummaryEndpointIntegrationTest > allows old api key before replacement and new api key after replacement on h2`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > allows old api key before replacement and new api key after replacement on postgres` | ✅ COMPLIANT |
| Platform | New API key allows access after replacement | `WorkspaceAccessSummaryEndpointIntegrationTest > allows old api key before replacement and new api key after replacement on h2`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > allows old api key before replacement and new api key after replacement on postgres` | ✅ COMPLIANT |
| Platform | Old API key is denied after replacement | `WorkspaceAccessSummaryEndpointIntegrationTest > allows old api key before replacement and new api key after replacement on h2`; `WorkspaceAccessSummaryEndpointPostgresIntegrationTest > allows old api key before replacement and new api key after replacement on postgres` | ✅ COMPLIANT |
| Platform | Replacement proof stays on the existing proving endpoint | Same H2/PostgreSQL cutover tests on `/api/authorization/workspace-access/current` | ✅ COMPLIANT |
| Platform | Broad credential lifecycle platform behavior remains deferred | Code remains narrow; no broad platform expansion found | ✅ COMPLIANT |
| Governance | Successor API-key allow is audit-ready at runtime after replacement | Existing allow-path audit seam exists, but the new cutover tests do not assert audit facts for successor-after-replacement specifically | ⚠️ PARTIAL |
| Governance | Predecessor API-key denial is audit-ready at runtime after replacement | Existing replaced→revoked audit mapping exists, but the new cutover tests do not assert audit facts for predecessor-after-replacement specifically | ⚠️ PARTIAL |
| Governance | Broader lifecycle governance remains deferred | No durable audit/reporting/dashboard expansion found | ✅ COMPLIANT |

**Compliance summary**: 12/14 scenarios compliant, 2/14 partial, 0/14 failing

---

### Correctness (Static — Structural Evidence)
| Requirement | Status | Notes |
|------------|--------|-------|
| Minimal persisted replacement metadata | ✅ Implemented | `002-create-api-key-credentials.yaml` adds `replaced_by_credential_id`, `replaced_credential_id`, and `replaced_at`. |
| Replacement denial reason supported in credential state | ✅ Implemented | `ApiKeyCredentialFailureReason.REPLACED` added in `ApiKeyCredentialStateLookup.kt`. |
| Runtime lookup denies replaced predecessors | ✅ Implemented | `R2dbcApiKeyCredentialStateLookup` denies when `replaced_at != null`. |
| Narrow replacement command/use case exists | ✅ Implemented | `ReplaceApiKeyCredentialCommand.kt` and `R2dbcApiKeyCredentialReplacementGateway.kt` add the command/handler/gateway. |
| Transactional cutover invalidates predecessor and creates successor | ✅ Implemented | Gateway inserts successor then updates predecessor inside one transaction. |
| Existing security/audit path remains narrow | ✅ Implemented | `IdentitySecurityConfiguration` maps `REPLACED` into existing `REVOKED_CREDENTIAL` audit path. |
| Existing proving slice extended with before/after cutover proof | ✅ Implemented | H2 and PostgreSQL integration suites now include cutover scenarios on the existing endpoint. |

---

### Coherence (Design)
| Decision | Followed? | Notes |
|----------|-----------|-------|
| Model replacement locally on `api_key_credentials` | ✅ Yes | Additive self-referential columns were used. |
| Prefer additive lineage columns over mapping table | ✅ Yes | No separate lineage table introduced. |
| Single atomic cutover, no overlap window | ✅ Yes | Transactional handler exists and tests prove predecessor denial plus successor acceptance after cutover. |
| Keep runtime authentication contract shape unchanged | ✅ Yes | Existing parse → lookup → verify → state evaluation path remains intact. |
| Treat replaced predecessors as credential-state denial | ✅ Yes | Replaced credentials fail authentication with `REPLACED` mapped to unauthorized. |
| Prove behavior on existing workspace-access endpoint in H2 and PostgreSQL | ✅ Yes | Required integration tests are present in both suites. |

---

### Issues Found

**CRITICAL** (must fix before archive):
- None.

**WARNING** (should fix):
- The new cutover tests prove HTTP/runtime behavior and persisted lineage, but they do not assert the audit facts for successor-after-replacement allow and predecessor-after-replacement deny explicitly, so the governance proof is structurally present but only partially demonstrated by tests.
- This verify run observed `./gradlew test` and `./gradlew build` as successful with Gradle tasks `UP-TO-DATE`, so it confirmed green state but did not force fresh re-execution from scratch.

**SUGGESTION** (nice to have):
- Add explicit `auditHook` assertions inside the new H2/PostgreSQL cutover tests to lock the governance scenarios with direct runtime-proof evidence.

---

### Verdict
PASS WITH WARNINGS

The prior FAIL is cleared: the missing proving-slice cutover tests now exist, tasks are updated, and the narrow change satisfies the core proposal/spec/design scope. Remaining gaps are limited to tighter governance-assertion coverage, not missing functionality or failed required behavior.
