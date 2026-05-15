# Delta for Foundation

## REMOVED Requirements

### Requirement: Repo-Local Foundation Ownership

(Reason: Replaced by the broader platform-oriented specification in `platform/spec.md`, which
preserves repo-local ownership while moving the canonical architecture to reusable IAM/workspace
platform terms.)

### Requirement: Hexagonal Backend Seams

(Reason: Replaced by the broader platform-oriented specification in `platform/spec.md`, which
carries forward hexagonal boundaries as reusable platform seams.)

### Requirement: CQRS and Mediator Dispatch

(Reason: Replaced by the broader platform-oriented specification in `platform/spec.md`, which
defines CQRS and mediator dispatch as shared platform behavior.)

### Requirement: Active Workspace Context for the First Slice

(Reason: Replaced by the broader platform-oriented specification in `platform/spec.md` and
`tenancy/spec.md`, which now define active workspace handling within the reusable workspace platform
model.)

### Requirement: Persistence and Migration Conventions for the Proving Slice

(Reason: Foundation persistence expectations are now distributed to the platform-bounded specs and
will be detailed further in design. Keeping the old requirement here would duplicate or conflict
with the new bounded-context structure.)
