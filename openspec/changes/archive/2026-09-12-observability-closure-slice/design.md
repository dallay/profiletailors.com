# Design: Observability Closure Slice

## Technical Approach

Keep `OperationalEventSink` framework-free and make `BestEffortOperationalEventSink` the single safety boundary. It sanitizes once, invokes the delegate, rethrows `CancellationException`, and swallows ordinary `Exception` failures so telemetry cannot alter business outcomes. `Slf4jOperationalEventSink` becomes an SMP adapter that formats an already-safe event and maps `Severity` to SLF4J; it does not sanitize or catch. Migrate the seven remaining SMP caller groups, then remove deprecated helpers only after a production zero-consumer scan. Correlation propagation, tracing, exporters, real OpenTelemetry, and unrelated SLF4J logs remain excluded.

## Architecture Decisions

| Decision | Choice | Alternatives | Rationale |
|---|---|---|---|
| Safety ownership | Shared decorator owns sanitization and ordinary-failure isolation; SLF4J adapter only emits | Duplicate protection; adapter-owned safety | One boundary protects every adapter and avoids divergent behavior while keeping shared code vendor-neutral. |
| Self-diagnostics | No diagnostic event on sink failure | Re-enter the same sink; direct fallback logger; new metrics port | Same-sink reporting can recurse; any fallback adds work or a new dependency. Dropping telemetry is safer than affecting business execution. Revisit only with a separately injected, bounded diagnostic channel. |
| Redaction matcher | `lowercase(Locale.ROOT)` plus explicit sensitive key segments/suffixes; unconditional `argument.` redaction | Arbitrary substring fragments; regex over every value; caller-only redaction | Protects token/password/authorization/api-key/cookie/credential/otp/email/pii families without redacting ordinary keys containing `auth`; deterministic and dependency-free. Sensitive string values are also replaced in messages. |
| Event schema | Stable dotted names; lower-camel-case, bounded scalar attributes; causes only on observable failures | Free-form legacy messages; new correlation fields | Names are searchable and testable without introducing a cross-cutting correlation contract. |

## Data Flow

```text
application port -> OperationalEvent -> BestEffortOperationalEventSink
                         sanitize -> delegate -> Slf4j format -> SLF4J
                         ordinary Exception: stop; CancellationException: rethrow
```

The sanitizer converts an attached cause to safe `errorType` metadata and clears the throwable before the adapter sees it. The conditional SMP bean remains `BestEffortOperationalEventSink(Slf4jOperationalEventSink())`; custom sinks remain injectable.

## File Changes

| File | Action | Description |
|---|---|---|
| `shared/observability/.../BestEffortOperationalEventSink.kt` | Modify | Centralize safety and preserve cancellation; no diagnostic path. |
| `shared/observability/.../OperationalEventSanitizer.kt` | Modify | Implement the explicit, locale-stable matcher and retain argument/cause/message safety. |
| `shared/observability/.../OperationalEventSink.kt` | Modify | Remove helpers and `emitLegacy` after migration. |
| `server/smp/.../Slf4jOperationalEventSink.kt` | Modify | Formatting and severity dispatch only. |
| `server/smp/.../media/application/*.kt` | Modify | Migrate `AssetPreviewUrlResolver`, `MediaAssetBackfillJob`, `MediaHandlers`, and `StaleAssetReconciler`. |
| `server/smp/.../identity/application/CloseAccountHandler.kt` | Modify | Migrate lifecycle events without identity or confirmation values. |
| `server/smp/.../privacy/application/*.kt` | Modify | Migrate closure and expiry events with bounded attributes and failure causes. |
| Existing shared/SMP observability and affected media/identity/privacy tests | Modify | TDD proof of ownership, matcher boundaries, structured schemas, unchanged outcomes, and cancellation. |
| `docs/observability-usage.md` | Modify | Final ownership, no-diagnostic policy, matcher, naming, attributes, and zero-consumer state. |
| `docs/observability-contracts.md` | Modify | Reconcile sanitizer/decorator/adapter guarantees. |
| `docs/architecture/shared/dependencies.md` | Modify | Record the framework-free contract and adapter direction. |
| `docs/architecture/adr/README.md` and a new observability ADR | Modify/Create | Record and index sink ownership, no diagnostics, and redaction trade-offs. |
| `docs/testing/test-tags-and-env.md` | Modify if needed | Add focused-to-full command sequence only if not kept in the usage guide. |

## Interfaces / Contracts

```kotlin
fun interface OperationalEventSink { fun emit(event: OperationalEvent) }
class BestEffortOperationalEventSink(private val delegate: OperationalEventSink) : OperationalEventSink
class Slf4jOperationalEventSink : OperationalEventSink
```

Caller convention: `name = "<context>.<resource>.<action>[.<outcome>]"`; attributes are bounded scalars such as `assetId`, `workspaceId`, `scanned`, `failed`, `durationMs`, and `reason`. Never include credentials, email addresses, signed URLs, request payloads, or unbounded user text. Migrations use `media.asset.*`, `media.backfill.*`, `media.gc.*`, `media.expiration.*`, `media.preview.*`, `identity.account_closure.*`, `privacy.account_closure.*`, and `privacy.expiry.*`, preserving severity and meaning rather than message-derived names.

## Testing Strategy

| Layer | Coverage | Approach |
|---|---|---|
| Shared unit | Matcher, nested/case-insensitive secrets, safe `auth` keys, argument redaction, cause metadata, delegate failure, cancellation | Failing Kotlin tests first with recording/throwing sinks; assert delegate input and caller-visible exceptions. |
| SMP unit | Adapter formatting/severity and migrated caller event shapes | Existing JUnit tests with recording sinks; assert business results remain unchanged. |
| Integration/architecture | Conditional bean wiring, vendor-neutral shared boundary, zero legacy production imports/calls | Existing architecture/modularity owners plus repository scan. |
| Full | Backend regression | Focused classes, then `just backend-test-fast`, then `just backend-check`; add BDD/Postgres gates when the touched path requires them. |

Strict sequence: failing tests, minimal implementation, affected tests, zero-consumer scan, focused shared/SMP tests, fast backend suite, then applicable full gates. Report local evidence separately from CI.

## Migration / Rollout

No data migration, flag, or staged rollout. Revert the focused commits if needed; no persisted or wire contract changes are introduced.

## Open Questions

- [ ] Assign the next ADR number when the implementation creates the record.
- [ ] Choose one owner for focused-test command documentation to avoid duplication.
