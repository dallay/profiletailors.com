# 4R Quality and Resilience Review: Tenancy Bounded Context Remediation

## Overview

This document presents a comprehensive **4R Review** (Risk, Readability, Reliability, Resilience) and subsequent technical remediation actions performed on the **Tenancy Bounded Context** within the Profile Tailors workspace.

The review targets critical paths managing multi-tenant workspace isolation, member status changes, owner registration, ownership transfers, and owner deletion flows. The core purpose of the remediation is to ensure that database state mutations and their respective audit trails are executed within robust transactional boundaries with high concurrency protection.

---

## Changes

The following remediation files and structural modifications have been introduced into the workspace:

### 1. Concrete Production-Grade R2DBC Repositories

- **`R2dbcWorkspaceOwnershipRepository.kt` (New):**
  Located under `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/infrastructure/`.
  Implements the `WorkspaceOwnershipRepository` interface using Spring’s reactive `DatabaseClient`. It provides fully mapped, parametrized queries for ownership inserts, existence checks, workspace ownership retrieval, and owner removals.
  To serialize concurrent deletions and prevent a workspace from ever becoming ownerless, the `removeIfReplacementExists` method executes an exclusive database-level row lock (`SELECT ... FOR UPDATE`) prior to executing the atomic conditional check-and-delete.

- **`R2dbcWorkspaceMembershipRepository.kt` (New):**
  Located under `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/infrastructure/`.
  Implements the `WorkspaceMembershipRepository` interface. It provides reactive R2DBC queries to retrieve memberships by workspace and update membership statuses natively in the database.

### 2. Missing Handler for Exposed DELETE Workspace Owner API

- **`RemoveWorkspaceOwnerHandler` (New):**
  Added inside `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/application/TenancyOwnershipHandlersInternal.kt` to handle `RemoveWorkspaceOwnerCommand`.
  Previously, the HTTP DELETE endpoint `DELETE /api/tenancy/workspace-ownership/owners/{principalId}` dispatched this command, but there was no corresponding handler, causing a `HandlerNotFoundException` at runtime.

### 3. Handler Refactoring & Error-Handling Simplification

- **Shared Inline Helper `auditedMutation`:**
  To reduce code duplication and centralize error-handling and audit contracts, a shared `auditedMutation` private inline helper was extracted inside `TenancyOwnershipHandlersInternal.kt`. It wraps the execution inside `transactionRunner.runAtomically`, handles standard validation exceptions, logs failures via `TenancyMutationAuditor.recordRejected`, and rethrows.
  `AddWorkspaceOwnerHandler`, `TransferWorkspaceOwnershipHandler`, and `RemoveWorkspaceOwnerHandler` have been refactored to route through this unified helper.

### 4. Bounded Context Wiring as Spring Beans

- **Stereotype Annotations:**
  Added the `@com.profiletailors.common.domain.Service` custom stereotype annotation to:
  - `AddWorkspaceOwnerHandler`
  - `TransferWorkspaceOwnershipHandler`
  - `RemoveWorkspaceOwnerHandler`
  - `UpdateWorkspaceMembershipStatusHandler`
  - `TenancyMutationAuditor`
  This registers these classes inside Spring's ApplicationContext for automatic component scanning, allowing them to be fully wired and automatically routed by the Mediator.

### 5. Validation and Verification Test Suites

- **Unit Tests:**
  Added comprehensive tests for `RemoveWorkspaceOwnerHandler` in `TenancyOwnershipHandlersInternalTest.kt`.
- **Database Integration Tests:**
  Created `R2dbcWorkspaceOwnershipRepositoryTest.kt` and `R2dbcWorkspaceMembershipRepositoryTest.kt` under `server/smp/src/test/.../tenancy/infrastructure/` running in active Testcontainers environments to verify actual PostgreSQL querying, row mapping, and transactional rollbacks.
- **Concurrency Test:**
  Added a concurrent database test inside `R2dbcWorkspaceOwnershipRepositoryTest` which launches parallel coroutines that simultaneously attempt to remove different owners of the same workspace. This verifies under real database load that the exclusive row-locking strategy safely preserves the one-owner invariant.

---

## Usage

### Handler Registration & Discovery

All mutation handlers in this context adhere to the Monorepo Hexagonal Architecture principles (`domain ← application ← infrastructure`). They are registered as Spring Beans using the workspace custom `@Service` stereotype and are completely decoupled from any direct HTTP or controller layer.

### Dispatching Commands

To invoke any of these operations from controllers or API endpoints, inject the workspace `Mediator` and dispatch the command:

```kotlin
// Example: Adding a workspace owner
mediator.send(AddWorkspaceOwnerCommand(targetPrincipalId = "user_123"))

// Example: Removing a workspace owner
mediator.send(RemoveWorkspaceOwnerCommand(targetPrincipalId = "user_123"))
```

Spring's Mediator will automatically locate the scanned handlers in the registry and dispatch them within transactional contexts.

---

## Troubleshooting

### Unsatisfied Dependency Exception

If the application context fails to initialize, check the constructor arguments of the mutation handlers:
1. Ensure `TenancyMutationAuditor` is carrying the custom `@Service` annotation.
2. Confirm that the Spring Bean for `Clock` is declared (normally configured in `PlatformBootstrapConfiguration`).
3. Ensure that the active databases contain the expected tables (`workspace_ownerships`, `workspace_memberships`) created by Liquibase migrations.

### Concurrency Block / Lock Timeout

The `removeIfReplacementExists` operation implements database row-level locking via `SELECT ... FOR UPDATE`. If concurrent deletes are held, check if database connection pools or transaction timeouts are tuned to prevent thread starvation during peak traffic.

---

## References

- **Spec Location:** `openspec/specs/publishing/spec.md`
- **Architecture Model:** `docs/architecture/c4/` (Modular Monolith and Hexagonal architecture)
- **Spring Modulith Specification:** `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/ModuleMetadata.kt`
