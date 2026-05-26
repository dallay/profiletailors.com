# Platform Specification

## Purpose

Define the reusable SaaS IAM and workspace platform foundation for `server/smp`. This specification
establishes the platform orientation, bounded-context seams, principal and resource-context
taxonomies, CQRS and hexagonal seams, deterministic API protection rules, caching and invalidation
principles, and the phase-one proving slice boundaries required to validate the platform in running
code without forcing full platform breadth in the first implementation wave.

## Requirements

### Requirement: Reusable Platform Orientation

The system MUST be oriented as a reusable SaaS IAM and workspace platform rather than as a one-off
auth module for a single feature.

The system MUST define repo-local contracts owned by Profile Tailors.
The system MUST separate platform target architecture from phase-one implementation scope.
The platform target architecture MUST define the durable semantic model that future backend features
and products can reuse.
Phase one MUST implement only the minimum vertical slice needed to validate the platform direction.
The system MUST NOT depend on external vendor-specific package names, token claim semantics, or
product headers as part of its core platform contract.

#### Scenario: Platform architecture exceeds the first implementation wave

- GIVEN the backend IAM foundation is specified for long-term reuse
- WHEN the phase-one implementation scope is planned
- THEN the platform specification MUST preserve the full reusable semantic model
- AND phase one MUST remain limited to the minimum proving behavior required to validate that model

#### Scenario: Repo-local platform contracts remain independent from vendor assumptions

- GIVEN an external reference or future identity provider suggests a specific claim, header, or
  package convention
- WHEN that convention is not explicitly adopted by the platform specification
- THEN the platform MUST treat it as an adapter concern or deferred detail
- AND the core platform semantics MUST remain repo-local

### Requirement: Platform Bounded Contexts

The system MUST define the following bounded contexts for the platform architecture: Identity,
Tenancy, Authorization, Credentials, Governance, and Platform.

The Platform context MUST own cross-cutting seams required by all other contexts, including
mediator-style dispatch, context propagation, and adapter-facing shared contracts.
The Identity context MUST own principal identity semantics.
The Tenancy context MUST own workspace lifecycle, ownership, and membership semantics.
The Authorization context MUST own permissions, roles, grants, scopes, policies, and effective
authorization evaluation semantics.
The Credentials context MUST own authentication credential and token semantics.
The Governance context MUST own auditing and governance semantics.
Phase one MUST implement only the minimum contracts and behaviors from these contexts required by
the proving slice.

#### Scenario: Cross-context behavior remains bounded

- GIVEN a protected workspace-scoped capability is evaluated
- WHEN the platform resolves authentication, workspace membership, and authorization
- THEN each behavior MUST be attributable to the appropriate bounded context
- AND no single context MUST absorb unrelated responsibilities merely for convenience

#### Scenario: Deferred contexts still exist semantically

- GIVEN Governance or advanced Credentials capabilities are not fully implemented in phase one
- WHEN the platform is specified
- THEN those contexts MUST still be defined as durable architectural seams
- AND their operational breadth MAY be deferred without removing them from the platform model

### Requirement: Hexagonal and CQRS Platform Seams

The system MUST provide hexagonal seams and CQRS/mediator-style dispatch sufficient for the proving
slice and reusable by future platform features.

Application and domain behavior MUST remain independent from HTTP, framework security, and
persistence adapter types.
The system MUST support distinct command and query request contracts and corresponding handlers.
The system MUST allow cross-cutting behaviors to surround request dispatch without changing feature
semantics.
Phase one MUST route the proving slice through the same mediator-style seams intended for future
platform behavior.

#### Scenario: Protected query executes through platform seams

- GIVEN an authenticated principal requests the proving protected capability
- WHEN the request is handled
- THEN the protected application request MUST execute through the platform mediator seam
- AND adapter-specific concerns MUST remain outside the application and domain core

#### Scenario: Cross-cutting platform behavior surrounds dispatch

- GIVEN a request requires principal resolution, active workspace context, and authorization
  evaluation
- WHEN the request is dispatched
- THEN the platform MUST support applying those cross-cutting concerns around the request lifecycle
- AND the feature-specific handler MUST remain focused on business behavior

### Requirement: Resource Context Taxonomy

