# Delta for Credentials

## MODIFIED Requirements

### Requirement: JWT, Service Account, and API Key Platform Concepts

The platform MUST recognize JWT tokens, service accounts, and API keys as first-class credential concepts in the target architecture.

Phase one MUST implement JWT-backed authentication for USER principals in the proving slice.
This change MUST additionally implement bearer-based service-account authentication for the existing `/api/authorization/workspace-access/current` proving slice.
The implemented service-account path MUST validate a presented bearer credential against authoritative backend credential state before protected access is granted.
The implemented service-account path MUST support authoritative revocation enforcement for that credential path.
This change MUST additionally implement API-key authentication for the existing `/api/authorization/workspace-access/current` proving slice only.
For this change, persisted API-key credential state MUST include only the minimum authoritative fields required to securely identify a credential record for lookup, verify the presented secret without plaintext or reversible secret persistence, bind the credential to one persisted principal, determine whether the credential is active or revoked, and make one completed replacement cutover explicit when predecessor/successor linkage is needed for enforcement.
The implemented API-key path MUST validate a presented API key against authoritative backend credential state through lookup plus verifier comparison before protected access is granted.
The implemented API-key path MUST support one narrow API-key replacement capability for an existing active API-key credential on the existing proving slice.
If replacement lineage is needed to make cutover semantics explicit and testable, the system MUST persist explicit predecessor/successor semantics that relate the replaced API-key credential to its replacement credential.
When an API-key replacement completes, the successor API key MUST be accepted for subsequent authentication and the predecessor API key MUST be denied for subsequent authentication.
The replacement cutover MUST NOT permit a dual-active overlap window, grace period, or delayed predecessor denial.
The implemented API-key path MUST deny a presented API key when authoritative backend credential state is inactive, revoked, or replaced by a completed successor.
The current change MUST prove before-and-after API-key behavior end to end on `GET /api/authorization/workspace-access/current`.
Service-account rotation or replacement MUST remain deferred.
Dual-active rollover windows, grace periods, overlap semantics, and delayed predecessor revocation MUST remain deferred.
Inventory, list, detail, search, or operator-facing management APIs for credentials MUST remain deferred.
Broad credential issuance or admin CRUD expansion beyond what is minimally necessary to execute one replacement path MUST remain deferred.
Generalized credential-family management across credential types MUST remain deferred.
Deferred implementation MUST NOT remove any credential concept from the canonical platform model.
The platform SHOULD preserve a path for future external federation or provider-backed token validation without redefining the core Credentials context.

(Previously: The implemented API-key path supported active/inactive/revoked validation on the proving slice while API-key issuance, rotation workflows, inventory surfaces, broad metadata expansion, and generalized credential-platform redesign remained deferred.)

#### Scenario: Predecessor API key is accepted before replacement completes

- GIVEN a persisted API-key credential is active for a principal allowed to access `/api/authorization/workspace-access/current`
- AND no completed replacement has made that credential a predecessor
- WHEN the platform validates a request that presents that API key
- THEN the platform MUST authenticate the request through the API-key credential path
- AND the proving slice MAY continue into identity and authorization evaluation

#### Scenario: Successor API key is accepted after replacement completes

- GIVEN an existing active API-key credential has been replaced through the supported replacement capability
- AND a successor API-key credential is explicitly linked as the completed replacement for that predecessor
- WHEN a request to `/api/authorization/workspace-access/current` presents the successor API key
- THEN the platform MUST authenticate the request through the API-key credential path
- AND the proving slice MAY continue into identity and authorization evaluation

#### Scenario: Predecessor API key is denied after replacement completes

- GIVEN an existing active API-key credential has been replaced through the supported replacement capability
- AND a successor API-key credential is explicitly linked as the completed replacement for that predecessor
- WHEN a request to `/api/authorization/workspace-access/current` presents the predecessor API key
- THEN the platform MUST reject the request as unauthenticated or invalid for the protected slice
- AND the protected use case MUST NOT execute

#### Scenario: Dual-active overlap is out of scope for replacement cutover

- GIVEN a requested credential lifecycle behavior requires a period where predecessor and successor API keys are both valid
- WHEN the replacement scope for this change is evaluated
- THEN the platform MUST treat that behavior as deferred
- AND the supported replacement capability MUST keep a no-overlap cutover rule

#### Scenario: Service-account rotation remains deferred

- GIVEN a requested capability requires service-account credential replacement or rotation
- WHEN the credential scope for this change is evaluated
- THEN the platform MUST treat that capability as deferred
- AND the current change MUST proceed without adding service-account lifecycle behavior

#### Scenario: Inventory and generalized family management remain deferred

- GIVEN a requested capability requires credential inventory APIs, credential detail APIs, or generalized credential-family management
- WHEN the credential scope for this change is evaluated
- THEN the platform MUST treat that capability as deferred
- AND the current change MUST proceed without broadening beyond one API-key replacement capability
