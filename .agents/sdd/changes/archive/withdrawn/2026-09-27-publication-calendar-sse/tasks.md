# Tasks: Publication Calendar SSE

## Review Workload Forecast

| Field | Value |
|---|---|
| Review budget | 400 changed lines unless project configuration says otherwise |
| Estimated workload | High; backend domain + port + adapter + controller + worker wiring + SPA subscriber + Playwright |
| Chained PRs recommended | Yes |
| Chain strategy | github-stacked-prs or dependency-ordered feature branches |
| Work-unit balance | Unit 1 owns the publication domain event + port + RED tests; Unit 2 owns the backend adapter + controller + worker wiring + tests; Unit 3 owns the SPA subscriber + Playwright + docs |
| Decision needed before apply | Yes — confirm the three-unit delivery shape and the SSE auth strategy against `IdentitySecurityConfiguration` |

### Suggested Work Units

| Unit | Goal | Likely review slice | Dependencies |
|---|---|---|---|
| 1 | Pure domain event contract + publisher port + RED tests | Small domain slice | None |
| 2 | Reactor adapter + controller + worker + handler wiring + focused tests | State/infrastructure slice | Unit 1 |
| 3 | SPA `subscribePublicationEvents` + revalidation wiring + Playwright + docs | User-visible integration slice | Units 1–2 |

## Phase 1: Contracts and RED tests

- [ ] 1.1 RED: backend publishing domain tests for `PublicationEvent` value semantics and
      `PublicationEventType` enum exhaustiveness (`CREATED, UPDATED,
      RESCHEDULED, DELETED, STATUS_CHANGED` mapped to `publication.created`,
      `publication.updated`, `publication.rescheduled`, `publication.deleted`,
      `publication.status-changed`).
- [ ] 1.2 GREEN: create
      `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/PublicationEventContracts.kt`
      with `PublicationEvent`, `PublicationEventType`, and the
      `PublicationEventPublisher.publish(event)` port.
- [ ] 1.3 RED: backend unit tests proving the publisher port contract: one method
      `publish(event)`; blank `workspaceId` or blank `publicationId` is rejected.
- [ ] 1.4 GREEN: minimal port implementation only in the test fixtures; no production
      wiring yet.

## Phase 2: Backend infrastructure and worker wiring

- [ ] 2.1 RED: backend unit test for `ReactorPublicationEventPublisher`: emissions
      are delivered to a subscriber filtered by workspace id; `directBestEffort`
      semantics are observed; backpressure drops are accepted.
- [ ] 2.2 GREEN: create
      `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/events/ReactorPublicationEventPublisher.kt`
      mirroring the channel-events adapter.
- [ ] 2.3 RED: backend unit test for
      `PublicationEventStreamRegistry.stream()` returning the underlying
      `Flux<PublicationEvent>`.
- [ ] 2.4 GREEN: register the registry bean alongside the publisher.
- [ ] 2.5 RED: backend controller test for
      `PublishingPublicationSseController.streamEvents()` proving
      workspace filter, active-membership denial for non-members,
      heartbeat cadence, and event-name mapping to the dotted `changeType`
      wire values.
- [ ] 2.6 GREEN: add the controller with `Flux.merge(events, heartbeats)` and
      register the SSE route under
      `GET /api/publishing/publications/events` with
      `MediaType.TEXT_EVENT_STREAM_VALUE`.
- [ ] 2.7 RED: backend handler test for
      `CreatePublicationHandler` / `EditPublicationHandler` /
      `DeletePublicationHandler` (decided Option A: single atomic delete
      boundary, then emit) / `CancelPublicationHandler` /
      `ReschedulePublicationHandler` / `RetryPublicationHandler` proving that
      the publisher emits only after the transaction boundary completes
      successfully and only with the right wire `changeType` per the frozen
      emitter matrix in `design.md`.
- [ ] 2.8 GREEN: wire `PublicationEventPublisher` into each handler and call
      `publish` only on the success path after the transaction boundary
      returns. Publisher failure after commit never fails the business
      operation (best effort, no status change, no retry of the business op).
- [ ] 2.9 RED: backend handler test for rolled-back transactions
      asserting the publisher is never called.
- [ ] 2.10 GREEN: leave the publish call inside the success path only; document
      the invariant.
- [ ] 2.11 RED: backend worker test for `PublishingJobExecutor.executeClaim`
      proving the publisher receives `publication.status-changed` events for
      normal success, recovered success (`finalizeRecoveredSuccess`),
      terminal failure, reconnect block, the `AMBIGUOUS_OUTCOME` branch,
      and blocked recovery requeue; plus a test proving retryable attempts
      with no publication-visible state change emit zero events.
      `PROCESSING` reactivity is explicitly out of scope until its
      persisting write site is identified.
- [ ] 2.12 GREEN: wire the publisher into the executor and emit post-commit.
- [ ] 2.13 RED: backend worker test for rolled-back job execution asserting
      the publisher is never called.
