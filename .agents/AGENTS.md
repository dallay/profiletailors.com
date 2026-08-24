# AGENTS.md

> Engineering constitution and AI-agent instructions for the `profiletailors.com` monorepo.

This file is canonical at `.agents/AGENTS.md`. The root `AGENTS.md`, `CLAUDE.md`, `CODEX.md`,
`GEMINI.md`, and configured Copilot/OpenCode instruction targets are synchronized links. Edit the
canonical file only, then verify AgentSync status.

## Operating Contract

The repository values small, evidence-backed changes over speculative cleanup. These rules apply to
every agent unless a more specific nested instruction is present:

1. Inspect the current worktree, branch, relevant source, tests, configuration, ADRs, OpenSpec
   artifacts, and documentation before editing. Use `just -l` and existing skills instead of
   inventing commands or conventions.
2. Preserve unrelated working-tree changes. Never use reset, clean, checkout, or broad formatting
   to discard work you did not create.
3. Keep changes minimal, cohesive, reversible, and reviewable. Apply the Boy Scout Rule only to
   files directly implicated by the task; do not turn a focused change into a refactor.
4. Do not introduce a temporary architectural bypass. If an exception is unavoidable, name its
   scope, rationale, owner, test coverage, and removal condition, and record it in the relevant
   ADR or approved architecture artifact.
5. Do not commit or push unless the user explicitly asks. Autonomous maintenance agents under
   `.agents/agents/` follow their own repository maintenance framework and are the exception.
6. Report what was actually verified. Distinguish local results from GitHub Actions, remote, or
   deployed evidence; never describe an unrun check as passing.

## Fix Simplicity and Zero-Comment Policy

Fixes should make the system simpler, not more complex.

Prefer removing or consolidating code over adding a new layer, flag, or special case. If a fix
grows the system's surface area, look for the version that shrinks it.

Never leave comments in the repo. The standard is zero comments: no explanatory comments or
docblocks, TODO/FIXME notes, lint/type suppression directives, or commented-out code. Express
intent through names, structure, and tests; put rationale in commit messages or PR descriptions.
Interpreter shebangs are executable directives, not comments.

## Project Identity

**Profile Tailors** is a social media management platform for scheduling, publishing, analyzing,
engaging, and collaborating. The product name is **Profile Tailors**; `profiletailors.com` is the
domain only.

## Product Context and Source of Truth

Product truth uses inheritance: read `apps/web/PRODUCT.md` first, then the child `PRODUCT.md` for
the surface being changed. There is no root-level `PRODUCT.md` in this repository.

- `apps/web/PRODUCT.md` — shared web product truth
- `apps/web/marketing/PRODUCT.md` — public Astro marketing surface
- `apps/web/app/PRODUCT.md` — authenticated dashboard SPA
- `apps/web/admin/PRODUCT.md` — internal platform-admin SPA
- `shared/web/PRODUCT.md` — shared consent-contract package
- `tools/compliance/PRODUCT.md` — compliance validator CLI

Before design, copy, route, or product-behavior work:

- Read the applicable product files and `.agents/DESIGN.md`.
- Treat facts marked `Undecided`, planned, or future as deliberately open; never fabricate them.
- Keep marketing, dashboard, admin, and shared-web constraints separate. A shared package is not a
  place to move surface-specific UI or business behavior.

## Monorepo Structure

```text
apps/web/marketing/   # Astro 7 static-first marketing site (EN + ES)
apps/web/app/         # Vue 3 + Pinia dashboard SPA
apps/web/admin/       # Vue 3 + Pinia platform-admin SPA
server/smp/           # Spring Boot 4 backend (Kotlin, WebFlux, R2DBC)
shared/               # Kotlin shared modules and shared web contracts/assets
tools/compliance/     # TypeScript/Zod compliance validation CLI
docs/                 # Architecture, API, operations, compliance, and testing docs
openspec/             # Product contracts and spec-driven change artifacts
.agents/              # Canonical agent instructions, skills, commands, and automation
```

