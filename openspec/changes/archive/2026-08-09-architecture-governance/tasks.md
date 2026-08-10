# Tasks: Architecture Governance Contracts and Guidance

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 250–380 |
| 400-line budget risk | Medium |
| Chained PRs recommended | No |
| Suggested split | Single PR: contract skill, backend/frontend guidance, verification evidence |
| Delivery strategy | ask-on-risk |
| Chain strategy | single-pr |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: single-pr
400-line budget risk: Medium

### Suggested Work Units

| Unit | Goal | Likely PR | Notes |
|------|------|-----------|-------|
| 1 | Define ARCH contracts and ADR ownership | PR 1 | Shared skill; no duplicate ADRs |
| 2 | Align backend and frontend guidance | PR 1 | Canonical `.agents/skills` only; no generated targets |
| 3 | Verify baseline and gate decision | PR 1 | Evidence-only; `architecture-check` remains deferred |

## Phase 1: Contract Foundation

- [x] 1.1 Establish a clean or explicitly isolated baseline; record that modified DDD annotations/marker-coverage tests and all untracked `.agents/skills/impeccable`, `.claude`, `.codex`, `.cursor`, `.github/*`, and `.impeccable` paths are excluded.
- [x] 1.2 Create `.agents/skills/architecture-governance/SKILL.md` with exactly `ARCH-001..005`, owner, scope, ADR mapping (ADR-0001/0002 and ADR-0015..0017), exceptions, severity, and failure-message metadata.
- [x] 1.3 Map each contract to existing backend suites and deliberate fixtures without changing assertions: ArchUnit (`HexagonalArchTest`, `ComponentScanArchTest`), Modulith (`ModularStructureTest`, `ModularityVerificationTest`), and Konsist (`AggregateBoundaryTest`, `IdentityOnlyAggregateCommunicationTest`, `ValueObjectImmutabilityTest`).

## Phase 2: Guidance Tracks

- [x] 2.1 Modify `.agents/skills/backend-platform/ddd-architecture/SKILL.md`: correct `docs/architecture/adr/`, describe marker `AggregateRoot` versus `AggregateRoot<ID>`, use the `DomainEvent` interface/BaseDomainEvent, and document ArchUnit/Modulith/Konsist ownership and `just` commands.
- [x] 2.2 Create `.agents/skills/frontend-platform/frontend-architecture/SKILL.md` covering Vue feature barrels and prohibited cross-module infrastructure imports, flatter admin `views`/`stores`, Astro `pages`/`components`/`layouts`/`scripts`/`i18n`, `shared/web`, and approved shadcn/generated exceptions.
- [x] 2.3 Keep this phase documentation/guidance-only: do not alter production code, dependencies, ADR files, backend architecture assertions, `justfile`, CI, or frontend layout; any test consolidation or new analyzer is deferred.

## Phase 3: Gate Decision and Verification

- [x] 3.1 Record `just architecture-check` as unverified, opt-in follow-up; require contract-labelled output, preserved unrelated failures, no duplicate Modulith execution, and a separate CI decision after frontend tooling/baseline evidence.
- [x] 3.2 Run `just backend-test-fast`, `just backend-check`, `just frontend-lint`, `just frontend-check`, `just frontend-test`, `just admin-test`, `just admin-check`, and `pnpm --filter app type-check`; run `just ci-local` only for final integration evidence.
- [x] 3.3 Run `pnpm dlx @dallay/agentsync apply`, inspect synchronization/link status, and remove/revert generated target changes so only canonical skill files remain in the diff.
- [x] 3.4 Review the final diff/status to confirm excluded DDD-sweep files and unrelated tooling directories remain untouched; document rollback as reverting guidance files while retaining existing enforcement.
