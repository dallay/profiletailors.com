# Delta for publishing — DALLAY-413 Bulk Scheduling

## ADDED Requirements

### Requirement: Bulk Validate (Sync, No Persistence)

MUST expose `POST /api/v1/workspaces/{workspaceId}/bulk/validate` returning per-row `{rowIndex, status: VALID|INVALID, errors[]}`. MUST NOT persist publications or `BulkImportJob`.

#### Scenario: Gherkin 1 — per-row errors, no persistence

- GIVEN CSV with 2 valid, 1 invalid row
- WHEN `POST /bulk/validate` is called
- THEN response MUST list 3 rows (1 `INVALID`) with no DB writes

#### Scenario: Retry is side-effect free

- GIVEN same CSV validated twice
- WHEN both complete
- THEN responses MUST match and neither MUST persist

### Requirement: Chunked Bulk Schedule

MUST expose `POST /api/v1/workspaces/{workspaceId}/bulk/schedule` persisting valid rows via `PublicationCreationService` in 50–100-row `runAtomically` chunks. MUST return `{jobId, totalRows, scheduledCount, failedCount, rows[]}` with 200/207 and support partial success.

#### Scenario: Gherkin 2 — chunked atomic, partial success

- GIVEN validate flagged 2 VALID, 1 INVALID
- WHEN `POST /bulk/schedule` is called
- THEN it MUST return `scheduledCount=2, failedCount=1` with 2 `SCHEDULED` publications

#### Scenario: 1000-row batch chunked

- GIVEN 1000-row CSV
- WHEN schedule is called
- THEN system MUST persist in 10–20 transactions and report via job status

### Requirement: Bulk Job Status

MUST expose `GET /api/v1/workspaces/{workspaceId}/bulk/jobs/{jobId}` returning `status` (`SCHEDULED|PARTIAL|FAILED`), counts and row errors. MUST be workspace-scoped.

#### Scenario: Gherkin 3 — owner sees counts

- GIVEN workspace A job is `PARTIAL`
- WHEN `GET /bulk/jobs/{jobId}` in A is called
- THEN it MUST return 200 with counts and row errors

#### Scenario: Cross-workspace blocked

- GIVEN job in workspace A
- WHEN workspace B requests same `jobId`
- THEN it MUST return 404

### Requirement: Bulk Templates

MUST expose `GET /bulk/templates` and `GET /bulk/templates/{id}/csv` with canonical header `bodyText,scheduledFor,timezone,media_urls,hashtags`.

#### Scenario: Gherkin 4 — catalog and CSV correct

- GIVEN templates configured
- WHEN both template endpoints are called
- THEN list MUST be non-empty and CSV header MUST match canonical order

### Requirement: CSV Parsing and Row Errors

MUST skip blank lines, flag `INVALID_DATE` for non-ISO-8601 `scheduledFor`, flag `MISSING_CONTENT` when `bodyText` and `media_urls` empty, warn `DUPLICATE` on `sha256(ws+body+scheduledFor)` collision, validate `media_urls` via `MediaAssetResolver` (SSRF allowlist, 10MB) → `INVALID_MEDIA`.

#### Scenario: Blank lines skipped

- GIVEN CSV with 2 rows + 1 blank line
- WHEN validate is called
- THEN result MUST contain 2 rows

#### Scenario: Gherkin 5 — invalid date and missing content

- GIVEN row with `scheduledFor=not-a-date`, empty body/media
- WHEN validate is called
- THEN row MUST be `INVALID` with `INVALID_DATE` and `MISSING_CONTENT`

#### Scenario: Duplicate warning and invalid media

- GIVEN duplicate rows and row with blocked/oversized URL
- WHEN validate/schedule is called
- THEN second duplicate MUST warn `DUPLICATE`, media row MUST be `INVALID_MEDIA`

### Requirement: Bulk Isolation, Auth, Idempotency

All bulk endpoints MUST verify `path workspaceId == context workspaceId` else 403/404, require `emailStatus=VERIFIED` else 403, and enforce `idempotencyKey=sha256(ws+principal+csvHash)` → 409 with existing `jobId` on duplicate.

#### Scenario: Workspace mismatch rejected and unverified blocked

- GIVEN user in A calls bulk for B, or `UNVERIFIED` calls schedule
- WHEN request is evaluated
- THEN it MUST return 403/404 and not process

#### Scenario: Duplicate CSV returns 409

- GIVEN same principal resubmits identical `csvHash`
- WHEN schedule is called
- THEN it MUST return 409 with existing `jobId`

### Requirement: Reuse Publishing Lifecycle

Each row MUST pass `PublicationLifecyclePolicy.validateForCreation`, `ProviderCapabilityValidator`, `ConflictDetectionPolicy` (15-min, same account, `SCHEDULED`/`QUEUED` only → `hasConflict:true` warn-only V1), and `MediaAssetResolver` for `EXTERNAL_URL`. V1 MUST only allow `SCHEDULED_AT` with future `scheduledFor`.

#### Scenario: Capability violation

- GIVEN LinkedIn row with `APPLICATION/PDF`
- WHEN validate is called
- THEN row MUST be `INVALID` with capability error

#### Scenario: Conflict is warn-only

- GIVEN two rows same account 10 min apart
- WHEN schedule is called
- THEN both MUST schedule with `hasConflict:true`
