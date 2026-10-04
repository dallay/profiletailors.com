# Tasks: Notification Admin Queries and Safe Retry Operations

## Review Workload Forecast

| Field                   | Value                                                                                                          |
|-------------------------|----------------------------------------------------------------------------------------------------------------|
| Estimated changed lines | 1200-1500                                                                                                      |
| 400-line budget risk    | High                                                                                                           |
| Chained PRs recommended | Yes                                                                                                            |
| Suggested split         | PR 1 (domain + redaction) → PR 2 (query port + adapter) → PR 3 (retry + controller) → PR 4 (BDD + integration) |
| Delivery strategy       | ask-on-risk                                                                                                    |
| Chain strategy          | pending                                                                                                        |

Decision needed before apply: Yes
Chained PRs recommended: Yes
Chain strategy: pending
400-line budget risk: High

### Suggested Work Units

| Unit | Goal                                 | Likely PR | Notes                                                                    |
|------|--------------------------------------|-----------|--------------------------------------------------------------------------|
| 1    | Domain whitelist + redaction utility | PR 1      | Base layer; pure domain + shared utility; tests/docs included            |
| 2    | Query port + R2DBC adapter           | PR 2      | Depends on PR 1; read-only infrastructure; integration tests included    |
| 3    | Retry command + controller + auth    | PR 3      | Depends on PR 1,2; controller + handler + audit; unit + webtest included |
| 4    | BDD scenarios + nav integration      | PR 4      | Depends on PR 1,2,3; E2E verification + UI wiring                        |

## Phase 1: Domain Foundation

- [x] 1.1 Create `NotificationRetryEligibility` domain value object in `notifications.domain` with
  template whitelist (`PASSWORD_RECOVERY` allowed, `WORKSPACE_INVITATION`/`WAITLIST_INVITATION`
  denied)
    - **Acceptance**: `isEligible(templateId: String): Boolean` method returns true for
      PASSWORD_RECOVERY, false for invitation templates
    - **Test**: Unit test covering all template cases (eligible, ineligible, unknown)
    - **Dependencies**: None

- [x] 1.2 Extend `Notification` domain aggregate with `canRetry(): Boolean` method delegating to
  `NotificationRetryEligibility`
    - **Acceptance**: Method returns eligibility based on template_id property
    - **Test**: Unit test with notification fixtures (eligible template → true, ineligible → false)
    - **Dependencies**: 1.1

- [x] 1.3 Create `PayloadRedactor` utility in `platformadmin.infrastructure.util` with
  `redact(payload: Map<String, Any>): Map<String, Any>` stripping sensitive keys
    - **Acceptance**: Removes `token`, `password`, `acceptUrl`, `rawToken`, `verificationToken`;
      retains `recipient`, `channel`, `templateId`
    - **Test**: Unit test with sample payloads covering all denylist keys, nested maps, null values
    - **Dependencies**: None

## Phase 2: Query Layer

- [x] 2.1 Define `NotificationAdminQuery` port interface in `platformadmin.application.query` with
  `listNotifications(filters, pagination): Page<NotificationSummary>` method
    - **Acceptance**: Interface declares filters (status, channel, templateId, recipient,
      timeRange), pagination (page, size), returns `Page<NotificationSummary>` DTO
    - **Test**: Compilation check; no runtime test (port is interface)
    - **Dependencies**: None

- [x] 2.2 Create `NotificationSummary` DTO in `platformadmin.application.query` with id, channel,
  templateId, recipient, status, error, createdAt, redactedPayload fields
    - **Acceptance**: Immutable data class with all required fields; `redactedPayload` typed as
      `Map<String, Any?>`
    - **Test**: Instantiation test; JSON serialization test if annotated with Jackson annotations
    - **Dependencies**: None

- [x] 2.3 Implement `R2dbcNotificationAdminQueryAdapter` in
  `platformadmin.infrastructure.persistence` querying `notifications` table with dynamic WHERE
  clauses per filter
    - **Acceptance**: Adapter implements `NotificationAdminQuery`; builds SQL with optional filters;
      maps rows to `NotificationSummary` with `PayloadRedactor.redact()` applied to payload column
    - **Test**: Integration test with Testcontainers R2DBC; insert test notifications with various
      statuses/channels/templates; verify filtering (status=FAILED, channel=EMAIL,
      templateId=PASSWORD_RECOVERY, recipient contains, time range); verify redaction (sensitive
      keys removed); verify pagination (page 0 size 10, page 1 size 10)
    - **Dependencies**: 1.3, 2.1, 2.2

## Phase 3: Retry Command Layer

- [x] 3.1 Create `RetryNotificationCommand` in `platformadmin.application.command` with
  `notificationId: NotificationId`, `operatorId: OperatorId` fields
    - **Acceptance**: Immutable command class with required fields
    - **Test**: Instantiation test
    - **Dependencies**: None

