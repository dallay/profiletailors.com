# Design: R01 — Remove 3 UNUSED_PARAMETER Suppressions in PublishingProblemDetailsHandler

## Technical Approach

Rename-and-drop on 3 fixed-detail handlers in `PublishingProblemDetailsHandler`: rename each overloaded `handle` to a distinct name, then drop the unread `exception` parameter and its `@Suppress("UNUSED_PARAMETER")`. Bodies are constant `ProblemDetail.forStatusAndDetail(...)` + title, so output is byte-identical. Spring dispatch is annotation-driven (`@ExceptionHandler(X::class)` value carries the mapping), never method-name-driven, so routing survives the rename and the zero-arg form. A new web-slice test per handler locks exception → status/title (red pre-rename, green post-rename); existing handler unit tests updated to renamed call sites plus `publishing-publications.feature` form the regression net.

## Architecture Decisions

| Option | Tradeoff | Decision |
|--------|----------|----------|
| Rename-first (`handleProviderNotConfigured`, `handlePublicationNotFound`, `handleRecurringScheduleNotFound`) then drop param | 3 extra renames, but keeps JVM signatures distinct; follows hexagonal adapter-naming freedom (infrastructure method names, no callers) | **Chosen** |
| Drop param without rename (keep all named `handle`) | Zero-arg `handle()` × 3 → identical JVM signatures `handle()LProblemDetail` — kotlinc duplicate-declaration error, compiler-caught | Rejected — does not compile |
| Rename without drop (keep unused param, new names) | Compiles, preserves dispatch, but leaves the lint debt this batch exists to clear | Rejected — misses success criteria |
| Touch `detekt.yml` / `detekt-baseline.xml`, `TooManyFunctions` (`:35`), remaining 8 sites | Out of scope; baseline is ratchet-only, hand-untouched; rest is R02 | Rejected — see Non-goals |

## Data Flow

No runtime change. Spring `ExceptionHandlerMethodResolver` maps thrown exception → method via the `@ExceptionHandler` value attribute:

    throw ProviderNotConfiguredException ──→ @ExceptionHandler(ProviderNotConfiguredException::class) ──→ handleProviderNotConfigured(): ProblemDetail(503, "Provider not configured")
    throw PublicationNotFoundException ──→ @ExceptionHandler(PublicationNotFoundException::class) ──→ handlePublicationNotFound(): ProblemDetail(404, "Publication not found")
    throw RecurringScheduleNotFoundException ──→ @ExceptionHandler(RecurringScheduleNotFoundException::class) ──→ handleRecurringScheduleNotFound(): ProblemDetail(404, "Recurring schedule not found")

After the drop there is no exception parameter; Spring still routes because the mapping lives in the annotation value (confirmed: `:38`, `:70`, `:157` each declare the exception class explicitly). Zero-arg `@ExceptionHandler` methods are a supported Spring form — resolvable argument not required when the body needs no exception state.

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `server/smp/.../publishing/infrastructure/http/PublishingProblemDetailsHandler.kt:38-40` | Modify | `handle(exception: ProviderNotConfiguredException)` → `handleProviderNotConfigured()`; drop `:39` `@Suppress` |
| `.../PublishingProblemDetailsHandler.kt:70-72` | Modify | `handle(exception: PublicationNotFoundException)` → `handlePublicationNotFound()`; drop `:71` `@Suppress` |
| `.../PublishingProblemDetailsHandler.kt:157-159` | Modify | `handle(exception: RecurringScheduleNotFoundException)` → `handleRecurringScheduleNotFound()`; drop `:158` `@Suppress` |
| `server/smp/.../publishing/infrastructure/http/PublishingProblemDetailsHandlerTest.kt` | Modify | Update 3 call sites to renamed methods; add per-handler status/title/detail assertions (TDD red→green) |

## Interfaces / Contracts

No new types or APIs. New method shapes (bodies unchanged):

```kotlin
@ExceptionHandler(ProviderNotConfiguredException::class)
fun handleProviderNotConfigured(): ProblemDetail
@ExceptionHandler(PublicationNotFoundException::class)
fun handlePublicationNotFound(): ProblemDetail
@ExceptionHandler(RecurringScheduleNotFoundException::class)
fun handleRecurringScheduleNotFound(): ProblemDetail
```

Parameters that ARE read (`reason`, `denial`, `message`, `jobId`) are untouched.

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit (new, TDD) | Each renamed handler maps exception → expected status/title/detail | Web-slice test calls renamed method directly; write against new names first (red — unresolved reference), green after rename |
| Unit (existing) | No collateral breakage in `infrastructure/http` tests | `PublishingProblemDetailsHandlerTest`, `BulkPublishingProblemDetailsHandlerTest` green after call-site update |
| Static | Suppressions gone, no new findings | Lint-oracle pre-check (delete `@Suppress` → `just backend-lint` flags live); post-change `just backend-lint` PASS |
| Arch | Hexagonal direction intact | `just backend-check` PASS |
| E2E/BDD | Published-surface behavior unchanged | `publishing-publications.feature` green |

## Migration / Rollout

No migration required. Blast radius: 1 production file (≤3 hunks), 1 test file; Spring-dispatched methods have no Kotlin callers; no wire/API change. Rollback: revert rename + re-add dropped lines. Fallback: if the lint oracle proves a site dead-suppressed, convert that site to deletion-only with oracle evidence cited. Constraints: `detekt.yml`, `detekt-baseline.xml`, `shared/` untouched; zero new `@Suppress`.

## Open Questions

None — scope closed. Explicit non-goals: `TooManyFunctions` split, remaining 8 `UNUSED_PARAMETER` sites (R02), Identity (12) / Platform (4) handlers (later batches reuse this pattern).
