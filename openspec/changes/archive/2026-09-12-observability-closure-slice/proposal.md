# Proposal: Close the Observability Boundary

## Intent

Finish the observability boundary without changing business failure behavior. A single shared decorator should own sanitization and best-effort isolation; production code should use structured events; sensitive-key matching should protect secrets without erasing ordinary operational context; durable documentation should describe the final contract and limits.

## Scope

### In Scope
- Centralize sanitization, ordinary-exception swallowing, and `CancellationException` propagation in `BestEffortOperationalEventSink`; make `Slf4jOperationalEventSink` an emission/formatting adapter only.
- Migrate every remaining production severity-helper call to named structured events, verify zero consumers, then remove helpers and `emitLegacy`.
- Narrow sensitive-key matching with regression tests for credential/token/privacy protection and safe keys containing ordinary terms such as `auth`.
- Update observability usage, contracts, architecture, and testing documentation.
- Follow strict TDD and the repository zero-comment policy; production code is not implemented in this phase.

### Out of Scope
- Correlation or request-context propagation.
- Distributed tracing, exporters, or a real OpenTelemetry adapter.
- Changes to business exception propagation, handler outcomes, or unrelated SLF4J logging.

## Capabilities

### New Capabilities
- None. This is a technical closure/refactoring change, not a product capability.

### Modified Capabilities
- None. No product-level requirements change; implementation contracts and documentation are reconciled in this change.

## Approach

Write failing focused tests first for decorator ownership, cancellation and business-failure semantics, structured migrations, helper absence, and sensitive-key boundaries. Convert the remaining SMP `media`, `identity`, and `privacy` handlers/jobs to explicit event names and attributes. Remove duplicate sanitizer/catch logic from the SLF4J adapter, then delete compatibility helpers only after a production-wide scan confirms zero consumers. Reconcile durable docs and run focused tests followed by the applicable backend checks.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `shared/observability` | Modified | Sink ownership, sanitizer matching, legacy API removal, tests |
| `server/smp/.../observability` | Modified | SLF4J adapter and pipeline tests |
| `server/smp/.../{media,identity,privacy}` | Modified | Structured-event call sites and tests |
| `docs/observability-*`, `docs/architecture/`, testing docs | Modified | Final contract and verification guidance |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Sanitization is too permissive or broad | Med | Test secret families, nested/case-insensitive keys, and ordinary `auth` boundaries. |
| Migration changes event shape or outcomes | Med | Preserve explicit event semantics and assert rethrow/cancellation behavior. |
| A deprecated consumer is missed | Low | Scan production imports/calls before deleting helpers. |

## Rollback Plan

Revert the focused change commits. If necessary, restore compatibility helpers and prior adapter behavior while retaining tests and documentation for a follow-up correction.

## Dependencies

- Existing shared observability and SMP fixtures.
- `openspec/config.yaml` strict-TDD policy and current observability documentation.

## Success Criteria

- [ ] Focused sanitizer, sink-isolation, cancellation, and business-failure tests pass.
- [ ] Zero production consumers remain before deprecated helper removal.
- [ ] Documentation states the final boundary and excluded capabilities accurately.
- [ ] No production comments, suppressions, or unrelated behavior changes are introduced.