## Command Hub and Environment

Prefer repository recipes and start with `just -l`. Do not guess a recipe. `just` is the command
hub, but it does not currently expose every package-level app check; when a dedicated recipe does
not exist, use the exact workspace command already used by CI and say so in the verification report.

### Setup

| Command | Action |
|---|---|
| `just setup` | Create local env if needed, install dependencies, install hooks, sync agent targets, and set up optional tools |
| `just install` | Install all pnpm workspace dependencies from the frozen lockfile |
| `just hooks-install` | Install Lefthook hooks unless globally disabled |
| `just -l` | List the current command hub; treat this output as authoritative over this table |

### Frontend and shared web

| Command | Action |
|---|---|
| `just dev-frontend` | Start marketing and dashboard dev servers through Portless |
| `just frontend-build` | Build the marketing site |
| `just app-build` | Type-check and build the dashboard SPA |
| `just admin-build` | Type-check and build the admin SPA |
| `just frontend-lint` | Biome check for marketing only |
| `just frontend-format` | Format marketing only; do not use it as an unreviewed bulk rewrite |
| `just frontend-check` | Astro type/content check for marketing |
| `just frontend-test` | Marketing Vitest suite |
| `just admin-check` / `just admin-test` | Admin type-check / Vitest suite |
| `just frontend-test-e2e` | Marketing E2E plus the configured mocked app media lane |
| `just app-test-e2e-media-mocked` / `just app-test-e2e-media-real` | App Media Library E2E lanes |

For dashboard checks without a dedicated recipe, use the package scripts used by CI, for example
`pnpm --filter app lint`, `pnpm --filter app test:run`, and `pnpm --filter app type-check`.

### Backend and infrastructure

| Command | Action |
|---|---|
| `just backend-build` | Build the SMP backend artifact |
| `just backend-test` | Run backend tests; optionally pass excluded tags, e.g. `just backend-test 'postgres'` |
| `just backend-test-fast` | Run the backend test task through the repository password helper |
| `just backend-check` | Backend check including tests and Detekt, excluding the two BDD suites by design |
| `just backend-lint` | Run SMP Detekt |
| `just backend-lint-shared` | Attempt Detekt across shared modules |
| `just backend-bdd-fast` | Run the fast Cucumber suite |
| `just backend-test-postgres` | Run PostgreSQL integration tests |
| `just backend-bdd-postgres` | Run PostgreSQL BDD tests; use with `just infra-up` when required |
| `just infra-up` / `just infra-down` | Start / stop local infrastructure |
| `just production-smoke` | Verify production routing, migrations, data, secrets, and hardening |
| `just swarm-config` / `just production-config` | Validate rendered deployment configuration |

Backend tests that use Testcontainers require Docker; their PostgreSQL credential is defined by the
test fixture and does not come from `.env` or the shell. Never commit `.env` or any secret value.

### Local CI versus remote gates

- `just ci-local` runs the local fast pipeline, including staged Gitleaks, licence checks, frontend
  checks, backend checks, and builds.
- `just ci` adds the local BDD-fast and marketing E2E lanes.
- `just ci-full` adds PostgreSQL integration and PostgreSQL BDD tests after `infra-up`.
- GitHub Actions is authoritative for the remote gate. `.github/workflows/ci.yml` has the final
  `CI Gate` that requires every applicable check to be `success` or `skipped`, including lint,
  builds, backend tests, conditional BDD/PostgreSQL suites, and applicable E2E lanes.
- `.github/workflows/quality-gate.yml` adds coverage and SonarQube checks. The PR security workflow
  covers applicable Gitleaks, Semgrep, CodeQL, Trivy, and Biome security lanes.

Never infer that a local recipe proves a remote or deployed check. Record failed, passed, skipped,
or not-run status explicitly.

## Documentation and Contract Synchronization

