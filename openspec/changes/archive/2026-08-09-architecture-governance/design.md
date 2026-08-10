# Design: Architecture Governance Contracts and Guidance

## Technical Approach

Deliver a documentation-and-agent-guidance change in three phases, with no production-code, dependency, or CI behavior change in the first phase. A single `architecture-governance` skill owns the shared `ARCH-001..005` vocabulary and routes contributors to language-specific guidance. Backend guidance composes the existing ArchUnit, Spring Modulith, and Konsist checks; frontend guidance describes deterministic boundaries for the existing Vue/Pinia, admin Vue, Astro, and `shared/web` surfaces. Canonical skills remain under `.agents/skills`; AgentSync distributes them to configured agents, so generated `.claude`, `.cursor`, `.codex`, `.gemini`, and `.opencode` trees are never edited directly.

## Architecture Decisions

| Decision | Choice | Alternatives / tradeoff |
|---|---|---|
| Contract ownership | `ARCH-001` layer/framework direction → ArchUnit/ADR-0002; `ARCH-002` bounded-context/module isolation → Spring Modulith/ADR-0001 and ADR-0002; `ARCH-003` aggregate-root boundary → Konsist/ADR-0015; `ARCH-004` cross-context identity-only references → Konsist/ADR-0016; `ARCH-005` value-object immutability and construction validation → Konsist/ADR-0017. | One cross-language checker is rejected because bytecode/module rules and TypeScript import rules have different semantics. |
| Backend composition | Retain all three tools and document one owner per concern. Avoid duplicating the two Modulith verification tests; future consolidation is a separate test-organization change. | Replacing Konsist with ArchUnit would lose source-shape rules and contradict ADR-0016's rationale. |
| Domain contracts | Explain the distinction between the marker `com.profiletailors.common.domain.AggregateRoot` and `com.profiletailors.common.domain.model.AggregateRoot<ID>`. Document `DomainEvent` as the existing interface (`eventVersion`, `occurredOn`) and `BaseDomainEvent`; do not invent a `DomainEvent` annotation. | Unifying marker and base class would break the deliberate ADR-0015 flexibility. |
| Frontend profiles | App guidance governs `src/modules/<feature>` public `index.ts` surfaces and `domain/application/infrastructure/presentation` where present. Admin keeps its flatter `views`/`stores` structure. Astro marketing remains static-first (`pages`, `components`, `layouts`, `scripts`, `i18n`); `shared/web` is a dependency-light cross-frontend contract package. | Forcing backend DDD folders onto TypeScript/Astro would create ceremony and invalidate current structure. |
| Command gate | Do not add or block CI on `just architecture-check` in this phase. Record a follow-up decision: first make it an opt-in `just` aggregator with contract-labelled output; promote it to CI only after a frontend checker and clean baseline are proven. | Immediate blocking gate risks hiding duplicated checks and failing valid frontend/admin imports. |

## Data Flow

```text
Contributor/agent → architecture-governance → backend-ddd or frontend-architecture
       │                    │                         │
       └── AgentSync ───────┴── guidance ──→ existing tests/checks
                                                   │
                               future opt-in `just architecture-check` → later CI decision
```

## File Changes

| File | Action | Description |
|---|---|---|
| `.agents/skills/architecture-governance/SKILL.md` | Create | Shared contract matrix, ADR links, ownership, exceptions, failure-message convention, routing, migration and gate policy. |
| `.agents/skills/frontend-platform/frontend-architecture/SKILL.md` | Create | Vue/Pinia app and admin rules, Astro/shared-web boundaries, approved composition-root exceptions, and baseline verification guidance. |
| `.agents/skills/backend-platform/ddd-architecture/SKILL.md` | Modify | Correct ADR path to `docs/architecture/adr/`, remove the annotation-based `DomainEvent` example, describe actual contracts/tools/tags, and use the `just` command hub. |
| `docs/architecture/adr/0001-*.md`, `0002-*.md`, `0015-*.md`, `0016-*.md`, `0017-*.md` | Reference only | Existing decisions remain authoritative; no duplicate ADRs are created. |
| `server/smp/src/test/...` architecture suites, `justfile`, CI, generated AgentSync targets | Deferred/reference only | Inventory and future gate inputs; do not modify in this phase. |

## Interfaces / Contracts

Backend contract owners are the five `ARCH` entries above. Frontend public boundaries are feature barrels such as `apps/web/app/src/modules/media/index.ts`; application composition roots may import those surfaces and infrastructure stores. `@profiletailors/shared-web` may be consumed by app and marketing, but `shared/web` must not import either application. Exceptions include approved shadcn compatibility paths and admin/marketing profile differences.

## Testing Strategy

| Layer | What to verify | Approach |
|---|---|---|
| Backend | Existing layer, component-scan, Modulith, and three Konsist DDD invariants remain green. | Run focused architecture tests through the existing `just` backend test recipes; compare deliberate violating fixtures and confirm ADR-labelled failures. |
| Frontend | Guidance matches current module relocation/public-barrel tests and does not reject admin or Astro structure. | Use existing app Vitest relocation/alias tests, app/admin type checks, Biome, and marketing Astro check; no new analyzer dependency yet. |
| Distribution | Canonical skills synchronize without editing generated targets. | Run the repository's AgentSync apply flow and inspect links/status; keep generated changes out of the governance diff. |

## Migration / Rollout

1. Establish a clean or isolated baseline, inventory current tests and deliberate violations, and exclude unrelated dirty DDD-sweep files.
2. Land the shared skill, frontend skill, and backend guidance revision only; run focused backend/frontend checks and AgentSync verification.
3. Separately evaluate an opt-in `just architecture-check`; require labelled failures, no duplicate Modulith execution, and a rollback path before any CI adoption. Roll back by reverting guidance files; existing enforcement remains untouched.

## Open Questions

- [ ] Which existing frontend import-analysis mechanism can enforce the first deterministic rule without adding a dependency?
- [ ] After baseline evidence, should the future opt-in gate become a CI-required check or remain developer-invoked?