The system MUST define the following resource context taxonomy: GLOBAL, USER, WORKSPACE, and SYSTEM.

Authorization decisions MUST be evaluated relative to an explicit resource context.
Permissions, grants, scopes, and policies MUST NOT rely on implicit resource-context inference.
Phase one MUST fully support WORKSPACE context for the proving slice.
For `backend-scopes-execution`, the new target-aware proving capability MUST evaluate authorization in WORKSPACE context and MUST use explicit `targetResourceId` input as part of the protected request context.
This capability is implemented as a NEW protected endpoint: `GET /api/authorization/resources/{resourceId}/preview`
This target-aware proving capability is separate from `/api/authorization/workspace-access/current`, is evaluated in WORKSPACE context using explicit `targetResourceId`, and does not overlap with or extend the API-key replacement proving slice.
- The endpoint accepts an explicit `targetResourceId` in the path (resourceId)
- The proving slice is NOT extended onto `/api/authorization/workspace-access/current`

Support for GLOBAL, USER, and SYSTEM contexts is platform-required and MAY be deferred in implementation beyond the contracts required to keep the model stable.
(Previously: WORKSPACE context was required for the proving slice, but no executable target-aware capability was required to carry explicit target resource context for scope reduction.)

#### Scenario: Target-aware workspace request evaluates with explicit target context

- GIVEN the new resource-preview proving capability is defined for workspace data
- AND the request includes an active workspace identifier and explicit `targetResourceId`
- WHEN authorization is evaluated for that capability
- THEN the platform MUST evaluate the request in WORKSPACE resource context
- AND it MUST treat the supplied `targetResourceId` as explicit protected target context rather than as implicit or derived state

#### Scenario: Workspace-scoped request evaluates in explicit context

- GIVEN a protected capability is defined for workspace data
- WHEN authorization is evaluated for that capability
- THEN the platform MUST evaluate the request in WORKSPACE resource context
- AND it MUST NOT treat the capability as context-free

#### Scenario: Non-workspace contexts remain part of the platform model

- GIVEN phase one implements only a workspace-scoped protected capability
- WHEN the platform contracts are defined
- THEN GLOBAL, USER, and SYSTEM contexts MUST remain part of the reusable taxonomy
- AND later features MAY adopt them without redefining the core model

### Requirement: Active Workspace Handling for Phase One

The system MUST establish one active workspace per protected workspace-scoped request in phase one.

For phase one, the active workspace MUST be resolved from an explicit request-supplied workspace
identifier using a repo-local resolver.
If the active workspace identifier is missing for a workspace-scoped protected capability, the
request MUST be rejected.
If the resolved active workspace is not accessible to the requesting principal, the request MUST be
rejected.
Remembered defaults, automatic workspace switching, and multi-workspace batch evaluation are
platform-required considerations but are deferred beyond phase one.

#### Scenario: Phase-one request resolves a single active workspace

- GIVEN a protected workspace-scoped request includes the supported active workspace identifier
- WHEN the request is processed
- THEN the platform MUST resolve exactly one active workspace for that request
- AND downstream authorization evaluation MUST use that resolved workspace context

#### Scenario: Missing active workspace prevents execution

- GIVEN a protected workspace-scoped request omits the required active workspace identifier
- WHEN the request is processed
- THEN the platform MUST reject the request
- AND the protected use case MUST NOT execute

### Requirement: Stateless Scaling, Caching, and Invalidation Principles

The system MUST support stateless scaling principles for platform request processing.

Authorization and identity evaluation for API requests MUST be deterministic from explicit request
input and current authoritative platform state.
Caches MAY be used to improve performance.
Caches MUST reduce lookup cost without changing authorization semantics.
Cached authorization-related data MUST be invalidated or refreshed when underlying authoritative
state changes in a way that could affect effective permissions, scopes, grants, entitlements,
workspace membership, or credential validity.
For the implemented service-account bearer path, credential revocation state MUST be treated as
authoritative state for protected-request evaluation.
A technically valid presented service-account credential MUST NOT continue to authorize access when
current authoritative credential state revokes it.
For the local USER refresh-session path, refresh-credential validity and logout invalidation MUST be treated as authoritative state for session continuation.
A browser that loses in-memory access-token state MAY recover only through current authoritative refresh-session state, not through instance-local memory or durable frontend token persistence.
Caches and stateless nodes MUST NOT preserve a refresh-backed session after authoritative logout, revocation, expiry, or invalidation has occurred.
Caches MUST NOT expand permissions beyond what authoritative state allows.
Phase one MAY use minimal or no cache implementation, but the platform seams MUST permit later safe
caching and invalidation.

