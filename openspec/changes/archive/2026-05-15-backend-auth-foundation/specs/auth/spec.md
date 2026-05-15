# Delta for Auth

## REMOVED Requirements

### Requirement: JWT-First Authentication Boundary

(Reason: Replaced by `identity/spec.md` and `credentials/spec.md`, which separate principal identity
semantics from credential semantics in the broader platform model.)

### Requirement: Workspace Membership as the Authorization Membership Model

(Reason: Replaced by `tenancy/spec.md`, which defines workspace membership as part of the tenancy
model rather than as a narrow auth-local concern.)

### Requirement: Roles Are Compositions of Permissions

(Reason: Replaced by `authorization/spec.md`, which generalizes role composition across the reusable
IAM platform.)

### Requirement: Extensible Permission Naming by Feature Area

(Reason: Replaced by `authorization/spec.md`, which now defines explicit permission format and
platform-wide extensibility rules.)

### Requirement: Minimal Protected Vertical Slice

(Reason: Replaced by `authorization/spec.md` and `platform/spec.md`, which preserve the proving
slice while anchoring it in the broader platform architecture.)

### Requirement: Persistence Expectations for the Auth Slice

(Reason: Persistence expectations now belong to the platform-bounded context specs and subsequent
design artifacts, not to a narrow auth-only slice spec.)

### Requirement: Explicit Deferrals for First-Slice Auth Scope

(Reason: Deferrals are now distributed across the new bounded-context specs so the broader reusable
platform can stay explicit about phase-one versus deferred breadth.)
