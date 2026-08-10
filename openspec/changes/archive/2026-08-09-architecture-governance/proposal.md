# Proposal: Architecture Governance Contracts and Guidance

## Intent

Establish a minimal, language-appropriate architecture-governance model for the monorepo. The current repository already enforces different concerns with ArchUnit, Spring Modulith, and Konsist, while the DDD skill has documentation drift and frontend conventions are mostly implicit. This change makes ownership and contracts explicit without replacing working enforcement prematurely.

## Goals and Non-Goals

### Goals
- Define shared `ARCH-001..005` governance contracts, ownership, exceptions, and mappings to ADR-0002 and ADR-0015..0017.
- Align Kotlin `ddd-architecture` guidance with actual `AggregateRoot<ID>`, `DomainEvent`, ArchUnit, Spring Modulith, and Konsist coverage.
- Define separate architecture guidance for the Vue/Pinia app, flatter admin app areas, and Astro marketing/shared-web surfaces.
- Establish a verified path for a future `just architecture-check` follow-up.

### Non-Goals
- Do not delete Konsist, existing DDD tests, ADRs, shared domain contracts, or working ArchUnit/Modulith checks.
- Do not change application behavior, APIs, schemas, UI layout, module placement, dependencies, CI, skills, `justfile`, or unrelated tooling.

## Scope

### In Scope
- Contract ownership matrix and migration inventory for existing architecture tests and deliberate violating fixtures.
- Backend and frontend guidance updates as separate tracks.
- Phased verification and rollback criteria.

### Out of Scope
- Current uncommitted DDD annotation/marker-coverage changes and unrelated untracked tooling directories (`.agents/skills/impeccable`, `.claude`, `.codex`, `.cursor`, `.github/*`, `.impeccable`) are explicitly outside this proposal.
- A cross-language enforcement framework or wholesale replacement of backend tools.

## Capabilities

### New Capabilities
- `architecture-governance-contracts`: Stable `ARCH-001..005` contracts mapped to existing ADRs and enforcement owners.
- `backend-ddd-governance`: Kotlin guidance and discoverability for ArchUnit layer rules, Modulith module rules, and Konsist DDD/source-shape rules.
- `frontend-architecture-governance`: Separate deterministic boundary guidance for Vue/Pinia, flatter admin areas, Astro marketing, and `shared/web`.

### Modified Capabilities
- None. Existing `quality-gates` and `frontend-modularization` requirements remain unchanged until a later approved gate or migration proposal.

## Phased Delivery and Approach

1. **Contract reconciliation:** approve definitions, ADR mappings, ownership, exceptions, and failure semantics for `ARCH-001..005`; avoid duplicate contracts.
2. **Backend alignment:** update guidance/test organization only as needed; preserve equivalent ADR-0015..0017, Hexagonal ArchUnit, component-scan, and Modulith coverage.
3. **Frontend definition:** document a small baseline for actual app and marketing boundaries; do not impose Kotlin DDD markers on TypeScript.
4. **Gate follow-up:** only after command semantics and tooling are verified, propose `just architecture-check` as an aggregator or new enforcement lane; CI integration requires a separate decision.

Migration uses a clean or explicitly isolated worktree, focused old/new failure-case comparison, and no removal until equivalent coverage is demonstrated.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `docs/architecture/adr/`, `server/smp/src/test/` | Modified | Contract mapping and backend test ownership. |
| `.agents/skills/backend-platform/ddd-architecture/SKILL.md` | Modified | Correct paths, contracts, tools, and commands. |
| `apps/web/app/src/modules/`, `apps/web/marketing/src/`, `shared/web/` | Modified | Separate frontend guidance scope. |
| `justfile`, CI | Follow-up only | No changes in this proposal. |

## Acceptance Criteria

- [ ] Each `ARCH-001..005` contract has one owner, ADR mapping, scope, exception policy, and failure semantics.
- [ ] Existing ArchUnit, Modulith, and Konsist checks remain discoverable and their baseline coverage is preserved.
- [ ] Backend and frontend guidance describe different enforcement mechanisms and actual repository structure.
- [ ] No excluded dirty-worktree files are included.
- [ ] A later gate proposal is explicitly blocked until command semantics and tooling are verified.

## Risks and Rollback

| Risk | Likelihood | Mitigation |
|---|---|---|
| Contract duplication or lost DDD coverage | Medium | ADR/test inventory and failing-case comparison before removal. |
| Frontend rules reject valid shared or admin patterns | Medium | Start with a measured baseline and explicit exceptions. |
| Future gate changes CI behavior | Medium | Separate follow-up proposal with verified command semantics. |

Rollback by reverting documentation/test-organization changes; retain all existing enforcement and defer any gate or dependency change until separately approved.

## Open Decisions

- Exact meaning and ADR mapping of each `ARCH-001..005` identifier.
- Whether admin areas share app rules or need a narrower profile.
- Whether marketing and `shared/web` are enforced in the first frontend phase.
- Whether `just architecture-check` aggregates existing checks or adds new analyzers.
