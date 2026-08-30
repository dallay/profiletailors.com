# Exploration: DALLAY-414 — Recurring Posts Automation

## Current State

### Branch & Linear Reality

- **Branch `feature/dallay-414-featscheduling-recurring-posts-automation`** (worktree `ptflow`) is tracked against `origin/main` at `56a6340b` with **zero divergent commits** — `git diff origin/main --stat` empty, `git log origin/main..HEAD` empty. It is a placeholder that has not received work since creation. Locally it is `up to date with 'origin/main'` with clean working tree.
- **Linear DALLAY-414** shows `In Progress since 2024-07-31, 3 pts, should-have, no blockers` — stale for ~13 months by wall-clock (2024-07-31 → 2026-08-30). History aligns exactly with **PR #552 `feat: Add recurring post scheduling across publishing` merged 2026-07-31T11:55:08Z** (commits `a653e607` → `b94fdfcd`). That PR is the real delivery vehicle, not the `feature/dallay-414-*` branch. The naming mismatch (`feat/add-recurring-post-scheduling` vs `feature/dallay-414-*`) explains why git history for `414` is empty.

### Publishing Recurring Context — Fully Shipped (v1)

**Domain** `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/RecurringModels.kt:9`:
- `RecurrenceFrequency { DAILY, WEEKLY, MONTHLY }`, `RecurringScheduleStatus { ACTIVE, PAUSED, CANCELLED }`
- `RecurrenceRule(frequency, interval, daysOfWeek, dayOfMonth, endDate, maxOccurrences)` with strict `init` invariants (weekly requires daysOfWeek, monthly requires dayOfMonth, interval>0, dayOfMonth 1..31, days 0..6). `occurrences(start:ZonedDateTime, until:LocalDate)` with `daily/weekly/monthlyOccurrences` capped by `maxOccurrences` and `min(endDate, until)`. Weekly uses `previousOrSame(MONDAY)` + day offsets; monthly skips invalid days (e.g. Feb 31) instead of clamping.
- `RecurringSchedule(id, workspaceId, createdBy, templatePostId, recurrenceRule, timezone, nextScheduledAt, status, createdAt, updatedAt)` — no `@AggregateRoot` marker (intentional: lightweight schedule header, not full aggregate).

**Repository** `RecurringScheduleRepository.kt` + `R2dbcRecurringScheduleRepository.kt`:
- `create/update/findByWorkspaceAndId/findByWorkspace/pauseByTemplatePost/delete`. `findByWorkspace` ordered `next_scheduled_at NULLS LAST, created_at DESC`. `pauseByTemplatePost` does `UPDATE status=PAUSED WHERE template_post_id=:id AND status=ACTIVE`. `delete` is soft `UPDATE status=CANCELLED WHERE id=:id AND status<>CANCELLED` returning boolean. `write()` handles nullable `dayOfMonth/endDate/maxOccurrences/nextScheduledAt` via `bindNullable`, insert vs update paths, workspace-scoped `WHERE id=:id AND workspace_id=:workspaceId`.

**Application** `application/RecurringScheduleHandlers.kt` (204 lines):
- `CreateRecurringScheduleHandler`: requires `requireEmailVerification(SCHEDULE_POST)`, `requireWorkspaceContext`, loads template via `publicationRepository.findByWorkspaceAndId(workspaceId, templatePostId)` — requires `status in {PUBLISHED, SCHEDULED}` — validates `ZoneId.of(timezone)`, `startsAt >= now+1s`, `occurrences(start, until)` where `until = endDate ?: start+30d`, requires non-empty occurrences, builds `RecurringSchedule(recur-UUID, ACTIVE, next=firstOccurrence)`, then `transactionRunner.runAtomically { scheduleRepository.create; occurrences.map { PublicationLifecyclePolicy.queue(DRAFT clone with same provider/socialAccountId/bodyText/assetIds/priority, scheduledFor=occurrence) → publicationRepository.createDraft + publicationJobRepository.enqueue(replacementJobFor(...)) } }`.
- `ListRecurringSchedulesHandler`, `UpdateRecurringScheduleHandler` (recomputes `nextScheduledAt` from new rule/timezone/startsAt), `DeleteRecurringScheduleHandler` (email verification + delete).
- Gap: creation is **one-shot expansion** within a single transaction for up to `maxOccurrences` or 30-day horizon. No background worker to generate beyond horizon, no `maxOccurrences` rollover, no holiday skip.