#### Scenario: Cached data cannot expand access

- GIVEN a principal previously had access to a protected capability
- AND authoritative platform state later removes that access
- WHEN the system evaluates a later request
- THEN any platform cache MUST NOT cause broader access than current authoritative state permits
- AND the request outcome MUST converge to the revoked state through invalidation or refresh
  behavior

#### Scenario: Stateless nodes produce equivalent decisions

- GIVEN two platform instances evaluate the same protected request against the same authoritative
  state
- WHEN both instances process the request independently
- THEN they MUST produce the same authorization outcome
- AND the outcome MUST NOT depend on instance-local session state

#### Scenario: Revoked service-account credential cannot retain cached access

- GIVEN a service-account credential was previously accepted for
  `/api/authorization/workspace-access/current`
- AND authoritative backend credential state later marks that credential as revoked
- WHEN the platform evaluates a later request with that same credential
- THEN any platform cache MUST NOT cause broader access than the revoked state allows
- AND the request outcome MUST converge to denial for the protected slice

#### Scenario: Invalidated refresh session cannot survive cache or node-local state

- GIVEN a local USER refresh-backed session was previously valid
- AND authoritative backend state later invalidates that refresh session through logout, revocation, or expiry
- WHEN any platform instance evaluates a later refresh attempt for that session
- THEN instance-local state or caches MUST NOT restore the session
- AND the refresh request MUST be denied according to current authoritative state

#### Scenario: Equivalent nodes evaluate refresh continuation consistently

- GIVEN two platform instances evaluate the same refresh request against the same authoritative refresh-session state
- WHEN both instances process the request independently
- THEN they MUST produce the same allow-or-deny refresh outcome
- AND the result MUST NOT depend on which instance previously issued the access token

### Requirement: Deterministic API Protection Principles

The system MUST enforce deny-by-default, explicit-over-implicit, and deterministic API protection behavior.

Protected API behavior MUST require successful authentication, applicable context resolution, and explicit authorization success before access is granted.
The absence of a required permission, grant, membership, or applicable rule MUST result in denial.
Explicit denial MUST override any allow path.
The system MUST NOT infer access from role names, token presence, or unspecified defaults.
Equivalent requests against equivalent state MUST produce equivalent authorization outcomes.
For the existing `/api/authorization/workspace-access/current` proving slice, the same protection principles MUST apply to authenticated USER, authenticated SERVICE_ACCOUNT, and authenticated API_KEY requests.
This change MUST prove end-to-end behavior for that slice with API-key allow, authorization-controlled deny, revoked-or-inactive-credential deny, and completed-replacement cutover outcomes.
For the supported API-key replacement capability, the platform MUST apply one explicit runtime rule: after the replacement operation completes, the successor API key MUST be accepted and the predecessor API key MUST be denied.
The completed replacement rule MUST NOT allow any overlap window where both predecessor and successor are accepted on `/api/authorization/workspace-access/current`.
The API-key replacement proving slice for `/api/authorization/workspace-access/current` MUST remain limited to that endpoint.
The API-key replacement proving slice MUST NOT broaden into new endpoints, service-account rotation, dual-active rollover windows, inventory or detail APIs, or generalized credential-family management.
The API-key replacement proving slice MUST NOT broaden into broad issuance/admin platform behavior beyond what is minimally necessary to execute one API-key replacement path.
For the local USER browser session flow, protected API calls MUST be made with an in-memory access token rather than a durable browser-persisted access token.
For the local USER browser session flow, a `401` from a protected API MAY trigger exactly one refresh-based recovery attempt for the original request.
If that refresh-based recovery attempt fails, the platform and client flow MUST fail closed rather than loop or infer continued access.

#### Scenario: Access is denied by default

- GIVEN a request targets a protected capability
- AND the system cannot establish all required authentication, context, and authorization facts for
  an allow decision