Documentation is part of the implementation. A change is not complete when code works but the
repository's contracts, examples, or operational instructions are stale.

### Source-of-truth order

Use the artifact that owns the claim:

| Claim | Canonical owner |
|---|---|
| Product behavior and user-facing scope | Relevant `PRODUCT.md` and current `openspec/specs/` |
| Active change status and verification | `openspec/changes/<name>/state.yaml` and `verify-report.md` |
| Durable architecture decision or exception | `docs/architecture/adr/` and its index |
| Current system shape and dependencies | `docs/architecture/c4/` and architecture README |
| API behavior and media-type contract | Controllers/OpenAPI annotations, tests, clients, and `docs/api-versioning*.md` |
| Commands and CI behavior | `Justfile`, package manifests, Gradle tasks, and `.github/workflows/` |
| Operational, legal, security, and deployment behavior | The corresponding `docs/` runbook/register/configuration and source/configuration |

Code, configuration, migrations, routes, and executable tests are evidence of implemented behavior;
roadmaps, planned sections, issues, and stale prose are not.

### Change-impact checklist

For every non-trivial change, explicitly check whether it affects:

- `PRODUCT.md`, user copy, localized content, or `.agents/DESIGN.md`;
- an OpenSpec requirement, scenario, task, state, or verification report;
- an ADR, ADR index, C4 model, architecture enforcement document, or shared dependency graph;
- an API route, request/response schema, error contract, OpenAPI annotation, client, or the
  `Accept: application/vnd.api.v1+json` media-type contract;
- a database migration, environment variable, deployment file, production runbook, compliance
  inventory, privacy/consent contract, or security documentation;
- a command, package script, CI path filter, quality gate, or test tag.

Update impacted artifacts in the same change. If an artifact is intentionally not changed, state
why in the review summary. Never document planned behavior as implemented; use explicit `Planned`,
`Undecided`, or `Not implemented` wording instead.

### Documentation standards

- Documentation is English-only. Use lowercase `kebab-case.md`, except `README.md`.
- Prefer the structure `Overview → Changes → Usage → Troubleshooting → References` where it fits.
- Add new documents to the appropriate index and verify relative links.
- Do not rewrite historical ADR decisions to conceal drift. Correct current documentation or record
  the unresolved contradiction and propose a new ADR when intent is uncertain.
- Documentation-only changes do not need an OpenSpec cycle unless they alter a product or technical
  contract. Product behavior changes must reconcile the relevant OpenSpec artifacts before closure.

## Backend Architecture: Hexagonal Architecture

**Dependency rule:** `domain <- application <- infrastructure`.

Every new SMP bounded context follows:

```text
server/smp/src/main/kotlin/com/profiletailors/smp/<context>/
├── domain/          # pure business model, rules, ports, events, errors
├── application/     # framework-agnostic use cases, commands/queries, orchestration
└── infrastructure/  # Spring, WebFlux, R2DBC, HTTP, security, and external adapters
```

| Layer | May depend on | Must not depend on |
|---|---|---|
| Domain | Pure Kotlin and approved framework-free shared contracts | Application, infrastructure, Spring, R2DBC, Reactor, persistence annotations, or transport concerns |
| Application | Domain and inward-facing ports | Infrastructure, Spring stereotypes/configuration, HTTP, R2DBC, Reactor, or security transports |
| Infrastructure | Domain, application, and external frameworks | — |

Rules:

- Domain code owns business invariants and remains framework-free. Application code orchestrates
  use cases through ports. Infrastructure adapts HTTP, persistence, messaging, and providers.
- Package backend code as `com.profiletailors.smp.{context}.{layer}`. Follow the established CQRS
  vocabulary (`GetXQuery`, `{Verb}XCommand`, `XHandler`) and existing adapter naming; do not rename
  code solely to impose a new variant of the convention.
- Put repository/gateway contracts on the inward-facing side and implementations in infrastructure;
  follow the existing context convention rather than importing an adapter into application code.
