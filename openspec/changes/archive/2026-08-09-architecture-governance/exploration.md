## Exploration: architecture governance

### Current State

The repository already has three complementary backend architecture-enforcement mechanisms; it does not have a single missing ArchUnit/Modulith foundation that can simply replace the current DDD approach.

- **Layer boundaries are enforced with ArchUnit.** `server/smp/src/test/kotlin/com/profiletailors/smp/HexagonalArchTest.kt` imports production bytecode and verifies that domain code is framework-free, application code does not depend on infrastructure or Spring transport/persistence types, and bounded contexts expose the expected three layers. `ComponentScanArchTest.kt` adds Spring stereotype and component-scan rules.
- **Module boundaries are enforced with Spring Modulith.** `ModularStructureTest.kt` and `ModularityVerificationTest.kt` call `ApplicationModules.of(SmpApplication::class.java).verify()`. Bounded contexts carry `ModuleMetadata`/`@ApplicationModule`, for example `server/smp/src/main/kotlin/com/profiletailors/smp/governance/ModuleMetadata.kt`.
- **DDD/source-shape rules are enforced with Konsist.** `AggregateBoundaryTest.kt`, `IdentityOnlyAggregateCommunicationTest.kt`, and `ValueObjectImmutabilityTest.kt` scan the production source set and run under `@Tag("ddd-conformance")`. They enforce ADR-0015, ADR-0016, and ADR-0017 respectively.
- **The dependencies already exist.** `server/smp/build.gradle.kts` includes ArchUnit, Spring Modulith test support, and Konsist; `gradle/libs.versions.toml` pins `archunit = "1.4.2"`, `spring-modulith`, and `konsist = "0.17.3"`.
- **The domain contracts are real repository code, not only proposed markers.** `shared/common/src/main/kotlin/com/profiletailors/common/domain/AggregateRoot.kt` defines the marker annotation, while `shared/common/src/main/kotlin/com/profiletailors/common/domain/model/AggregateRoot.kt` is a separate base class with identity/event behavior. `shared/common/src/main/kotlin/com/profiletailors/common/domain/bus/event/DomainEvent.kt` is an event interface. ADR-0015 explicitly documents why the marker and base class are separate.
- **The current DDD skill is partly stale but not empty.** `.agents/skills/backend-platform/ddd-architecture/SKILL.md` is version `0.1` and correctly describes DDD invariants and the relationship to the hexagonal skill, but its examples prescribe Konsist and its ADR path example uses `docs/architecture/decisions/`, while accepted repository ADRs are under `docs/architecture/adr/`. Its marker example also presents a `DomainEvent` annotation, whereas the current shared event contract is the `DomainEvent` interface under `common.domain.bus.event`.
- **Frontend architecture is documented convention rather than executable governance.** `openspec/config.yaml` and `AGENTS.md` describe Vue feature modules, shared web libraries, and the Astro marketing surface. The app has feature-local `domain`, `application`, `infrastructure`, and `presentation` areas in places such as `apps/web/app/src/modules/analytics`, and `shared/web` contains reusable consent types/validation/storage. No dedicated frontend architecture test or architecture-check recipe was found in the inspected app/marketing areas.
- **There is no current unified command.** `justfile` exposes backend tests/lint/check and frontend checks, but no architecture-specific recipe or cross-stack architecture gate. Adding one would be new command behavior, not a rename of an existing gate.
- **The worktree is already dirty with unrelated DDD-sweep changes.** Current status includes modified identity/platformadmin domain files, new marker-coverage tests, and generated/synced agent directories. These changes must remain outside this change and must not be used as migration evidence without a clean-diff review.

### Contradictions in the User Proposal

