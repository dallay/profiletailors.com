# Proposal: Standardize `shared/observability` Usage

## Intent

`shared/observability` and the SMP `observability` bounded context exist and are partially
used, but no canonical contract, layered usage guide, redaction policy, correlation
convention, test matrix, or web boundary is documented. This records the **current state**
as observed and declares the **proposed norm** as documentation only — no code changes
ship here.

## Scope

### In Scope
- Document `shared/observability` (`OperationalEvent`, `OperationalEventSink`, `Severity`,
  `NoOpOperationalEventSink`, extensions) and `RequestOutcome`.
- SMP layering: domain → port; application → port; infrastructure → binding. No direct
  SLF4J/Micrometer/OTel from a handler.
- Web boundary: `shared/observability` is Kotlin-only; `apps/web/**` and `shared/web/**`
  do not import it.
- Redaction: sinks strip secret-like keys (`token`, `password`, `secret`, `authorization`,
  `cookie`, `set-cookie`, `pii`, `email`, `otp`) before emission.
- Errors: failures emit at handler boundary with `cause`. Correlation via Reactor `Context`
  + SLF4J MDC (absent today — see Risks).
- Evolution: `name` stable dotted; attributes additive; severity upgrades via ADR.
- Tests: unit for sinks; redaction unit for new sinks; integration through
  `OperationalEventPipelineBehavior`; correlation end-to-end test.
- `docs/README.md` pointer to this proposal.

### Out of Scope
- New sinks, OTel/Micrometer exporters, or replacing `Slf4jOperationalEventSink`.
- Implementing correlation-id propagation (follow-up change).
- Frontend telemetry or error-reporting SDK.
- Modifying `OperationalEvent`, `Severity`, or `RequestOutcome` API.

## Capabilities

### New Capabilities
None. Per `openspec/README.md`, a purely technical task must not create a standalone
capability. The change lives in OpenSpec because audit, redaction, and error semantics
touch privacy/security contracts referenced by other specs.

### Modified Capabilities
None.

## Approach

Point at existing files that already implement most rules; do not add or rename code. A
follow-up change will introduce correlation-id propagation and redaction enforcement as
test-first code.

| Topic | Current state (observed) | Proposed norm (this change) |
|---|---|---|
| Contract surface | `OperationalEvent`, `OperationalEventSink`, `Severity`, `RequestOutcome` | Documented as the only public surface |
| Backend layering | Mixed structured + convenience forms | Domain → port; application → port; infra → binding |
| Web boundary | No imports from `shared/observability` | Confirmed: Kotlin-only; no web consumer |
| Redaction | Not enforced | Sinks strip secret-like keys before emission |
| Errors | Pipeline emits `bus.request.completed`/`failed` | Keep; failures carry `cause`; emit at handler boundary |
| Correlation | `correlationId()` only in MCP tools | Centralize via Reactor `Context` + MDC (follow-up) |
| Evolution | No version rule | `name` stable; attributes additive; severity upgrades via ADR |
| Testing | Sink + pipeline + SLF4J tests exist | Add redaction + correlation tests (follow-up) |
| Canonical docs | SLO/SLI doc + `monitoring/*` runtime docs | Index entry pointing at this proposal |

## Affected Areas

| Area | Impact | Description |
|---|---|---|
| `shared/observability/**` | Reference only | Document existing API; no edits |
| `shared/common/.../observability/RequestOutcome.kt` | Reference only | Document value object |
| `server/smp/.../observability/**` | Reference only | Document SMP layered module |
| `docs/README.md` | Modified | Add observability usage pointer |
| `docs/observability-contracts.md` | Reference only | Clarify it covers SLO/SLI, not usage |

## Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| Drift between proposal and ad-hoc usage | Med | Follow-up enforces redaction and correlation |
| Frontend silently imports `shared/observability` | Low | ArchUnit/lint guard (follow-up) |
| Readers treat this as implemented guarantee | Med | "Current state" vs "Proposed norm" tables |
| Correlation-id gap remains unaddressed | Med | Tracked as explicit follow-up |
| Existing call sites leak secrets via `attributes` | Med | Disclosed; redaction in follow-up |

## Rollback Plan

Revert the `docs/README.md` pointer and delete
`openspec/changes/shared-observability-usage-standard/`. No runtime or build artifact is
touched.

## Dependencies

- `docs/observability-contracts.md` (existing SLO/SLI scope)
- `docs/monitoring/actuator-security.md`, `docs/monitoring/prometheus-grafana-setup.md`
- `openspec/specs/mcp-tool-audit` (audit vocabulary)
- `openspec/changes/archive/2026-09-06-dallay-562-administrative-audit-event-infrastructure`
  (audit redaction precedent)

## Success Criteria

- [ ] `proposal.md` exists with Current-state vs Proposed-norm tables.
- [ ] `state.yaml` reflects `current_phase: propose`.
- [ ] `docs/README.md` index points readers to this proposal.
- [ ] No source code, build files, or runtime configuration are modified.
- [ ] Follow-up work enumerated in Risks.