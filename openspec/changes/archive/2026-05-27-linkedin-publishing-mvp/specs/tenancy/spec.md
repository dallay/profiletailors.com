# Delta for Tenancy

## ADDED Requirements

### Requirement: Workspace Ownership of Social Publishing Resources

The system MUST treat social connections, social accounts, publication drafts, publication jobs,
delivery attempts, and publishing assets as workspace-scoped tenancy resources.

A publishing resource MUST belong to exactly one workspace. A principal with access to one workspace
MUST NOT gain implicit visibility into publishing resources of another workspace. Moving or sharing
a publishing resource across workspaces MUST NOT be implied by provider identity alone.

#### Scenario: Publishing resources stay isolated per workspace

- GIVEN two workspaces exist in the system
- AND the same authenticated principal can access only one of them
- WHEN the principal queries or mutates publishing resources
- THEN the system MUST evaluate those resources within the active workspace context
- AND it MUST NOT expose resources owned by the other workspace

#### Scenario: Connected provider identity does not collapse workspace ownership

- GIVEN a provider account identity has previously been connected in one workspace
- WHEN another workspace attempts to use or mutate that connection without an explicit
  workspace-scoped registration path
- THEN the system MUST reject the operation
- AND workspace ownership of the publishing resource MUST remain explicit
