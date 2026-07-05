## 4R Review
### Scope
- Repository: `dallay/profiletailors.com`
- PR / branch / commit / area: `97e2683` ("feat(tenancy): wrap ownership mutations and audit in atomic transactions")
- Files inspected:
    - `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/application/TenancyOwnershipHandlersInternal.kt`
    - `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/application/UpdateWorkspaceMembershipStatusHandler.kt`
    - `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/application/TenancyMutationAuditor.kt`
    - `server/smp/src/main/kotlin/com/profiletailors/smp/media/infrastructure/persistence/R2dbcAtomicTransactionRunner.kt`
    - `shared/common/src/main/kotlin/com/profiletailors/common/domain/persistence/AtomicTransactionRunner.kt`
    - `shared/storage/src/main/kotlin/com/profiletailors/storage/infrastructure/S3RetryHelper.kt`
    - `shared/spring-boot-common/src/main/kotlin/com/profiletailors/config/WorkspaceContextWebFilter.kt`
- Change type: Backend / Security-sensitive / Critical path
- Critical path affected: Workspace Ownership & Membership management
- Sensitive areas touched: Atomic Transactions, Audit Logging, multi-tenant isolation, S3 Resilience.

### Summary
- Product impact: Ensures that workspace ownership changes are always recorded in the audit trail.
- Main risk: Production startup failure due to missing repository implementations.
- Merge confidence: ⚠️ Needs attention (logic is sound, but wiring is incomplete).

### 4R Scorecard
| Lens | Score | Finding |
|---|---|---|
| R1 Risk | ❌ | Missing production implementations for required repositories will crash production. |
| R2 Readability | ✅ | Strict adherence to hexagonal architecture and CQRS patterns. |
| R3 Reliability | ⚠️ | Logic proven by unit tests (stubs), but production wiring is untested and likely broken. |
| R4 Resilience | ✅ | `AtomicTransactionRunner` and `S3RetryHelper` significantly improve system robustness. |

### Critical Findings
#### CRITICAL-1: Missing Production Implementations for Tenancy Repositories
- R: R1 Risk / R3 Reliability
- Evidence: `AddWorkspaceOwnerHandler` and `UpdateWorkspaceMembershipStatusHandler` depend on `WorkspaceOwnershipRepository` and `WorkspaceMembershipRepository`. Grep and file listing confirm that while `R2dbcWorkspaceReadRepository` and `R2dbcWorkspaceMutationRepository` exist, no concrete implementations for the `Ownership` or `Membership` interfaces exist in the infrastructure layer.
- Impact: Application context will fail to load in production, leading to a `NoSuchBeanDefinitionException`.
- Reproduction: Attempt to start the `smp` server with the `dev` profile without mocking these beans.
- Required fix: Implement `R2dbcWorkspaceOwnershipRepository` and `R2dbcWorkspaceMembershipRepository` using `DatabaseClient` in `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/infrastructure/`.
- Verification required: Successful application startup and E2E test run for ownership transfer.

### Warnings
#### WARNING-1: Redundant Multi-tenant Joins vs RLS
- R: R1 Risk
- Evidence: `R2dbcWorkspaceReadRepository.kt` manually joins `workspace_memberships` and `workspace_ownerships` on `workspace_id` while `WorkspaceContextWebFilter` is also configured to set the session variable for PostgreSQL RLS. Redundancy increases complexity and risk of drift if RLS policies are updated but manual queries are not.
- Recommended fix: Document the relationship between manual `workspace_id` filtering and RLS, or move towards full RLS reliance.
- Verification required: Code review of all tenancy repositories.

### Suggestions
#### SUGGESTION-1: Consolidate AtomicTransactionRunner Location
- R: R2 Readability
- Evidence: `R2dbcAtomicTransactionRunner.kt` is currently located in the `media` module infrastructure, but it is a cross-cutting concern used by `tenancy` and `publishing`.
- Recommendation: Move the R2DBC implementation to `shared/spring-boot-common` or a dedicated `persistence-common` module to clarify its shared nature.

### Missing Evidence
- Concrete `WorkspaceOwnershipRepository` and `WorkspaceMembershipRepository` implementations.
- Integration tests that load the full application context with these handlers active.

### Checks Executed
| Command | Result |
|---|---|
| `just backend-lint` | Passed |
| `just backend-test` (partial) | Passed (using unit test stubs) |
| `just frontend-check` | Failed (pre-existing type errors in marketing site) |

### Required Follow-Up
1. Implement `R2dbcWorkspaceOwnershipRepository`.
2. Implement `R2dbcWorkspaceMembershipRepository`.
3. Add an integration test that performs a real database ownership transfer to verify the `AtomicTransactionRunner` and `R2DBC` integration.

### Verdict
Do not ship (due to CRITICAL-1)
