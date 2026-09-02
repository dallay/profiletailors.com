# Proposal: Stabilize Local CI PostgreSQL Tests

## Intent

Keep required PostgreSQL coverage in the ordinary backend test lane while removing two proven
sources of instability. Testcontainers reports PostgreSQL as started before its mapped host port
accepts connections; Liquibase then fails with `Connection refused`. Manual Docker PostgreSQL works,
so no broader root cause is claimed. Independently, a MockK fluent-spec helper omits
`bind(String, Int)` and hangs the bulk-import repository test.

## Scope

### In Scope

- Keep `@Tag("postgres")` tests in the ordinary Gradle/CI lane; do not add PostgreSQL exclusions to
  `just ci`, `.github/workflows/ci.yml`, or `.github/workflows/quality-gate.yml`.
- Make shared `PostgresTestContainerSupport` use a bounded, condition-based readiness check against
  the mapped host endpoint before JDBC, R2DBC, or Liquibase access. Prefer an explicit host-port/JDBC
  probe or compatible Testcontainers wait strategy; do not add arbitrary sleeps. Preserve cleanup.
- Under TDD, stub `bind(String, Int)` and add regression coverage in
  `R2dbcBulkImportJobRepositoryTest`.
- Update `docs/testing/test-tags-and-env.md` to state that ordinary tests include PostgreSQL-tagged
  tests and that a dedicated PostgreSQL task/job may still run intentionally.

### Out of Scope

- Testcontainers dependency upgrades; the `2.0.5` core versus `1.21.4` modules mismatch remains a
  risk for a later bounded experiment.
- Workflow edits unless implementation evidence proves they are required, production behavior,
  frontend changes, and any Option A/B exclusion workaround.

## Capabilities

### New Capabilities

None. This is test-harness, CI-lane, and testing-documentation work.

### Modified Capabilities

None. No product or `openspec/specs/` capability requirement changes.

## Approach

Implement Option C: repair readiness at the shared Testcontainers boundary, not by hiding tagged
coverage. Add the MockK regression first, then the smallest helper fix. Verify focused tests, the
ordinary lane, and the dedicated PostgreSQL lane while checking lifecycle cleanup. Leave workflow
files unchanged unless a proven implementation need emerges. Recommend an ADR only if the readiness
contract and ordinary-lane PostgreSQL policy are confirmed as durable repository architecture; until
then, keep the rationale in this change and the testing guide.

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `server/smp/src/test/.../PostgresTestContainerSupport.kt` | Modified | Bounded mapped-port readiness before database use. |
| `server/smp/src/test/.../R2dbcBulkImportJobRepositoryTest.kt` | Modified | MockK overload stub and regression test. |
| `docs/testing/test-tags-and-env.md` | Modified | Actual ordinary/dedicated lane contract. |
| `just ci`, CI workflow files | Protected | No tag-exclusion workaround. |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Readiness still races or times out | Med | Bounded retries, useful diagnostics, and focused/ordinary evidence. |
| Ordinary tests need Docker and take longer | Med | Preserve coverage and retain intentional dedicated verification. |
| Mixed Testcontainers versions hide compatibility issues | Med | Do not change versions; isolate a later experiment. |

## Rollback Plan

Revert the readiness and MockK test-harness changes and documentation together. Keep PostgreSQL in
the ordinary lane during rollback; do not restore tag exclusion as a workaround.

## Success Criteria

- [ ] Ordinary `:server:smp:test` evidence shows PostgreSQL-tagged tests execute successfully.
- [ ] Mapped-port readiness completes before Liquibase; strategy is bounded and condition-based, with no arbitrary sleep.
- [ ] MockK regression completes within its timeout and verifies `bind(String, Int)`; existing coverage remains.
- [ ] Testing documentation makes no false exclusion claim, and workflow diffs remain empty unless proven necessary.
