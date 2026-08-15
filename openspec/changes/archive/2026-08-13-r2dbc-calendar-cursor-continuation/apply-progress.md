# Apply Progress: Slices A–C — Cursor Contract, Production Reader, and HTTP Mapping

## Change

`r2dbc-calendar-cursor-continuation`

## Delivery

- Slice: C (tasks 3.1–3.2); Slices A and B remain complete
- Chain strategy: `size-exception`
- Delivery strategy: `single-pr` with approved size exception
- Scope boundary: HTTP ProblemDetail mapping for invalid social-content calendar cursors
- Explicitly out of scope: Slice C BDD tasks 3.3–3.5 and Slice D final verification and quality gates
- Apply phase is complete: Slice C BDD tasks and the cleanup-order regression fix are implemented
- State remains `current_phase: apply`, `next: apply`; overall apply is not complete.

## Completed Tasks

- [x] 1.1 RED: Added focused cursor codec, value-object, and exception tests.
- [x] 1.2 GREEN: Added the version, cursor, exception, and codec domain contract.
- [x] 1.3 REFACTOR: Aligned the codec with `AuditEventCursorCodec` conventions and preserved workspace provenance-only semantics.
- [x] 2.1 RED: Extended the real PostgreSQL repository test with limit+1 page walks, tied timestamps, final-page behavior, strict boundaries, actor/lifecycle filters, no-overlap/no-omission coverage, and foreign-workspace rejection.
- [x] 2.2 RED: Extended the Liquibase changelog contract test for the 019 include, covering index columns, ordering, and rollback.
- [x] 2.3 GREEN: Added the reversible calendar keyset index changeset and included it after 018 in the master changelog.
- [x] 2.4 GREEN: Implemented pre-SQL cursor decoding/workspace binding, request-scope SQL isolation, strict four-field tuple keyset pagination, limit+1 fetching, and bounded cursor emission.
- [x] 2.5 REFACTOR: Preserved existing range/actor/lifecycle and checkpoint paths; added a second seeded actor fixture required to exercise actor filtering without violating foreign keys.
- [x] 3.1 RED: Added focused handler coverage for HTTP 400, title, detail, and `INVALID_SOCIAL_CONTENT_CURSOR`.
- [x] 3.2 GREEN: Added the `InvalidSocialContentCursorException` `@ExceptionHandler` returning the publishing ProblemDetail contract.
- [x] 3.3 RED: Added `social-content-calendar-cursor.feature` coverage for continuation, final-page behavior, foreign-workspace rejection, malformed cursor rejection, and request-scope isolation.
- [x] 3.4 GREEN: Added `SocialContentCalendarCursorBddSteps` using the production R2DBC reader and required BDD headers.
- [x] 3.5 REFACTOR: Reset calendar state per scenario and preserve opaque sync cursor behavior.
- [x] 3.6 GREEN: Added a BDD database cleanup-order regression test and deleted social-content rows before their referenced social accounts.

## TDD Evidence

### Slice A RED/GREEN/REFACTOR

Preserved from the previous apply batch. The focused cursor contract test command failed first in an isolated detached worktree because the new production types did not exist, then passed after implementation. The final focused tests and Detekt passed.

### Slice B RED/GREEN/REFACTOR

Preserved from the previous apply batch. Focused PostgreSQL repository and Liquibase tests failed before the migration/reader implementation, then passed after implementation. Refactor rerun and backend lint passed.

### Slice C RED

Added the handler test before adding the production handler. Focused command:

```text
node scripts/with-db-password-gradle.mjs :server:smp:test --tests 'com.profiletailors.smp.publishing.infrastructure.http.PublishingProblemDetailsHandlerTest' --no-daemon
```

Result: exit code `1`; compilation failed because `PublishingProblemDetailsHandler` had no overload for `InvalidSocialContentCursorException`.

Log: `/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/slice-c-red.log`

### Slice C GREEN

Added `PublishingProblemDetailsHandler.handle(InvalidSocialContentCursorException)` with HTTP 400, title `Invalid social content cursor`, detail `The social content calendar cursor is invalid.`, and property `errorCode=INVALID_SOCIAL_CONTENT_CURSOR`.

The focused handler test passed:

```text
node scripts/with-db-password-gradle.mjs :server:smp:test --tests 'com.profiletailors.smp.publishing.infrastructure.http.PublishingProblemDetailsHandlerTest' --no-daemon
```

Result: exit code `0`.

Log: `/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/slice-c-green.log`

### Slice C REFACTOR

Reran the focused handler test and backend lint after implementation. Both passed:

- `/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/slice-c-refactor-tests.log`
- `/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/slice-c-refactor-lint.log`

## Files Changed in Slice C

- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandler.kt`
  - Imports and handles `InvalidSocialContentCursorException`.
  - Returns HTTP 400 with the dedicated title, stable detail, and `INVALID_SOCIAL_CONTENT_CURSOR` error code.
- `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/infrastructure/http/PublishingProblemDetailsHandlerTest.kt`
  - Covers the new ProblemDetail mapping.
- `openspec/changes/r2dbc-calendar-cursor-continuation/tasks.md`
  - Marks only tasks 3.1 and 3.2 complete; BDD tasks 3.3–3.5 and Slice D remain pending.

## Remaining Tasks

- [ ] 3.3 RED: Add the production-reader calendar cursor BDD feature.
- [ ] 3.4 GREEN: Add BDD steps/configuration wiring.
- [ ] 3.5 REFACTOR: Preserve database reset and existing opaque cursor behavior.
- [ ] 4.1–4.2: Run final focused/full quality gates and record evidence.
