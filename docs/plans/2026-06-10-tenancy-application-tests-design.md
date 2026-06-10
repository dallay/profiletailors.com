# Test Coverage: tenancy.application Handlers

## Goal

Add unit tests for `AddWorkspaceOwnerHandler` and `TransferWorkspaceOwnershipHandler` in `server/smp`, raising `tenancy.application` coverage from ~30% to ~80%+.

## Background

`tenancy.application` sits at 30% instruction coverage. The gap is concentrated in two internal handlers that have no tests:

- `AddWorkspaceOwnerHandler` (lines 16-88 of `TenancyOwnershipHandlersInternal.kt`)
- `TransferWorkspaceOwnershipHandler` (lines 90-170 of `TenancyOwnershipHandlersInternal.kt`)

Existing `UpdateWorkspaceMembershipStatusHandlerTest` establishes the testing pattern: in-memory repositories, fixed context providers, `CapturingAuditHook`.

## Scenarios

### AddWorkspaceOwnerHandler

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Actor is owner, target is active non-owner member | Ownership added, result contains both owners |
| 2 | Actor is not an owner | `WorkspaceOwnerAccessDeniedException` |
| 3 | Target is not an active member | `OwnerTargetMustBeActiveMemberException` |
| 4 | Target is already an owner | Idempotent — no duplicate, result unchanged |
| 5 | Success | `recordSuccess` audit called with `workspace.owner.add` |

### TransferWorkspaceOwnershipHandler

| # | Scenario | Expected |
|---|----------|----------|
| 1 | Actor is owner, transfer to active non-owner member | Ownership transferred, old owner removed |
| 2 | Transfer to self | `IllegalArgumentException("Cannot transfer ownership to yourself")` |
| 3 | Actor is not an owner | `WorkspaceOwnerAccessDeniedException` |
| 4 | Target is not an active member | `OwnerTargetMustBeActiveMemberException` |
| 5 | Success | `recordSuccess` audit called with `workspace.owner.transfer` |

## File to Create

`server/smp/src/test/kotlin/com/profiletailors/smp/tenancy/application/TenancyOwnershipHandlersInternalTest.kt`

## Dependencies

Reuses helper classes from `UpdateWorkspaceMembershipStatusHandlerTest.kt`:
- `FixedPrincipalContextProvider`
- `FixedResourceContextProvider`
- `StubWorkspaceMembershipLookup`
- `CapturingAuditHook`
- `InMemoryWorkspaceOwnershipRepository`
- `InMemoryWorkspaceMembershipRepository`

## Success Criteria

- All 10 scenarios pass
- `tenancy.application` instruction coverage ≥ 80%
- JaCoCo report shows `AddWorkspaceOwnerHandler` and `TransferWorkspaceOwnershipHandler` at 80%+
