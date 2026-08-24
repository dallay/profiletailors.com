# Proposal: LinkedIn Company Pages Community Inbox — PR2 Foundation

## Intent

Deliver PR2 backend read foundations for LinkedIn Company Pages without activating Community
Management by default. Expose sync, calendar, and post-detail contracts while preserving
personal-profile OAuth and publishing.

## Scope

### In Scope

- Wire Spring-managed Mediator handlers with workspace context for sync, calendar, and post detail.
- Produce provider-neutral contracts plus only the minimal production ports, repositories, fake
  seams, and LinkedIn adapters needed for Page discovery and read-only post import.
- Enforce safe-off gates from approval evidence: Community Management approval, administrator role,
  required scopes, API version, and retention-policy version.
- Add executable tagged Cucumber scenarios for success, isolation, denial, safe defaults, and
  regressions.
- Preserve and regression-test `/v2/userinfo`, personal OAuth, and `RealLinkedInPublisher`.

### Out of Scope

- Community Inbox UI, comments/replies, webhooks, background polling, purge jobs, or write
  operations.
- Company Page OAuth changes, new dependencies, database migrations, or production enablement.
- Changes to personal-profile publishing semantics or OAuth state validation.

## Capabilities

### New Capabilities

- `social-content-sync`: Workspace-scoped Page discovery, bounded post sync, checkpoints, and read
  contracts.
- `community-inbox`: Read-only imported post/calendar/detail contracts and safe-off policy.

### Modified Capabilities

- `publishing`: Compatible social-content wiring without changing personal OAuth/publishing.
- `visual-calendar`: Backend calendar contract can surface imported Page posts.

## Approach

Keep domain/application contracts provider-neutral and route requests through Spring/Mediator
handlers. Resolve workspace-owned accounts before provider calls. Deny Community Management unless
every evidence gate passes; defaults keep discovery, import, inbox, replies, and sync disabled. Use
fakes for deterministic Cucumber coverage and leave personal LinkedIn adapter beans unchanged.

## Affected Areas

| Area                                       | Impact   | Description                                          |
|--------------------------------------------|----------|------------------------------------------------------|
| `server/smp/.../publishing/domain`         | Modified | Contracts, gates, ports, and read models.            |
| `server/smp/.../publishing/application`    | Modified | Mediator handlers and workspace isolation.           |
| `server/smp/.../publishing/infrastructure` | Modified | Spring wiring, minimal adapter, fakes, HTTP mapping. |
| `server/smp/src/test/resources/features`   | New      | Executable PR2 Cucumber scenarios.                   |

## Risks

| Risk                                           | Likelihood | Mitigation                                                   |
|------------------------------------------------|------------|--------------------------------------------------------------|
| Community operations activate without approval | Low        | All gates default off; test complete denial paths.           |
| Wiring regresses personal OAuth/publishing     | Med        | Preserve existing beans/routes; add regression scenarios.    |
| Provider/API/retention assumptions drift       | Med        | Validate evidence and isolate provider details behind ports. |

## Rollback Plan

Disable/remove PR2 Spring bindings and feature configuration, leaving personal OAuth/publishing
routes untouched. Revert the change without schema rollback because PR2 adds no migrations.

## Dependencies

- Existing workspace context, Mediator, connection repositories, credential resolver, and LinkedIn
  HTTP transport.
- Verified approval, scopes, administrator role, API version, and retention evidence before
  enablement.

## Success Criteria

- [ ] Sync, calendar, and post-detail endpoints resolve through Spring/Mediator with workspace
  isolation.
- [ ] Cucumber scenarios pass for reads, denial gates, safe defaults, and personal OAuth/publishing
  regressions.
- [ ] Default configuration keeps Community Management disabled and exposes no credentials.