1. **“Replace the marker/Konsist-oriented DDD skill with ArchUnit/Spring Modulith” conflicts with the current enforcement split and accepted ADRs.** ArchUnit is already the layer/import tool and Spring Modulith is already the module-boundary tool. ADR-0016 explicitly considered ArchUnit for the property-name identity rule and rejected it because the rule is source-shape-oriented; ADR-0015 and ADR-0017 likewise chose Konsist for source-level marker and value-object rules. A wholesale replacement would remove or weaken existing DDD invariants rather than align them.
2. **A single cross-stack rule implementation is not technically coherent.** Backend rules inspect Kotlin packages, bytecode, annotations, and Spring modules. Vue/TypeScript rules need to reason about feature-folder imports, public module barrels, composables, shared libraries, and possibly Astro boundaries. The common contract can be shared conceptually, but the enforcement mechanisms and failure messages must be language-specific. The proposal should not imply that ArchUnit/Spring Modulith can govern the frontend.
3. **“Shared `ARCH-001..005` contracts” is underspecified and risks duplicating existing ADRs.** The repository already has ADR-0015..0017 for aggregate boundaries, identity-only references, and value-object immutability, plus ADR-0002 for hexagonal architecture and existing Modulith verification. New ARCH identifiers need an explicit relationship to these decisions; otherwise two naming systems can express conflicting versions of the same invariant.
4. **A single architecture-check gate is not current state and cannot be treated as a documentation-only change.** It would alter command-hub and possibly CI semantics. The gate must define whether it aggregates existing backend tests only, includes frontend checks, or introduces new TypeScript/AST validation. It must also preserve the repository rule that all commands go through `just`.
5. **The proposal’s implied “current skill is wrong” is only partially true.** The skill has documentation drift and overreaches in places, but its core DDD responsibilities are already reflected in executable tests and accepted ADRs. The minimal correction is composition and synchronization, not deletion of the Konsist layer.

### Affected Areas

- `.agents/skills/backend-platform/ddd-architecture/SKILL.md` — reconcile stale paths, marker examples, commands, scope boundaries, and the relationship between Konsist, ArchUnit, and Modulith; do not overwrite existing DDD guidance until contracts are approved.
- `server/smp/src/test/kotlin/com/profiletailors/smp/HexagonalArchTest.kt` and `ComponentScanArchTest.kt` — existing ArchUnit layer/component enforcement and candidate owners for backend architecture contracts.
- `server/smp/src/test/kotlin/com/profiletailors/smp/ModularStructureTest.kt` and `ModularityVerificationTest.kt` — existing Spring Modulith verification; assess duplication before adding another module gate.
- `server/smp/src/test/kotlin/com/profiletailors/smp/AggregateBoundaryTest.kt`, `IdentityOnlyAggregateCommunicationTest.kt`, and `ValueObjectImmutabilityTest.kt` — existing Konsist DDD enforcement that must remain intact or be deliberately migrated with equivalent failing-case coverage.
- `shared/common/src/main/kotlin/com/profiletailors/common/domain/` and `shared/common/src/main/kotlin/com/profiletailors/common/domain/bus/event/` — canonical marker, base aggregate, and event contracts; these are sensitive shared-kernel areas and are not required for the exploration artifact itself.
- `docs/architecture/adr/0002-*.md`, `0015-*.md`, `0016-*.md`, `0017-*.md` — authoritative decisions that new `ARCH-001..005` identifiers must map to rather than silently supersede.
- `gradle/libs.versions.toml` and `server/smp/build.gradle.kts` — current architecture-test dependencies; migration must avoid unnecessary dependency churn and must check dependency policy before any new library is proposed.
- `apps/web/app/src/modules/**` — candidate frontend feature-module boundary scope for a separate TypeScript/Vue architecture contract; rules must respect existing `domain/application/infrastructure/presentation` usage and shared UI libraries.
- `apps/web/marketing/src/**` and `shared/web/**` — separate Astro/static and shared-web surfaces; they should not be forced into the app’s Vue module rules.
- `justfile` and CI workflow configuration — future unified gate integration; explicitly excluded from this exploration-only write and requiring a later proposal/spec decision.

### Minimal Phased Scope

1. **Contract reconciliation (proposal/spec prerequisite).** Define five architecture contracts as stable names, owners, scope, allowed exceptions, and failure semantics. Map each contract to existing ADRs and tests. Candidate ownership is: ArchUnit for layer/import direction, Spring Modulith for backend module boundaries, Konsist for Kotlin DDD/source shape, and a separate TypeScript/Vue mechanism for frontend module boundaries. Do not create duplicate ARCH contracts until this mapping is approved.
2. **Backend governance consolidation.** Update the backend governance documentation and test organization only as needed to make the existing three mechanisms discoverable and non-overlapping. Preserve equivalent coverage for ADR-0015..0017 and existing Hexagonal/Modulith checks. Prefer one canonical backend architecture test entry point or test suite over duplicating assertions.
3. **Frontend governance definition.** Specify a minimal, language-appropriate first rule set for `apps/web/app` and separately decide whether `apps/web/marketing` and `shared/web` are governed in the same phase. Start with import/boundary rules that can be tested deterministically; do not introduce a broad frontend rewrite or impose backend DDD markers on TypeScript.
4. **Gate integration.** After the contracts and test ownership are stable, add a `just`-managed architecture command that invokes existing and newly approved checks. Decide separately whether CI calls it directly or whether it remains part of existing backend/frontend checks. The gate must report which contract failed and must not hide unrelated test failures.
5. **Migration and evidence.** Run focused architecture tests, the relevant frontend checker, and the existing repository checks from a clean or explicitly isolated worktree. Compare the old and new failure cases before removing or deprecating any skill/test/dependency.