**Infrastructure HTTP** `infrastructure/http/PublishingControllers.kt:463`:
- `RecurringScheduleController @RequestMapping("/api/v1/workspaces/{workspaceId}/recurring")` with `POST /` (create), `GET /` (list), `PATCH /{id}`, `DELETE /{id}` — all call `requireWorkspacePath(workspaceId)` validating `path workspaceId == ResourceContext workspaceId` else `PublicationValidationException`. Uses `RecurringScheduleRequest.toCreateCommand()` parsing `frequency.uppercase() -> RecurrenceFrequency`. Patch builds partial `RecurrenceRule` if frequency present. Version is path-based `/api/v1`, not `Accept: application/vnd.api.v1+json` like `/api/publishing/*` — intentional divergence documented per `412` exploration.

**DB** `resources/db/changelog/publishing/015-create-recurring-schedules.yaml`:
- Table `recurring_schedules(id PK, workspace_id FK workspaces, created_by FK principals, template_post_id FK publications, frequency varchar16, recurrence_interval int, days_of_week varchar64, day_of_month int, end_date date, max_occurrences int, timezone varchar64 default UTC, next_scheduled_at timestamptz, status varchar16, created_at/updated_at timestamptz)` + index `idx_recurring_workspace_status_next(workspace_id,status,next_scheduled_at)`. No `recurring_schedules -> publication_jobs` FK; publications created are standalone drafts.

**Deletion handling**: `PublishingPublicationHandlers.kt:188` in `DeletePublicationHandler` after `deleteUnpublished` success calls `recurringScheduleRepository.pauseByTemplatePost(workspaceId, current.id)` and emits `NotificationEvent(RECURR​ENCE_PAUSED, "Recurring schedule paused because its template post was deleted.")`. Workspace-scoped pause, not cascade delete.

**BDD** `src/test/resources/features/publishing-recurring.feature` (17 lines): single `@recurring @fast` scenario `Create and pause a daily recurring schedule` (create daily → 200 + schedule id + >=3 publications → pause → status paused → list shows paused). No scenarios for weekly, monthly, update, delete, invalid timezone, endDate, maxOccurrences, conflict, permission, bulk of occurrences. Spec mention of "6 Gherkin scenarios" is unmet (1/6).

**Frontend UI** `apps/web/app/src/modules/publishing/`:
- Store `infrastructure/publishing.store.ts:81` types `RecurringSchedule/RecurrenceFrequency/RecurringScheduleInput/RecurringScheduleUpdate`, state `recurringSchedules: ref<RecurringSchedule[]>`, helpers `recurringPath() => /api/v1/workspaces/${workspaceId}/recurring` with `encodeURIComponent`, actions `fetchRecurringSchedules()` (staleness guard `latestRecurringSchedulesFetchId`), `createRecurringSchedule(input)`, `updateRecurringSchedule(id,input)`, `cancelRecurringSchedule(id)` (DELETE).
- Modal `presentation/components/RecurringScheduleModal.vue` (192 lines): `frequency/interval/daysOfWeek/dayOfMonth/startsAt/endDate/maxOccurrences/timezone` with `Intl.DateTimeFormat().resolvedOptions().timeZone` default, `toggleDay`, `toLocalInput`, focus trap, i18n `postDetail.recurring.*`.
- Scheduler `views/SchedulerView.vue:591` renders card `v-if="publishingStore.recurringSchedules.length"` listing frequency·interval with edit/cancel buttons, plus `fetchRecurringSchedules().catch(()=>undefined)` on mount. `PostDetailModal.vue:468` exposes `RecurringScheduleModal` via `showRecurring` + `makeRecurring` button.
- No bulk/recurring automation beyond CRUD — no cron preview, holiday toggle, evergreen queue.

### What Is Missing vs "Automation"