- [x] 3.2 Implement `RetryNotificationHandler` in `platformadmin.application.command` with
  eligibility check, dispatch via `NotificationService`, audit publishing
    - **Acceptance**: Handler fetches notification by ID; validates `canRetry()`; if eligible,
      extracts original payload and dispatches via `NotificationService.notify()` with
      idempotency_key = `{originalKey}-retry-{timestamp}`; publishes `NOTIFICATION_RETRIED` audit
      event with outcome (SUCCESS, REJECTED, DISPATCH_FAILED); returns `RetryResult` sealed class
    - **Test**: Unit test with fakes (`FakeNotificationRepository`, `FakeNotificationService`,
      `FakeAdminAuditPublisher`); verify eligibility gate (eligible template → dispatch called,
      ineligible → REJECTED outcome, audit published); verify idempotency key format; verify audit
      metadata (notificationId, channel, templateId, retryOutcome, priorStatus, priorError)
    - **Dependencies**: 1.2, 3.1

- [x] 3.3 Add `NOTIFICATION_RETRIED` enum value to `AdminAuditEventType` in `platformadmin.domain`
    - **Acceptance**: Enum includes new value
    - **Test**: Compilation check; enum iteration test
    - **Dependencies**: None

- [x] 3.4 Extend `AdminAuditPublisher` (or create event factory) to support `NOTIFICATION_RETRIED`
  event structure with metadata (notificationId, channel, templateId, retryOutcome, priorStatus,
  priorError)
    - **Acceptance**: Publisher accepts retry-specific metadata map; applies redaction to priorError
      field
    - **Test**: Unit test verifying metadata structure; verify priorError redaction when containing
      sensitive substrings
    - **Dependencies**: 3.3

## Phase 4: HTTP Controllers

- [ ] 4.1 Create `NotificationAdminController` in `platformadmin.infrastructure.http` with
  `GET /api/admin/notifications` endpoint
    - **Acceptance**: Controller maps query params (status, channel, templateId, recipient,
      createdAfter, createdBefore, page, size) to `NotificationAdminQuery` filters; returns
      paginated `NotificationSummaryResponse` with redacted payloads; enforces
      `@RequiresPlatformPermission("platform.notifications.read")`
    - **Test**: WebFluxTest with `WebTestClient`; mock `NotificationAdminQuery` port; verify 200
      response with pagination metadata; verify query param mapping; verify 403 when operator lacks
      permission; verify Accept: application/vnd.api.v1+json media type
    - **Dependencies**: 2.1, 2.2

- [ ] 4.2 Add `POST /api/admin/notifications/{id}/retry` endpoint to `NotificationAdminController`
    - **Acceptance**: Controller extracts notificationId from path, operatorId from security
      context; invokes `RetryNotificationHandler`; returns 200 with retry outcome on success, 400 on
      ineligibility (body includes denial reason), 404 if notification not found, 403 if lacking
      permission; enforces `@RequiresPlatformPermission("platform.notifications.manage")`
    - **Test**: WebFluxTest; mock handler with eligible/ineligible/not-found scenarios; verify
      status codes; verify error body structure; verify permission enforcement; verify 403 for
      AUDITOR role (has read, lacks manage)
    - **Dependencies**: 3.2, 4.1

## Phase 5: Authorization

- [ ] 5.1 Register `platform.notifications.read` permission in permission registry with description
  "Read notification delivery logs and status"
    - **Acceptance**: Permission exists in registry; granted to OWNER, OPERATOR, AUDITOR roles via
      `OperatorAccessResolver`
    - **Test**: Integration test or unit test with `OperatorAccessResolver`; verify permission
      granted to expected roles, denied to VIEWER
    - **Dependencies**: None

- [ ] 5.2 Register `platform.notifications.manage` permission in permission registry with
  description "Retry failed notifications"
    - **Acceptance**: Permission exists in registry; granted to OWNER, OPERATOR roles via
      `OperatorAccessResolver`
    - **Test**: Integration test or unit test; verify permission granted to OWNER/OPERATOR, denied
      to AUDITOR/VIEWER
    - **Dependencies**: None

## Phase 6: Frontend Integration

- [ ] 6.1 Add `notifications` nav item to `apps/web/admin/src/config/nav-registry.ts` under Platform
  section
    - **Acceptance**: Nav item includes id, label, route `/admin/platform/notifications`, icon,
      permission guard `platform.notifications.read`
    - **Test**: Manual verification in nav menu; permission-based visibility (AUDITOR sees, VIEWER
      does not)
    - **Dependencies**: 5.1

- [ ] 6.2 Extend `apps/web/admin/src/stores/auth.store.ts` with `platform.notifications.read` and
  `platform.notifications.manage` permission checks
    - **Acceptance**: Store exposes `can('platform.notifications.read')` and
      `can('platform.notifications.manage')` methods; returns boolean based on operator
      role/permissions
    - **Test**: Unit test with Pinia test utils; verify permission logic for all roles
    - **Dependencies**: 5.1, 5.2

## Phase 7: BDD Scenarios