- Use `com.profiletailors.common.domain.Service` for application services, not Spring `@Service`,
  `@Component`, or `@Repository`. `ModuleMetadata` and the explicitly accepted cross-cutting
  `com.profiletailors.smp.config` wiring exception contain no business logic.
- Controllers translate transport DTOs to application inputs and map outputs/errors back to the
  API. They must not contain business rules or call persistence adapters directly.
- Persistence entities, API DTOs, and provider schemas are infrastructure models. Map them to
  domain/application models; never make them the domain model.
- No handler or controller may bypass the application use case to call a repository or adapter.
  Composition roots and infrastructure configuration wire ports to adapters.
- Keep blocking I/O out of the reactive backend. Use suspend/coroutine and R2DBC patterns already
  established by the repository.
- Shared Kotlin foundations remain framework-free where their ADR says so. Consult
  `docs/architecture/shared/dependencies.md` before changing module dependencies.

The executable owners are `HexagonalArchTest.kt` and `ComponentScanArchTest.kt`. Do not weaken
those tests or add a second equivalent architecture suite. The current architecture governance
matrix and routing guidance live in `.agents/skills/architecture-governance/SKILL.md`.

## Backend Domain-Driven Design

DDD rules apply to Kotlin domain code, not Vue, TypeScript, Astro, or `shared/web`.

- Model aggregate boundaries deliberately. An aggregate root is the sole public entry point; keep
  internal entities inside the aggregate and protect invariants there.
- Cross-context references between marked aggregates/entities are identity-only (`Id`, `Ids`, or
  `Identifier`-shaped properties). Communicate through an approved public application contract,
  shared-kernel type, or domain/application event seam; never import another context's internal
  entity or infrastructure adapter.
- Prefer value objects for concepts with validation, formatting, units, or domain meaning instead
  of scattering primitive strings/UUIDs. Mark new value objects with `@ValueObject`, keep them
  immutable, and validate at construction or through an approved factory.
- Mark new aggregate roots and internal entities with the existing markers in the same change that
  introduces them. `com.profiletailors.common.domain.AggregateRoot` is a marker annotation and
  `com.profiletailors.common.domain.model.AggregateRoot<ID>` is a different base class; do not
  conflate them.
- Put business rules in domain entities, value objects, policies, and domain services—not in
  controllers, persistence mappers, or anemic application handlers.
- Use domain events for meaningful business transitions or cross-context facts when an event seam
  is the appropriate coupling. `DomainEvent` is an interface, not an annotation.
- Avoid accidental bounded-context coupling. A shared kernel is intentionally small and governed;
  do not use `shared/` as a dumping ground for feature code.
- New or changed DDD rules must remain traceable to ADR-0015, ADR-0016, ADR-0017 and their Konsist
  tests: `AggregateBoundaryTest.kt`, `IdentityOnlyAggregateCommunicationTest.kt`, and
  `ValueObjectImmutabilityTest.kt`.

Spring Modulith owns backend module boundaries through `ModularStructureTest.kt` and
`ModularityVerificationTest.kt`. Keep this distinct from layer rules and DDD source-shape rules;
do not add a duplicate `ApplicationModules.verify()` test.

## DRY, Cohesion, and Abstraction

Apply DRY pragmatically:

- Remove duplicated **knowledge or policy**, not every pair of similar lines.
- Prefer a small amount of local duplication over an abstraction whose semantics are not stable.
  Usually wait until a rule has repeated use and a clear owner before extracting it.
- Keep abstractions cohesive and named after domain intent. Do not create generic `shared/`, `utils/`,
  or `common` catch-alls, and do not put surface-specific behavior in shared packages merely
  because two call sites currently look alike.
- Do not unify DTOs, persistence models, or value types across bounded contexts when their meanings,
  validation, lifecycle, or ownership differ.
- When extracting shared code, document its stable contract, consumers, dependency direction, and
  tests. Delete the abstraction if it only hides duplication without reducing conceptual coupling.