- **Timezone**: handled (`ZoneId.of`, browser Intl) but `ZonedDateTime` occurrences use schedule's `timezone` consistently; DST edge not tested.
- **Publication source deletion**: pause only, no resume, no un-pause on restore (publications are hard-deleted via `deleteUnpublished`, so template is gone).
- **Holiday handling**: none — `RecurringModels.occurrences` does not skip holidays, no `holidayCalendar` param.
- **Horizon/worker**: no `PublishingWorker` integration for rolling window; `PublishingWorker.kt` only handles `publication_jobs` claim/retry, not recurring generation. After 30 days or `endDate`, schedule silently stops unless user recreates.
- **Idempotency/duplicate guard**: per-occurrence publications use fresh `pub-UUID` without dedup key; re-creating same recurring schedule creates duplicates (no `idempotencyKey` like bulk's `sha256`).
- **Caps**: `maxOccurrences` enforced but not surfaced as progress; `interval` >1 works but monthly with `dayOfMonth=31` correctly skips Feb (tested in `RecurrenceRuleTest`).

## Affected Areas

- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/RecurringModels.kt` — current source of truth; any automation v2 (holiday, evergreen, worker window) modifies `RecurrenceRule.occurrences` and `RecurringSchedule` (+ `lastGeneratedUntil` or `holidayPolicy`).
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/RecurringScheduleRepository.kt` — add `findActiveDueForGeneration(now, limit)` / `updateNextScheduledAt` for worker; currently only CRUD+pause.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/RecurringScheduleHandlers.kt` — `CreateRecurringScheduleHandler` single-transaction expansion would need extraction to `RecurringGenerationService` for worker reuse and horizon chunking.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcRecurringScheduleRepository.kt` — needs `FOR UPDATE SKIP LOCKED` query for worker claiming active schedules.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingControllers.kt` — `RecurringScheduleController` (4 endpoints exist); automation v2 would add `POST /{id}/pause`/`resume` explicit vs status patch, and `GET /{id}/occurrences?from=&to=` preview.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/scheduling/PublishingWorker.kt` — would host `RecurringScheduleWorker.pollOnce` if rolling generation chosen; currently no recurring logic.
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/application/PublishingPublicationHandlers.kt:188` — deletion → pause wiring is correct; re-scope would add `pauseByTemplatePost` idempotency guard already present.
- `server/smp/src/main/resources/db/changelog/publishing/015-create-recurring-schedules.yaml` — base migration shipped; v2 would add `022+` for `last_generated_until / occurrence_count / holiday_policy`.
- `apps/web/app/src/modules/publishing/infrastructure/publishing.store.ts` — recurring actions exist; v2 adds `previewOccurrences`, `pause/resume`, holiday UI.
- `apps/web/app/src/modules/publishing/presentation/components/RecurringScheduleModal.vue` — v1 modal complete; v2 needs horizon/holiday/evergreen controls.
- `apps/web/app/src/modules/publishing/views/SchedulerView.vue` — list + toggle/cancel exist; needs status badges PAUSED vs CANCELLED.
- `openspec/specs/publishing/spec.md` (2537 lines) — contains **no** `Recurring` requirement/scenario — drift: shipped feature has zero durable spec. `openspec/specs/dashboard-scheduling/spec.md` unrelated.
- `server/smp/src/test/resources/features/publishing-recurring.feature` — 1/6 scenarios; 5+ pending per original spec claim.
- `openspec/changes/dallay-413-bulk-scheduling/` — sibling change for bulk CSV (PR #922 open, not merged) — explicitly separate bounded context (`BulkModels`, `BulkValidationPipeline`, `/api/v1/workspaces/{workspaceId}/bulk/*`). No code overlap with recurring beyond `PublicationLifecyclePolicy` reuse pattern.

## Approaches

1. **Close DALLAY-414 as Delivered (PR #552) — Housekeeping Only**
   - Declare recurring v1 complete, close Linear as Done, delete orphan branch `feature/dallay-414-featscheduling-recurring-posts-automation` (no commits to preserve), backfill `openspec/specs/publishing/spec.md` delta for shipped 4 endpoints/RecurrenceRule/pause-on-delete/BDD, and archive change. No new code.
   - Pros: Truthful to git evidence; unblocks stale In Progress; avoids double-work; bulk (413) proceeds independently via PR 922.
   - Cons: Leaves "automation" gap (horizon, holidays, evergreen) unaddressed; spec drift remains until backfill.
   - Effort: Low (docs-only, 1 PR)

2. **Re-scope DALLAY-414 to Recurring Automation v2 — Rolling Worker + Holiday + Evergreen**
   - Keep 414 In Progress but redefine scope: add `RecurringGenerationWorker` (poll active schedules where `nextScheduledAt <= now+horizon`, generate next N occurrences with `FOR UPDATE SKIP LOCKED`, `lastGeneratedUntil` tracking), `holidayPolicy: SKIP|POSTPONE` with calendar source, `evergreen` mode (re-queue from library), expanded BDD (6→10 scenarios), and `GET /occurrences` preview. Reuses `RecurrenceRule.occurrences` plus `holidayCalendar` filter.
   - Pros: Delivers true "automation" implied by issue title; closes horizon gap; aligns with Linear follow-up-to-bulk narrative (bulk creates many, recurring automates repeats).
   - Cons: New worker + table migration + schedule claiming complexity; conflicts with 413 review bandwidth (PR 922 stacked 3 slices); needs product decision on holiday source and evergreen UX; higher test surface (worker + stale lease).
   - Effort: High

3. **Merge 414 Backfill + 413 Bulk as Single Stacked Delivery**
   - Treat `feature/dallay-414-*` as already merged and fold any remaining recurring polish (spec backfill, extra BDD) into bulk PR 922 chain as PR0. No new branch.
   - Pros: Single review train; leverages existing PR 922 CI; minimizes branch sprawl.
   - Cons: Conflates distinct capabilities (recurring vs bulk) violating bounded-context isolation; review budget exceeds 400-line guard; bulk is 900 lines already — adding recurring polish pushes over.
   - Effort: Medium (coordination cost)

## Recommendation

**Approach 1 — Close DALLAY-414 as Delivered** is recommended.

Rationale: Evidence is unambiguous — `RecurringSchedule`, `RecurrenceRule`, 4 endpoints, R2DBC, pause-on-delete, store/modal/scheduler UI, and BDD are already on `main` via PR #552 (31 Jul 2024). The `feature/dallay-414-*` branch has no commits; Linear staleness is process debt, not code debt. Re-scoping to true automation (Approach 2) is valid but should be a **new** Linear issue `DALLAY-414-v2` or `DALLAY-4xx Recurring Automation` with explicit holiday/evergreen requirements, not a reopening of a 3-point should-have that already shipped. Approach 3 risks review overload and couples unrelated bounded contexts.

Immediate next steps for Approach 1:
- Close Linear DALLAY-414 (or mark Done with comment linking PR #552 + this exploration), delete remote branch `feature/dallay-414-featscheduling-recurring-posts-automation` if no longer needed (preserve if team prefers empty branch hygiene).
- Backfill spec drift: `openspec/specs/publishing/spec.md` delta for recurring (4 endpoints, rule validation, pause on delete, workspace scoping, 1 BDD passing + 5 NOT_IMPLEMENTED until v2).
- If automation gap is product-desired, create new issue with spec referencing this exploration's "What Is Missing" section.

## Risks

- **Stale In Progress hides velocity**: 414 at In Progress for 13 months skews burndown/budget; closing restores accurate reporting.
- **Orphan branch confusion**: `feature/dallay-414-*` with no commits invites accidental work on top of stale base; `origin/main` at 56a6340b is correct base but branch should be removed or rebased.
- **Spec drift**: Publishing spec lacks any recurring requirement — future audits (Sonar, Modulith) will flag undocumented behavior; backfill is required before archive gate per project definition of done.
- **Horizon trap**: v1 creates only initial window (≤30d or endDate). Users expecting perpetual automation will see silent stop; support load risk if not documented.
- **No holiday/evergreen**: Product may have promised automation including holidays — gap between issue title and shipped v1 is contractual risk.
- **BDD coverage gap**: 1/6 scenarios passing — `verify-report` for archive would be BLOCKED under QA policy (`acceptance_required_for_behavior_changes`, `BLOCKED/NOT TESTED` blocks archive).
- **Relation to 413 bulk**: Bulk PR 922 (`feature/dallay-413-featscheduling-bulk-scheduling-for-multiple-posts`) is OPEN and independent; conflating 414 with 413 would create stacked PR chain >900 lines already at budget edge — keep separate.
- **Workspace isolation already correct**: Recurring reuses `requireWorkspacePath` like bulk will; no new auth risk.
- **Timezone DST**: `RecurrenceRule` uses `ZonedDateTime.plusDays/Weeks/Months` which handles DST, but no explicit test for DST transition (weekly at 02:30 on DST day) — edge case.
- **Recommendation to close needs Linear write**: No API token available to auto-transition Linear — manual step required.

## Ready for Proposal

No — and intentionally. `sdd-propose` is **not** recommended for DALLAY-414. The change is already implemented and merged; a new proposal would duplicate shipped behavior. Proceed instead to `sdd-archive` housekeeping (spec backfill + verify/qa for v1) if process requires formal closure, or directly close Linear and delete branch. If product wants automation v2 (worker, holiday, evergreen), open a **new** change proposal scoped to those gaps.

Next: `sdd-archive` (docs-only) or `none` — do not run `sdd-propose` for DALLAY-414.

