# Reactive calendar browser sync

## Route

Explicit SDD, OpenSpec-only persistence, strict TDD. Planning only in this phase; no application or backend source implementation is authorized yet.

## Objective

Make the authenticated scheduler converge quickly and safely across browser tabs, visibility changes, clock boundaries, timezone transitions, and concurrent editors without replacing REST as the calendar source of truth.

## Scope

1. Add a testable reactive clock and visibility-aware scheduler ticker.
2. Centralize visible-range extraction and date arithmetic using the existing `@internationalized/date` dependency and preserve the backend's exclusive upper-bound semantics.
3. Broadcast minimal, validated calendar invalidation messages with workspace isolation.
4. Coalesce invalidations into one calendar revalidation, reusing `fetchCalendar()` and its monotonic `latestCalendarFetchId` stale-response guard.
5. Harden authenticated `pt_publications` behavior so browser-local fallback data cannot cross workspace or authentication boundaries; retain only an explicitly bounded anonymous fallback if the existing product contract still requires it.
6. Expose publication `updatedAt` through the authenticated calendar and mutation contracts.
7. Detect a remote publication change while the editor is open without overwriting a dirty form; require an explicit user decision before retrying an update against a changed version.
8. Add Vitest coverage for range/time/broadcast/store/editor behavior and Playwright coverage for visible revalidation, cross-tab invalidation, and stale-editor protection.

## Explicit non-goals

- `publication-calendar-sse` and `openspec/specs/channel-events-sse/spec.md` are separate work and remain unchanged.
- No SSE endpoint, subscription, event schema, or server-side publication event stream.
- No new dependency; use existing Vue/Pinia, `@internationalized/date`, Vitest, and Playwright facilities.
- No broad scheduler redesign, provider changes, migration of unrelated local-storage keys, or optimistic form overwrite.

## Dependency order

1. Contracts and pure utilities: message schema, visible range, clock/ticker seams, and updatedAt DTO fields.
2. RED tests for each utility and contract boundary.
3. Minimal implementation of utilities and backend DTO/mapping changes.
4. Store invalidation/revalidation and authenticated local-cache hardening.
5. Scheduler lifecycle wiring and editor stale-state behavior.
6. Focused unit tests, then app type/lint/build and scheduler Playwright tests.
7. OpenSpec verification and QA; archive only after all required evidence is green.

## Strict-TDD rule

For every implementation task, add a failing test first, make the smallest implementation pass, then refactor only when the behavior is covered. Do not add suppressions, broaden mocks, weaken assertions, or bypass type/lint rules to make tests pass.

## Review work units

The change should be delivered as three dependency-ordered review units unless the implementation remains below the repository's practical review budget:

- Unit 1: calendar range/clock/broadcast contracts plus backend `updatedAt` contract and tests.
- Unit 2: publishing-store invalidation, coalescing, workspace/authenticated storage hardening, and unit tests.
- Unit 3: SchedulerView/CreatePostModal integration, Playwright scenarios, and final docs/verification.

## Gates

- `pnpm --filter app test:run` with focused files first.
- `pnpm --filter app lint`.
- `pnpm --filter app type-check`.
- `pnpm --filter app build` or repository-equivalent app build command.
- Backend focused publishing tests, then `just backend-check` because the API DTO/mapping is changed.
- Scheduler Playwright lane using the repository's configured command after the app is running.
- Final diff inspection and OpenSpec verification report. Remote CI and deployed behavior remain not-run unless separately evidenced.

## Risks to resolve during design/apply

- Local browser fixtures and existing E2E helpers write `pt_publications`; tests must be updated without restoring an unsafe authenticated fallback.
- A BroadcastChannel message is advisory and may be unavailable; the canonical REST fetch must remain authoritative.
- A dirty editor needs a deterministic conflict state and accessible decision UI; remote data must never silently replace typed content.
- The existing `PublicationResult` is reused by several command consumers, so adding an optional field must preserve compatibility while calendar responses expose a non-ambiguous version.
- Date ranges must not regress the backend's exclusive `to` bound or shift days through UTC conversion.