## KDoc and TSDoc Contract

The zero-comment policy above is authoritative. Do not add KDoc, TSDoc, docblocks, inline comments,
TODO/FIXME/HACK notes, lint/type suppression directives, or commented-out code. Express public
contracts through names, types, structure, and tests. Generated code must be regenerated rather than
hand-edited.

## Frontend Architecture and Contracts

Use the profile-specific rules in `.agents/skills/frontend-platform/frontend-architecture/SKILL.md`:

- `apps/web/app/src/modules/<feature>` is the dashboard feature boundary. Cross-feature consumers
  use the feature's stable `index.ts` barrel; do not import another feature's infrastructure or
  private store directly.
- `apps/web/admin` intentionally remains a flatter, separate SPA. Do not force dashboard module
  folders or import dashboard/admin internals across apps.
- `apps/web/marketing` is static-first Astro. Keep route, component, layout, script, i18n, legal,
  and asset responsibilities separate; do not import dashboard or admin implementation.
- `shared/web` is framework-neutral and browser-safe. It may be consumed by all web surfaces but
  must not import from an application surface. Its consent versions and validation are a shared
  contract: test marketing and app consumers when changing it.
- Use locale files for bilingual copy. Spanish copy can be longer than English; do not compensate
  with fixed-width containers.
- Shared web assets live under `shared/assets/web/*` and are consumed through the configured alias.

## Backend API, BDD, and Testing

### API contract

- Keep controllers thin and use the existing SpringDoc/OpenAPI annotations and response/error
  conventions. Update client code, BDD/integration tests, `docs/api-versioning*.md`, and relevant
  examples when an endpoint or schema changes.
- Preserve the versioned media type `application/vnd.api.v1+json` unless an approved compatibility
  decision says otherwise. New endpoints must specify auth, workspace/tenant scope, validation,
  status codes, error shape, idempotency, and backward-compatibility behavior.
- Treat breaking changes as explicit decisions: provide a migration/compatibility path or record
  why the change is intentionally breaking. Do not silently change persisted or wire formats.

### Cucumber BDD

Every new user-visible backend feature, mediator command/query with externally observable behavior,
or endpoint MUST include Cucumber scenarios. Pure refactors, internal wiring, and documentation-only
changes still need the appropriate unit/architecture checks but do not require a synthetic BDD
scenario.

- Feature files: `server/smp/src/test/resources/features/{domain}-{entity}.feature`.
- Step definitions: `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/*BddSteps.kt`.
- Use `BddDatabaseSupport`; reset state before each scenario and use the injected `WebTestClient`.
- Use domain tag + `@smoke` + `@fast`; use `@postgres` only when the PostgreSQL variant is needed.
- Use `BddDatabaseSupport.USER_BEARER` (`Bearer valid-token`) and the repository's accepted test
  token prefixes (`valid-token`, `e2e-*`, `register-*`, `pending-*`, `verified-*`, `owner-*`).
- Set `Authorization`, `Accept: application/vnd.api.v1+json`, and `X-Workspace-Id` where the
  endpoint contract requires them. Use the repository's token fixtures, not real credentials.
- Run `just backend-bdd-fast` and, for PostgreSQL behavior, `just infra-up` followed by
  `just backend-bdd-postgres`.

### Tests by boundary

| Change | Minimum evidence |
|---|---|
| Domain/value object/policy | Pure unit tests, including valid and invalid invariants |
| Application/use case/port | Plain unit tests with fakes/mocks; verify orchestration and failure behavior |
| HTTP/persistence/provider adapter | Focused integration tests with `WebTestClient`, real serialization, and Testcontainers/real adapter behavior where it matters |
| New endpoint or user-visible backend behavior | Above tests plus Cucumber BDD scenarios |
| Frontend feature or shared web contract | Vitest, type-check/lint/build for each affected surface, and E2E for critical user flows |
| Architecture or dependency change | Existing ArchUnit, Spring Modulith, Konsist, Gradle, and package checks; do not replace a failing owner with a weaker check |