### Explicitly Out of Scope

- Modifying application/domain behavior, persistence schemas, APIs, UI behavior, or frontend module layout.
- Re-annotating all bounded contexts or completing the existing DDD marker sweep as part of governance-tool migration.
- Removing Konsist, the DDD tests, ADR-0015..0017, or the shared marker/base/event contracts before equivalent enforcement is proven.
- Replacing ArchUnit or Spring Modulith with a new backend framework, compiler plugin, or third-party architecture platform.
- Applying identical Kotlin rules to TypeScript/Vue/Astro code.
- Defining typed IDs, changing aggregate boundaries, tightening same-context exceptions, or refactoring repository DTO/materialization patterns.
- Rewriting or renaming existing ADRs without an explicit decision record and migration plan.
- Changing `justfile`, CI workflows, dependency versions, skills, or application code in this exploration phase.
- Touching unrelated uncommitted DDD annotations, marker-coverage tests, or generated agent/sync directories in the worktree.

### Approaches

1. **Composition and contract mapping (recommended)** — retain ArchUnit, Spring Modulith, and Konsist for their current strengths; define ARCH contracts as a stable governance vocabulary; add frontend-specific enforcement separately.
   - Pros: preserves accepted ADR enforcement, minimizes migration risk, matches actual repository architecture, and avoids forcing one tool across languages.
   - Cons: requires an explicit ownership matrix and may leave multiple test technologies visible.
   - Effort: Medium.

2. **Backend-only replacement with ArchUnit/Spring Modulith** — migrate the DDD rules into bytecode/module rules and retire Konsist.
   - Pros: fewer backend test technologies and a more centralized bytecode-oriented suite.
   - Cons: loses or weakens source-level property/marker/invariant checks; contradicts ADR-0015..0017 and ADR-0016’s rejected alternative; migration has high false-negative risk.
   - Effort: High.

3. **Single cross-stack architecture framework** — introduce one abstraction/tooling layer intended to enforce Kotlin and TypeScript rules uniformly.
   - Pros: one conceptual command and one governance surface.
   - Cons: likely reduces rules to the weakest common denominator, adds tooling/dependency cost, and obscures language-specific failure semantics; no evidence of a suitable existing repository tool.
   - Effort: High.

### Recommendation

Proceed to `sdd-propose` with a narrowed composition strategy. The proposal should treat architecture governance as a contract-and-gate change, not as a wholesale replacement of the DDD skill: keep Konsist for Kotlin source-level DDD invariants, keep ArchUnit for layer/import rules, keep Spring Modulith for backend module boundaries, and define a separate TypeScript/Vue enforcement track. Require the proposal to map each `ARCH-001..005` contract to ADR-0002 and ADR-0015..0017, identify the exact frontend scope, and define whether the future `just` gate is an aggregator or a new enforcement lane.

The next proposal should also include a migration inventory of current tests and deliberate violating fixtures, a rollback plan for any dependency/skill/gate change, and a clean-worktree verification rule so the existing DDD-sweep changes are not mixed into governance work.

### Risks

- A replacement migration can create a false sense of coverage while dropping source-level checks that ArchUnit cannot express reliably.
- Duplicate `ModularStructureTest`/`ModularityVerificationTest` and a new gate could run the same verification multiple times or produce inconsistent failure output.
- The current `ARCH-001..005` names are not yet defined; assigning them without ADR mapping can create contradictory governance contracts.
- Frontend boundary rules may be too strict for existing shared UI, Pinia, generated, or barrel-import patterns; they need a baseline and explicit exceptions.
- Kotlin/TypeScript parity may be mistaken for identical implementation, leading to either untestable rules or an overly weak common denominator.
- Konsist `0.17.3` is already a pinned dependency and its replacement would be a dependency migration requiring supply-chain review and compatibility verification.
- Existing uncommitted changes make failures and diffs harder to attribute; implementation must isolate or cleanly stage the governance change without discarding them.
- Adding a command and CI gate changes delivery behavior and must preserve all existing test/lint/BDD/E2E gates.

### Ready for Proposal

Yes, with the recommendation and contradictions above made explicit. The proposal should first approve the contract ownership matrix and phased scope; it should not promise a direct ArchUnit/Spring Modulith replacement or a single identical rule set for Kotlin and TypeScript.
