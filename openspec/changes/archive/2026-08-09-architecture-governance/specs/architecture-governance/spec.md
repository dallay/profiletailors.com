# Architecture Governance Specification

## Requirements

### Requirement: ARCH contracts have explicit ownership

Governance MUST define exactly five contracts: `ARCH-001` layer/import direction, `ARCH-002` backend module boundaries, `ARCH-003` aggregate-root entry points, `ARCH-004` identity-only aggregate communication, and `ARCH-005` value-object immutability. Each MUST have one owner, scope, ADR mapping, exception policy, and failure semantics. They MUST complement ADR-0002 and ADR-0015..0017.

#### Scenario: Inventory and failures are attributable

- GIVEN the governance inventory or a check result is reviewed
- WHEN `ARCH-001..005` or a violation is inspected
- THEN the identifier MUST have required metadata, and the result MUST name its owner, scope, ADR, and severity

### Requirement: Backend and frontend scopes remain separate

Governance MUST define backend Kotlin and frontend TypeScript/Astro scopes separately. Backend rules MAY inspect packages, bytecode, annotations, Spring modules, and Kotlin source; frontend rules MUST use language-appropriate boundaries. Kotlin DDD markers MUST NOT be required in frontend code.

#### Scenario: Scope-specific checks run

- GIVEN checks run for `server/smp`, `apps/web/app`, `apps/web/marketing`, or `shared/web`
- WHEN a rule fails
- THEN it MUST identify the applicable surface and owner, and MUST NOT evaluate files outside that scope
- AND Vue rules MUST NOT be imposed on Astro or shared-web code without approval

### Requirement: Current DDD enforcement remains compatible

The model MUST preserve equivalent coverage for ADR-0002 and ADR-0015..0017. ArchUnit MUST own layer/import rules, Spring Modulith MUST own backend module boundaries, and Konsist MUST remain available for Kotlin DDD invariants. Existing tests, tools, contracts, and ADRs MUST NOT be weakened without equivalent failure evidence.

#### Scenario: Ownership and replacement are verifiable

- GIVEN a maintainer seeks enforcement or proposes replacing a DDD check
- WHEN governance is inspected or the change is reviewed
- THEN the responsible check and ADR MUST be identifiable, and focused old/new failure comparison MUST precede removal

### Requirement: ADR enforcement metadata is mandatory

Each ARCH contract MUST state its relationship to mapped ADRs. Metadata MUST identify executable owner, covered paths, approved exceptions, and whether failure is blocking, warning, or unverified.

#### Scenario: Exceptions are observable

- GIVEN a contract or deliberate fixture is reviewed
- WHEN its metadata or check result is inspected
- THEN its ADR relationship, owner, scope, rationale, and expected result MUST be explicit
- AND an approved exception MUST NOT appear as an unobserved pass

### Requirement: Frontend module boundaries are deterministic

Guidance MUST define testable ownership and import boundaries for `apps/web/app/src/modules/**`, including public entry points and no direct imports of another module's infrastructure internals. It MUST separately describe flatter admin areas, `apps/web/marketing/src/**`, and `shared/web/**`, with shared-UI and generated shadcn-vue exceptions.

#### Scenario: Feature and shared imports are governed

- GIVEN Vue code consumes another feature or code belongs to admin, Astro, shared-web, or generated UI
- WHEN imports are checked
- THEN feature code MUST use an approved entry point or adapter, while the applicable profile or exception MUST prevent out-of-scope rejection
- AND feature code MUST NOT use another module's infrastructure internals

### Requirement: Architecture checks are phased and initially unverified

Governance MUST proceed through contract reconciliation, backend alignment, frontend definition, and gate integration. Until command semantics, tooling, and baseline failures are verified, a proposed `just architecture-check` MUST be labeled unverified, MUST NOT alter CI, and MUST NOT replace existing gates. A verified gate MUST report included contracts, preserve unrelated failures, and retain rollback capability.

#### Scenario: Verification controls delivery

- GIVEN architecture-check tooling is unverified or verified
- WHEN repository or architecture gates run
- THEN unverified checks MUST NOT be CI-required; verified checks MUST report each contract and owner, preserve equivalent coverage, and allow rollback before current enforcement is removed