## Quality Gates and Definition of Done

Select gates based on the changed surface, then run the broader gate when the change crosses
surfaces or the user asks for full validation. Never skip a failing test to obtain a green result.

### Minimum gate map

- **Kotlin backend or shared Kotlin:** `just backend-check`; add `just backend-bdd-fast` for
  externally observable backend behavior, PostgreSQL suites for persistence changes, and
  `just backend-build` when packaging or dependencies are affected.
- **Marketing:** `just frontend-lint`, `just frontend-check`, `just frontend-test`,
  `just frontend-build`; add marketing E2E for route, interaction, legal, consent, or accessibility
  behavior.
- **Dashboard:** run the app lint, type-check, unit-test, and build scripts used by CI; add the
  relevant Playwright scheduler/media lane for critical flows.
- **Admin:** `just admin-check`, `just admin-test`, and `just admin-build`.
- **`shared/web`:** run its Vitest suite plus checks/builds for both marketing and dashboard
  consumers; include admin when its contract is consumed there.
- **Docs/agent-only:** validate links and formatting, inspect the exact diff, and run
  `pnpm dlx @dallay/agentsync status` when canonical agent files change. Do not claim product or
  runtime validation from a docs-only check.

### Agent Definition of Done

Before reporting completion, the agent must be able to answer yes to all applicable items:

- scope, worktree status, source-of-truth documents, and existing architecture/skills were inspected;
- implementation and tests follow the correct layer, bounded-context, and frontend-surface rules;
- KDoc/TSDoc, API/OpenAPI, product, ADR, C4, OpenSpec, operational, compliance, and examples were
  updated or explicitly ruled out with a reason;
- focused tests and quality gates were run, with exact Passed/Failed/Not run results recorded;
- no tests, assertions, security checks, or architecture rules were weakened or bypassed;
- the final diff is minimal, formatted, link-safe, free of secrets, and does not include unrelated
  worktree changes;
- local, CI, remote, and deployed evidence are clearly separated, with blockers and next steps
  stated honestly.

## Documentation, ADR, OpenSpec, and Dependency Policy

- Create or update an ADR for a durable cross-cutting architecture decision, a new dependency
  direction, a bounded-context exception, an API compatibility policy, or a material trade-off.
  Use `docs/architecture/adr/template.md`, add the record to `docs/architecture/adr/README.md`,
  and link relevant C4/OpenSpec evidence.
- Use OpenSpec for product behavior and significant spec-driven changes. Read the current spec and
  active change state before implementing; do not archive a change while its verification report or
  source spec is stale.
- Check dependency licence/score policy before adding a dependency. Prefer existing packages and
  Astro/Vue/Kotlin primitives. Check dependency scores with `socket-mcp_depscore` before adding a
  dependency when that tool is available. Keep `pnpm-lock.yaml` and Gradle/version-catalog changes
  consistent; never hand-edit generated dependency output.
- Use existing skills from `.agents/skills/` when available. Canonical skills are under
  `.agents/skills/`; `.claude/skills`, `.codex/skills`, `.cursor/skills`, `.gemini/skills`, and
  `.opencode/skills` are synchronized targets.

## Code Comments Policy

Comments are prohibited. Do not add explanatory comments or docblocks, TODO/FIXME notes, lint/type
suppression directives, or commented-out code. Interpreter shebangs are executable directives, not
comments.

## Key Gotchas

- **Canonical agent file:** edit `.agents/AGENTS.md`; verify symlink targets with AgentSync.
- **Gradle wrapper:** use `gradlew.bat` on Windows and `./gradlew` on POSIX; normally prefer `just`.
- **Spanish copy:** longer than English; never use fixed-width containers.
- **Portless:** marketing, dashboard, and admin use named local HTTPS URLs; read the relevant
  product/setup docs rather than guessing a host.