- WHEN the request is evaluated
- THEN the platform MUST deny the request
- AND no protected result MUST be returned

#### Scenario: Explicit denial overrides other access paths

- GIVEN a principal has a role or grant that would otherwise allow a capability
- AND an explicit denial also applies to that principal for the same capability in the same context
- WHEN authorization is evaluated
- THEN the platform MUST deny access
- AND the denial outcome MUST be deterministic

#### Scenario: Service account is allowed on the current workspace-access slice

- GIVEN a persisted service account authenticates successfully through the existing bearer path
- AND the active workspace request is valid
- AND workspace membership and authorization facts explicitly allow access to
  `/api/authorization/workspace-access/current`
- WHEN the protected request is evaluated
- THEN the platform MUST allow the request
- AND the protected slice MUST return the allowed result for that service account

#### Scenario: Service account is denied by authorization on the current workspace-access slice

- GIVEN a persisted service account authenticates successfully through the existing bearer path
- AND the active workspace request is valid
- AND current workspace membership, permission, grant, or denial facts do not produce an explicit
  allow for `/api/authorization/workspace-access/current`
- WHEN the protected request is evaluated
- THEN the platform MUST deny the request
- AND the denial MUST be caused by authorization state rather than by credential-type mismatch

#### Scenario: Revoked service-account credential is denied on the current workspace-access slice

- GIVEN a persisted service account presents a bearer credential that would otherwise authenticate
  successfully
- AND authoritative backend credential state marks that credential as revoked
- WHEN the request targets `/api/authorization/workspace-access/current`
- THEN the platform MUST deny the request before protected access is granted
- AND the protected slice MUST NOT return an allowed result

#### Scenario: Old API key allows access before replacement

- GIVEN a persisted API-key credential authenticates successfully for `/api/authorization/workspace-access/current`
- AND the active workspace request is valid
- AND workspace membership and authorization facts explicitly allow access for the bound principal
- AND no completed replacement has made that credential a predecessor
- WHEN the protected request is evaluated
- THEN the platform MUST allow the request
- AND the protected slice MUST return the allowed result for that API-key principal

#### Scenario: New API key allows access after replacement

- GIVEN an existing active API-key credential has been replaced through the supported replacement capability
- AND the successor API-key credential now authenticates successfully for `/api/authorization/workspace-access/current`
- AND the active workspace request is valid
- AND workspace membership and authorization facts explicitly allow access for the bound principal
- WHEN the protected request is evaluated with the successor API key
- THEN the platform MUST allow the request
- AND the protected slice MUST return the allowed result for that API-key principal

#### Scenario: Old API key is denied after replacement

- GIVEN an existing active API-key credential has been replaced through the supported replacement capability
- AND the predecessor API key would otherwise match and verify successfully
- WHEN the request targets `/api/authorization/workspace-access/current` with that predecessor API key
- THEN the platform MUST deny the request before protected access is granted
- AND the protected slice MUST NOT return an allowed result

#### Scenario: Inactive or revoked API key is denied on the current workspace-access slice

- GIVEN a persisted API-key credential presents a secret that would otherwise authenticate
  successfully
- AND authoritative backend credential state marks that credential as inactive or revoked
- WHEN the request targets `/api/authorization/workspace-access/current`
- THEN the platform MUST deny the request before protected access is granted
- AND the protected slice MUST NOT return an allowed result

#### Scenario: Replacement proof stays on the existing proving endpoint

- GIVEN the replacement capability has completed for an API-key credential pair
- WHEN end-to-end proof for this change is defined
- THEN the proof MUST run on `/api/authorization/workspace-access/current`
- AND the change MUST NOT require new proving endpoints to express before-and-after behavior

#### Scenario: Broad credential lifecycle platform behavior remains deferred

- GIVEN a requested capability requires service-account rotation, dual-active rollover windows, inventory/list/detail APIs, or generalized credential-family management
- WHEN the proving-slice scope for this change is evaluated
- THEN that capability MUST be treated as deferred
- AND the current slice MUST proceed without broadening beyond one API-key replacement cutover path

#### Scenario: Protected request recovers once after access-token expiry

- GIVEN a local USER sends a protected API request with an access token that is no longer accepted
- AND the USER still has a valid refresh-backed session in authoritative backend state
- WHEN the protected request returns `401`
- THEN the client-platform flow MAY perform one refresh-based recovery attempt for that original request
- AND the replayed request MUST be attempted no more than once after a successful refresh

