# Cross-tenant media gates (disc-004 stage 1)

Handlers scope SQL by header-supplied workspaceId without membership proof.
Any authenticated user can read, delete and write foreign workspaces.

## Route

Delegated direct. Same branch fix/security-audit-run-1. TDD fail-first.

## Changes

1. New `authorization/application/WorkspaceMembershipGate.kt` house service:
   requireActiveMember resolves the current principal plus the accessed
   workspaceId through the public WorkspaceMembershipResolver and throws
   AuthorizationDeniedException on missing or inactive membership. Same
   message as the decider missing-membership branch. No new permission keys,
   no migration, no behavior change for members.
2. Gate GetWorkspaceAsset, ListWorkspaceAssets, DeleteWorkspaceAsset,
   CreateUploadedAsset, LegacyUploadAsset and PutAsset handlers.
3. BDD proof: victim workspace asset plus attacker header expects 403.
   Existing scenarios keep proving legit 200 without seed changes.
4. Anonymous signed preview flows straight to the repository and stays
   untouched.

## Non-goals

Hashtags, ideas, analytics and publishing contexts ride stage 2. No audit
event emission on this path; audit hooks stay a documented residual.

## Gates

Handler unit tests plus Detekt plus the media and security BDD lanes.