- [ ] 2.14 GREEN: confirm guards in the executor success branches.
- [ ] 2.15 RED: backend Modulith tests
      (`ModularStructureTest`, `ModularityVerificationTest`) confirming the
      new infrastructure symbols respect package boundaries.
- [ ] 2.16 GREEN: extend `ModuleMetadata` named interfaces if needed; document
      the new `PublicationEventPublisher` and `PublicationEventStreamRegistry`
      as published names.

## Phase 3: SPA subscription and revalidation

- [ ] 3.1 RED: extend `apps/web/app/src/modules/publishing/infrastructure/publishing.store.test.ts`
      with a subscriber test for `subscribePublicationEvents(onInvalidation)`
      that simulates SSE frames and asserts:
      - workspace match → `onInvalidation` invoked with the parsed event;
      - foreign workspace frame → ignored;
      - heartbeat frame → no callback.
- [ ] 3.2 GREEN: add `subscribePublicationEvents(onInvalidation)` and
      `unsubscribePublicationEvents()` to the publishing store as a pure
      input adapter (no coordinator or URL-range knowledge inside the
      store); expose `publicationEventsConnected` state.
- [ ] 3.3 RED: extend the SchedulerView/composition test with a duplicate-signal test: same
      mutation arrives via SSE and `BroadcastChannel` within the coordinator
      window (plus a second SSE frame); the test asserts at most one
      coalesced `fetchCalendar` call, never one-per-frame.
- [ ] 3.4 GREEN: route the parsed SSE invalidations through the SchedulerView
      composition into the existing coordinator surface (`request(range)`);
      do NOT introduce a parallel fetch path.
- [ ] 3.5 RED: `SchedulerView` test asserting that the SSE subscription is
      established on mount and torn down on unmount.
- [ ] 3.6 GREEN: wire `subscribePublicationEvents` into the SchedulerView
      lifecycle; remember to call `unsubscribePublicationEvents` on
      `onUnmounted`.
- [ ] 3.7 RED: add a workspace-switch test that asserts the previous
      subscription is aborted before the new one can deliver anything, and
      a new subscription starts for the new workspace.
- [ ] 3.8 GREEN: bind the workspace switch watcher to abort/replace the
      subscription.
- [ ] 3.9 RED: add SSE-disconnect tests asserting bounded backoff reconnect
      (`1s → 2s → 5s → 10s → max 30s + jitter`) while visible, no aggressive
      reconnect while hidden, and at most one coalesced `fetchCalendar` call
      on reconnect plus an immediate canonical revalidation on
      `visibility → visible` with subscription restart.
- [ ] 3.10 GREEN: implement the bounded reconnect through the same
      coordinator; do NOT introduce a new fetch path.

## Phase 4: Acceptance and documentation

- [ ] 4.1 RED: update `apps/web/app/e2e/fixtures/scheduler-mocks.ts` so the
      route-backed mock exposes a `text/event-stream` handler that emits a
      `publication.status-changed` frame after a route-backed mutation, with
      no `BroadcastChannel` equivalent posted.
- [ ] 4.2 GREEN: implement the SSE route-backed mock.
- [ ] 4.3 RED: extend `apps/web/app/e2e/specs/scheduler-browser-sync.spec.ts`
      with an SSE-only scenario: the event is provoked from the route-backed
      SSE/backend fixture (never via `BroadcastChannel`); tab B refetches
      `/calendar` and renders the new state without manual F5.
- [ ] 4.4 GREEN: implement the Playwright test, asserting backend fetch counts.
- [ ] 4.5 RED: update `docs/compliance/data-inventory.{md,yaml}` to describe
      the new SSE surface (no new personal data category is introduced, but
      the workspace-scoped stream must be noted).
- [ ] 4.6 GREEN: reconcile the inventory entry; bump schema version if
      needed.
- [ ] 4.7 RED: focused backend tests + Vitest + Playwright smoke before any
      broad gate.
- [ ] 4.8 GREEN: run `pnpm --filter app lint`, `pnpm --filter app type-check`,
      app build, focused backend tests, `just backend-check`, and the
      scheduler Playwright lane; record exact outcomes.
- [ ] 4.9 RED: confirm `channel-events-sse` was not modified and `sse_scope`
      is now `in_scope` for this change only.
- [ ] 4.10 GREEN: produce the verify report and the QA report; archive only
      after both report paths and the relevant CI lane are green.

## Completion Criteria

- [ ] All requirements in the durable spec
      (`openspec/specs/calendar-publication-sse/spec.md`) have at least one
      passing test or an explicitly recorded unavailable evidence.
- [ ] Strict-TDD order is visible in the implementation history or apply
      report: RED, GREEN, then refactor.
- [ ] No new source comments, suppressions, unsafe casts, explicit `any`,
      weakened tests, or configuration bypasses.
- [ ] No database migration is added because the existing `updated_at` column
      remains authoritative.
- [ ] Local, CI, remote, and deployed evidence are distinguished in the
      verify and QA reports.
- [ ] `reactive-calendar-browser-sync` files were not modified by this
      change beyond its own scoped edits.
- [ ] `channel-events-sse` artifacts and behaviour remain intact.