- **Marketing design:** the current public surface uses the Nothing-inspired dark theme; follow
  `.agents/DESIGN.md` rather than inventing a parallel visual language.
- **API versioning:** backend clients and BDD requests normally require
  `Accept: application/vnd.api.v1+json`.
- **Database tests:** PostgreSQL integration/BDD may require `just infra-up`.
- **Environment loading:** `bootRun` reads the root `.env`; Testcontainers test helpers use their
  fixed fixture credential without database password configuration.
- **Test tags:** `@Tag("postgres")`, `@Tag("bdd")`, and `@Tag("modularity")` classify suites; they
  must not be used to hide failures. `backend-check` excludes only the two BDD tasks by design.
- **Architecture checker:** there is no verified `just architecture-check` aggregator. Do not add
  one or make it CI-required without a separate proposal, clean baseline, labelled output, and
  non-duplicative enforcement plan.
- **Conventional commits:** `feat(scope):`, `fix(scope):`, `docs(scope):`, `chore(scope):` when
  commit creation is explicitly authorized.

## Consent Management

Consent spans marketing and dashboard through the framework-neutral `shared/web` package. Treat its
schema and version constants as a cross-surface contract.

### Shared layer (`shared/web`)

- `types/consent.ts` — `ConsentReceipt` and version/policy constants
- `validation/consent.ts` — Zod schema and `validateConsentReceipt()`
- `utils/consent-storage.ts` — `loadConsent()`, `saveConsent()`, `clearConsent()`
- `utils/detect-privacy-signals.ts` — DNT/GPC detection

### Contract and flow

- localStorage key: `pt-consent`
- receipt fields: `consentVersion`, `policyVersion`, `timestamp`, `region`, `categories`, `dnt`,
  and `source`
- source values: `banner` and `settings-panel`
- invalid or missing receipts mean no consent and must degrade gracefully by showing the banner
- the inline script validates localStorage and sets `window.__PT_CONSENT_ANALYTICS`
- analytics scripts must check `window.__PT_CONSENT_ANALYTICS` before loading
- DNT/GPC signals pre-disable the analytics toggle when detected

When the consent contract changes, increment `EXPECTED_CONSENT_VERSION` in
`shared/web/validation/consent.ts`, update the relevant `PRODUCT.md`/consent docs, and validate
marketing and dashboard flows. Old receipts should fail validation so the banner reappears.

The backend consent endpoints are `POST /api/governance/consent`,
`POST /api/governance/consent/withdraw`, `GET /api/governance/consent`, and
`GET /api/governance/consent/history`; maintain their documented coverage status rather than
assuming frontend consent tests prove backend behavior. Relevant E2E coverage lives at
`apps/web/marketing/tests/e2e/consent.spec.ts` and `apps/web/app/e2e/specs/consent.spec.ts`.

## References

- `CONTRIBUTING.md` — contribution and commit conventions
- `docs/README.md` — documentation index and standards
- `docs/architecture/README.md` — architecture overview
- `docs/architecture/adr/README.md` — ADR lifecycle and index
- `docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md` — hexagonal decision
- `docs/architecture/adr/0015-aggregate-root-as-sole-entry-point.md` — aggregate boundaries
- `docs/architecture/adr/0016-aggregates-communicate-by-identity-only.md` — identity-only references
- `docs/architecture/adr/0017-value-objects-are-immutable.md` — value-object invariants
- `docs/architecture/shared/dependencies.md` — shared-module dependency graph
- `openspec/README.md` — product contracts and change artifacts
- `.agents/skills/architecture-governance/SKILL.md` — ARCH-001..005 ownership and routing
- `.agents/skills/backend-platform/hexagonal-architecture/SKILL.md` — backend layer guidance
- `.agents/skills/backend-platform/ddd-architecture/SKILL.md` — Kotlin DDD conformance
- `.agents/skills/frontend-platform/frontend-architecture/SKILL.md` — frontend boundaries