- [x] 7.1 Create `server/smp/src/test/resources/features/platformadmin/notifications.feature` with 8
  scenarios covering:
    - List all notifications (scenario: Platform admin lists all notifications)
    - Filter by status=FAILED (scenario: Filter notifications by failed status)
    - Filter by channel=EMAIL (scenario: Filter notifications by channel)
    - Redaction verification (scenario: Sensitive payload fields are redacted)
    - Safe retry success (scenario: Retry eligible failed notification)
    - Unsafe retry blocked (scenario: Retry invitation notification is rejected)
    - Permission denial read (scenario: Viewer cannot query notifications)
    - Permission denial retry (scenario: Auditor cannot retry notifications)
    - **Acceptance**: Feature file follows existing BDD structure; uses @platform-notifications
      @fast tags; scenarios use Given-When-Then with realistic data
    - **Test**: Cucumber parser validation
    - **Dependencies**: None

- [x] 7.2 Implement `NotificationAdminBddSteps` glue in
  `server/smp/src/test/kotlin/com/profiletailors/smp/bdd/glue/NotificationAdminBddSteps.kt`
    - **Acceptance**: Step definitions map to feature file steps; use `BddDatabaseSupport` for
      setup; use `WebTestClient` with operator bearer tokens (owner-token, auditor-token,
      viewer-token); set `Accept: application/vnd.api.v1+json` and `X-Workspace-Id` headers; assert
      response status, pagination, redaction, retry outcomes
    - **Test**: Run `just backend-bdd-fast`; verify all scenarios pass
    - **Dependencies**: 7.1, 4.1, 4.2

## Phase 8: Integration Tests

- [x] 8.1 Create `R2dbcNotificationAdminQueryAdapterIntegrationTest` in
  `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/`
    - **Acceptance**: Test uses Testcontainers PostgreSQL; inserts notifications with various
      statuses/channels/templates/payloads; verifies query filtering (each filter dimension
      independently and combined); verifies pagination (offset, limit); verifies payload redaction
      (sensitive keys stripped, safe keys retained); verifies empty result when no matches
    - **Test**: Run test class; all assertions pass
    - **Dependencies**: 2.3

- [x] 8.2 Create `NotificationAdminControllerIntegrationTest` (WebFlux slice test) in
  `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/infrastructure/http/`
    - **Acceptance**: Test uses `@WebFluxTest(NotificationAdminController::class)`; mocks
      `NotificationAdminQuery` port and `RetryNotificationHandler`; verifies query endpoint
      request/response contract (pagination, filters, media type); verifies retry endpoint
      request/response contract (success, eligibility denial, not found); verifies authorization
      enforcement (403 on missing permissions); verifies error response structure matches API
      standards
    - **Test**: Run test class; all assertions pass
    - **Dependencies**: 4.1, 4.2

## Phase 9: Documentation and Verification

- [x] 9.1 Update OpenAPI annotations on `NotificationAdminController` endpoints with operation
  summaries, parameter descriptions, response schemas, security requirements
    - **Acceptance**: Swagger UI displays endpoints with full documentation; parameters documented
      (type, required, description); responses documented (200, 400, 403, 404 with example bodies);
      security shows required permissions
    - **Test**: Run backend, open Swagger UI at `/swagger-ui.html`, verify endpoints appear under
      "Platform Admin - Notifications" section
    - **Dependencies**: 4.1, 4.2

- [x] 9.2 Run full backend quality gate: `just backend-check` (includes lint, tests, build) and
  `just backend-bdd-fast`
    - **Acceptance**: All checks pass; no new Detekt findings; no test failures; no compilation
      errors
    - **Test**: CI-equivalent local verification
    - **Dependencies**: All prior tasks

- [x] 9.3 Verify no static analysis regressions: no new suppressions, no baseline changes, no
  architecture test failures
    - **Acceptance**: `git diff` shows no changes to `detekt-baseline.xml`, no new `@Suppress`
      annotations, `HexagonalArchTest` and `ComponentScanArchTest` pass
    - **Test**: Manual diff inspection + architecture test execution
    - **Dependencies**: 9.2

## Summary

**Total tasks**: 30 (9 phases)
**Estimated scope**:

- Domain: 3 tasks (whitelist, aggregate extension, redaction utility)
- Query layer: 3 tasks (port, DTO, R2DBC adapter)
- Retry layer: 4 tasks (command, handler, audit enum, audit publisher)
- Controllers: 2 tasks (query endpoint, retry endpoint)
- Authorization: 2 tasks (read permission, manage permission)
- Frontend: 2 tasks (nav item, store permissions)
- BDD: 2 tasks (feature file, step definitions)
- Integration tests: 2 tasks (adapter test, controller test)
- Documentation: 3 tasks (OpenAPI, quality gate, static analysis verification)

**Critical path**: 1.1 → 1.2 → 3.2 → 4.2 (domain eligibility → retry handler → controller)
**Parallel tracks**: Query layer (2.x) can proceed in parallel with retry layer (3.x) after 1.3
completes **Verification bottleneck**: 9.2 blocks 9.3 (quality gate must pass before static analysis
check)
