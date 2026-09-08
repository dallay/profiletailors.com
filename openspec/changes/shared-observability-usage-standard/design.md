# Design: Shared Observability Usage Standard

## Technical Approach

Create one canonical, English-language guide at `docs/observability-usage.md`. It documents the
implemented Kotlin contracts separately from the proposed usage norm, so readers never mistake a
policy for runtime enforcement. The guide links back to the OpenSpec change, source files, tests,
hexagonal architecture, shared-kernel governance, C4 observability context, and the existing SLA/SLI
catalog. `docs/README.md` becomes the entry point; `docs/observability-contracts.md` remains the
owner of SLA/SLO/SLI targets, not event-usage rules.

## Architecture Decisions

| Decision | Alternatives considered | Rationale |
|---|---|---|
| Use a single canonical usage guide | Split contract, security, and testing across several new docs | One reviewable norm reduces drift; sections preserve clear ownership boundaries. |
| Record current state and norm side by side | Document only the desired future state | The proposal explicitly forbids claiming deferred behavior is implemented. |
| Keep `shared/observability` Kotlin-only and framework-free | Add web telemetry adapters or shared TypeScript types | Preserves the shared-module boundary and prevents backend framework concerns crossing into `apps/web` or `shared/web`. |
| Treat correlation and sink redaction as explicit gaps | Imply support from MCP-only correlation or current SLF4J output | Prevents unsafe operational assumptions; implementation belongs to a follow-up change. |

## Data Flow

```text
Domain/application handler
        -> observability port / OperationalEventSink
        -> infrastructure binding (Slf4jOperationalEventSink)
        -> operational logger / existing monitoring pipeline

Mediated request -> OperationalEventPipelineBehavior -> started/completed/failed events
```

The guide will explain that handlers preserve the original failure as `cause` and rethrow it; it
will not describe correlation propagation as available outside the MCP-specific helper.

## File Changes

| File | Action | Description |
|---|---|---|
| `docs/observability-usage.md` | Create | Canonical module reference and project-wide usage norm. |
| `docs/README.md` | Modify | Replace the proposal-only observability link with the canonical guide and retain the OpenSpec link as change evidence. |
| `docs/architecture/shared/dependencies.md` | Modify | Add `:shared:observability`, its framework-free contract, and SMP consumer relationship to the dependency catalog. |
| `openspec/changes/shared-observability-usage-standard/state.yaml` | Modify | Mark `design` complete and advance `next` to `tasks`. |
| `docs/observability-contracts.md`, `docs/architecture/c4/*.md`, ADR-0002, ADR-0010 | Review only | Link or cross-reference where useful; no duplicate SLA or architecture contract is created. |

## Interfaces / Contracts

The guide contains a contract table for:

- `OperationalEvent(name, severity, message?, attributes, cause?)`.
- `OperationalEventSink.emit(event)`, `NoOpOperationalEventSink`, structured `emit` extension,
  and legacy severity helpers.
- `Severity`: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`.
- `RequestOutcome`: `SUCCESS` or `FAILURE` from `shared:common`.
- SMP `MetricsHook`, `RateLimitHook`, `ObservabilityHookRegistry`, pipeline behavior, and SLF4J
  sink as adapters, not shared contracts.

A safe example uses a stable dotted name and low-cardinality identifiers, for example
`media.asset.preview.failed` with `workspaceId` and `assetId`; it must not include token, password,
secret, authorization, cookie, set-cookie, PII, email, or OTP values. The guide states that names
are stable and dotted, attributes are additive and low-cardinality, and severity changes require an
ADR.

## Testing Strategy

| Layer | What to document | Evidence / planned coverage |
|---|---|---|
| Shared unit | Structured and legacy event preservation | `OperationalEventSinkTest`; current evidence. |
| SMP unit/integration | Severity rendering, start/completion/failure, cause preservation and rethrow | `Slf4jOperationalEventSinkTest`, `OperationalEventPipelineBehaviorTest`; current evidence. |
| Redaction unit | Every future sink excludes sensitive keys and values | Normative follow-up; not currently enforced. |
| Correlation E2E | Request correlation through Reactor Context and MDC | Deferred follow-up; no current guarantee. |
| Boundary checks | No web import; no handler dependency on SLF4J/Micrometer/OTel | Documented matrix and architecture/source review. |

## Migration / Rollout

No runtime migration or feature flag. Publish the guide, update navigation and the shared dependency
catalog, then use it as the review standard. Redaction enforcement and centralized correlation are
separate follow-up changes. Existing mixed direct logging remains a disclosed gap, not silently
reclassified as compliant.

## Open Questions

- [ ] Assign the permanent documentation owner and reviewer from the Principal Architect/platform group.
- [ ] Define the follow-up change owner and rollout plan for sink redaction and Reactor Context + MDC correlation.
