# ADR-0023: platformadmin May Consume governance Application Ports for Back Office Takedown

- Status: Accepted
- Date: 2026-09-21
- Decision owners: Principal Architect
- Scope: `server/smp` modules `platformadmin` and `governance`
- Supersedes: None
- Superseded by: None
- Related:
  - OpenSpec: `openspec/changes/671-takedown-governance-admin/`
  - C4: `docs/architecture/c4/03-component.md`
  - Issues/PRs: dallay/profiletailors.com#671, epic #656
  - ADR-0001, ADR-0002, ADR-0016

## Context

Workspace takedown already lives in `governance` (`ApproveTakedownHandler`, `RejectTakedownHandler`,
`ListTakedownReportsHandler`) and is scoped by `ResourceContext` plus workspace permissions. Back
Office needs a cross-workspace list/detail/approve/reject surface under `/api/admin/**`.

ADR-0001 says contexts MUST NOT depend on another context's `application` or `infrastructure`
layers, while also saying cross-context communication SHOULD use shared application interfaces.
`platformadmin` already has named-interface edges to `publishing :: application`,
`identity :: application`, and `tenancy :: application`. Reusing workspace takedown handlers would
import `TakedownReport`, require workspace context, and leak aggregate internals.

## Decision drivers

- Keep hexagonal direction: domain stays inside governance; platformadmin sees DTOs only.
- Preserve workspace `/api/governance/takedown/**` behavior, emails, and `MEDIA_TAKEDOWN_*` audit.
- Enforce ARCH-002 with an explicit named-interface allowlist, not a silent Modulith weakening.
- Reporter email is PII; SUPPORT_AGENT must not gain read.

## Decision

`platformadmin` MAY depend on `governance :: application` only.

That named interface MUST expose ports and DTOs for Back Office takedown. It MUST NOT expose
`TakedownReport` or other governance domain types.

`platformadmin` MUST NOT:

- depend on `governance :: domain` or `governance :: infrastructure`
- import `TakedownReport`
- reuse workspace `ApproveTakedownHandler` / `RejectTakedownHandler` / `ListTakedownReportsHandler`

Governance application ports MUST accept the operator principal id as an argument and MUST NOT read
`ResourceContext` or workspace `GovernanceAuthorizationService`. Domain `approve` / `dismiss`,
`MediaAssetStatusUpdater`, workspace `AuditHook` (`MEDIA_TAKEDOWN_*`), and `TakedownApproved` /
`TakedownRejected` email events MUST remain inside governance.

`platformadmin` application handlers MUST enforce `platform.governance.read` /
`platform.governance.manage` via `OperatorAccessResolver` and MUST publish
`AdminAuditAction.TAKEDOWN_APPROVED` / `TAKEDOWN_REJECTED`.

Asset status MUST be read through a governance application port implemented in
`com.profiletailors.smp.config.bridges`. platformadmin MUST NOT depend on `media`.

This is an ARCH-002 named-interface exception, analogous to `publishing :: application`.

## Scope and boundaries

- Allowed: `platformadmin` → `governance :: application` (ports/DTOs).
- Forbidden: `platformadmin` → `governance :: domain` / `:: infrastructure`.
- Unchanged: workspace HTTP, workspace handlers, leadcapture's existing governance edge.

## Alternatives considered

### Reuse workspace handlers via Mediator

- Description: `AdminTakedownController` sends `ApproveTakedownCommand`.
- Advantages: Less new code.
- Disadvantages: Handlers are `internal`, require workspace context, authorize workspace
  permissions, and return `TakedownReport`.
- Reason rejected: 403 plus aggregate leak; locked for #671.

### Events-only / duplicate domain in platformadmin

- Description: Copy takedown into platformadmin or drive it only with events.
- Advantages: No Modulith edge.
- Disadvantages: Duplicate state machine, emails, and asset suspension.
- Reason rejected: Proposal forbids domain duplication.

## Consequences

### Positive

- Back Office can operate globally without workspace scope.
- Modulith `verify()` still fails if domain types leak.
- Dual audit keeps workspace and admin trails distinct.

### Negative

- A second command path exists beside workspace handlers.
- ADR-0001's "MUST NOT depend on application" wording now has an explicit exception.

### Risks

- Future features might treat `governance :: application` as a dump for aggregates.
- Review size of the implementing change is likely over 400 lines.

### Accepted trade-offs

- Named-interface application dependency over events-only or domain duplication.

## Compliance and enforcement

- `platformadmin/ModuleMetadata.kt` `allowedDependencies` MUST include `governance :: application`.
- `ModularityVerificationTest` and `ModularStructureTest` remain blocking (ARCH-002).
- Hexagonal tests remain blocking (ARCH-001). No `TakedownReport` in platformadmin sources.

## Verification

- Modulith verify passes with the new allowlist and fails if `TakedownReport` is imported.
- Workspace takedown BDD/handlers remain green without edits to those handlers.

## Migration or remediation

Add the allowlist in the same change that introduces the ports. Do not add `governance :: domain`.

## Follow-up actions

- [ ] Record `Rel(platformadmin, governance, "Admin takedown ports")` in C4 component diagram during apply.
- [ ] Keep chained PRs if the implementing diff exceeds the 400-line review budget.

## Revisit conditions

- Takedown ownership moves out of governance.
- A second platformadmin consumer of governance appears; widen this ADR rather than adding a silent edge.