#### Scenario: Protected request fails closed after exhausted recovery

- GIVEN a local USER sends a protected API request that returns `401`
- AND no valid refresh-backed session remains to recover that request
- WHEN the client-platform flow evaluates recovery
- THEN the original request MUST remain denied after the single allowed recovery path is exhausted
- AND the system MUST NOT create an implicit authenticated session from stale client state

### Requirement: Dedicated Refresh and Logout Endpoints for Local User Sessions

The system MUST expose dedicated local USER session-continuation endpoints separate from protected business APIs.

The refresh flow MUST be available through a dedicated endpoint for session continuation.
The logout flow MUST be available through a dedicated endpoint for authoritative session invalidation.
The refresh endpoint MUST accept refresh-cookie transport without requiring an existing valid access token.
The logout endpoint MUST invalidate the current refresh-backed session when one exists and MUST clear client-facing refresh-cookie state in its response.
The system MUST preserve deny-by-default behavior when refresh or logout requests reference missing or invalid session state.

#### Scenario: Refresh endpoint is public to the session-continuation flow

- GIVEN a browser has no current valid access token in memory
- AND the browser still holds a valid refresh cookie for a local USER session
- WHEN the browser calls the dedicated refresh endpoint
- THEN the platform MUST evaluate the refresh credential without requiring prior protected-API authentication
- AND it MUST issue a new access token only if authoritative refresh state allows it

#### Scenario: Logout invalidates session continuity even after access token loss

- GIVEN a browser has an active refresh-backed local USER session
- WHEN the browser calls the dedicated logout endpoint
- THEN the platform MUST invalidate the authoritative refresh session and clear the refresh cookie
- AND later session bootstrap or retry recovery for that session MUST be denied

### Requirement: Pluggable Storage Abstraction Layer

The system MUST provide a pluggable Storage Abstraction Layer (SAL) for object storage operations with support for multiple providers.

The storage API MUST be based on Kotlin coroutines (suspend functions and kotlinx.coroutines.Flow) for efficient streaming of large objects.
The system MUST support multiple storage providers simultaneously, configurable by name through Spring Boot properties.
The system MUST provide implementations for Local filesystem, AWS S3, and Cloudflare R2 (S3-compatible) providers.
The storage API MUST expose operations for upload (streaming), download (streaming), delete, list, and presigned GET URLs.
The system MUST provide a BucketRegistry for resolving storage providers by name at runtime.
The system MUST provide a default storage bean for injection by type.
The LocalFilesystem provider MUST protect against path traversal attacks (e.g., `..` in keys).
The S3/R2 providers MUST support presigned GET URLs and handle large file uploads efficiently.
The storage module MUST be reusable from other bounded contexts in the backend.
The system MUST integrate with existing observability hooks (MetricsHook/AuditHook) when available.

#### Scenario: Upload and download with local filesystem

- GIVEN a bucket "local-test" mapped to `/tmp/storage/local-test`
- WHEN a file is uploaded by streaming with key "foo/bar.txt"
- THEN the file can be downloaded and its content matches the uploaded data
- AND listing with prefix "foo/" contains "foo/bar.txt"

#### Scenario: Presigned URL generation for S3

- GIVEN an S3 provider configured for bucket "attachments"
- WHEN a presigned GET URL is requested for "attachments", "invoices/1.pdf", with 600 seconds expiry
- THEN a valid URL is returned that allows downloading the object within 600 seconds

#### Scenario: Path traversal protection in LocalFS

- GIVEN a local bucket mapped to `/var/data/bucket`
- WHEN an upload is attempted with key "../secret.txt"
- THEN the operation fails with StorageSecurityException preventing path traversal

#### Scenario: Multi-provider resolution

- GIVEN providers "local" and "attachments" are configured
- WHEN `registry.getStorage("attachments")` is called
- THEN a Storage instance associated with the S3Provider is returned

#### Scenario: Large object streaming

- GIVEN a large object (>100MB)
- WHEN uploaded via Flow chunks
- THEN the upload does not consume memory proportional to file size
- AND download also streams in chunks without loading the entire file into memory
