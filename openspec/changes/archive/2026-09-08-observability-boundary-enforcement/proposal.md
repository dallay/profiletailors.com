# Proposal: Observability Boundary Enforcement

## Intent

`shared/*` domain/application leaks SLF4J and Jackson, breaking hexagonal purity and ADR-0010. Route signals through `OperationalEventSink` and enforce with tests.

## Scope

### In Scope
- Per-module import bans in existing `*ArchTest` plus one new `presentation`/`spring-boot-common` test; blocking, migration-first with fail-then-pass
- Migrate 4 storage warns to `emit(WARN, ...)` with defaulted `NoOpOperationalEventSink`; preserve swallow-and-continue and `CancellationException` rethrow; update ~24 test sites
- Move `RHSFilterParser` out of `..domain..` next to its factory; Jackson leaves domain
- New contract: publish-failure events carry `operation`/`provider`/sanitized-`bucket` only; `key`/payloads never emitted
- Sync `dependencies.md`, `observability-usage.md`, `observability-contracts.md`

### Out of Scope
- Sink redaction hardening (follow-up)
- Legacy `info`/`warn`/`error` deprecation (follow-up)
- Common aggregator module; `just architecture-check`
- Metric/SLO renames

## Capabilities

> Researched `openspec/specs/`: no observability capability exists; platform-governance, no user-facing behavior.

### New Capabilities
- None

### Modified Capabilities
- None

## Approach

1. Migrate storage warns to `emit` with dotted names (e.g. `storage.operation.event.publish.failed`), key-safe attributes, captured-event tests.
2. Move parser package; fix factory/tests; stop logging full query map.
3. Enable bans last; allowlist `..infrastructure..` SLF4J/Micrometer owners.
- Assumption: rests on `exploration.md`; `research.md` blocked. `strict_tdd: true` untouched. `chain_strategy` undecided — tasks-phase input.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `shared/storage/.../application/StorageApplicationService.kt` | Modified | 3 warns to `emit` + sink injection |
| `shared/storage/.../application/GeneratePresignedUrlUseCase.kt` | Modified | 1 warn to `emit` + sink injection |
| `shared/presentation/.../domain/presentation/filter/RHSFilterParser.kt` | Moved | Out of `..domain..` |
| `shared/spring-boot-common/.../presentation/filter/RHSFilterParserFactory.kt` | Modified | Import rewrite |
| `shared/*ArchTest` + new presentation test | Modified/New | Import bans, blocking |
| `shared/storage/build.gradle.kts`, `shared/presentation/build.gradle.kts` | Modified | Acyclic observability edge |
| `docs/observability-usage.md`, `docs/observability-contracts.md`, `docs/architecture/shared/dependencies.md` | Modified | Names and edge |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Bans redden CI on undiscovered SLF4J | Med | Migration first; allowlist infrastructure owners |
| `emit` copies `bucket`/`key` leak to attributes | Med | `key` never emitted; caller discipline until sink hardens |
| Narrow ban leaves Jackson-in-domain | Low | Ban covers Jackson in domain |

## Rollback Plan

Revert reverse-order: bans, warns, parser move, Gradle edge. Each step compiles; no data migration.

## Dependencies

- `exploration.md` only; no external dependency.

## Success Criteria

- [ ] No SLF4J/Jackson in `shared/*` domain/application; bans green
- [ ] Stable dotted events with `operation`/`provider`/sanitized-`bucket`, no `key`; swallow-and-continue kept
- [ ] Parser outside `..domain..`; factory and tests pass
