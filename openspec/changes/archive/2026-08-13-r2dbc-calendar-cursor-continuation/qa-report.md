# Acceptance QA Report: r2dbc-calendar-cursor-continuation

## 1. Identity

| Field | Value |
|---|---|
| Change | `r2dbc-calendar-cursor-continuation` |
| Mode | `openspec` filesystem artifacts |
| Phase | QA / acceptance gate |
| Date | 2026-08-13 |
| Target | Local Spring Boot API at `http://localhost:7638` |

This report records live acceptance evidence for the calendar cursor continuation endpoint. The controlled QA rows were inserted into the local PostgreSQL instance and are not production data.

## 2. Source Artifacts and Technical Verification Handoff

### Source artifacts reviewed

- `openspec/changes/r2dbc-calendar-cursor-continuation/proposal.md`
- `openspec/changes/r2dbc-calendar-cursor-continuation/specs/social-content-sync/spec.md`
- `openspec/changes/r2dbc-calendar-cursor-continuation/design.md`
- `openspec/changes/r2dbc-calendar-cursor-continuation/tasks.md`
- `openspec/changes/r2dbc-calendar-cursor-continuation/verify-report.md`
- `openspec/changes/r2dbc-calendar-cursor-continuation/state.yaml`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/infrastructure/persistence/R2dbcSocialContentRepositories.kt`
- `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/domain/SocialContentModels.kt`

### Technical verification handoff

The existing `verify-report.md` records passing focused backend unit, PostgreSQL integration, BDD, backend-check, compiler, Detekt, and `just ci-local` evidence. It also records inconsistent full-CI evidence: one run completed with Playwright tests passing, while a later run failed during marketing Playwright server startup. Those results remain technical verification evidence and are not silently promoted to live acceptance.

## 3. Target, Environment, Permissions, and Limitations

| Area | Result |
|---|---|
| Application target | Available: local backend responding on `http://localhost:7638` |
| Database | Available: Docker PostgreSQL container `profile-tailors-postgresql-1` |
| Authentication | Available: QA login credentials; login returned an access token |
| Workspace context | Available: `qa-workspace-001` authenticated QA user; `dev-workspace-001` was also present in the local database but not assigned to the QA principal |
| Controlled fixture | Three `PUBLISHED` LinkedIn posts in `qa-workspace-001`, ordered at 10:00, 11:00, and 12:00 UTC on 2026-08-01; one separate post in `dev-workspace-001` |
| Browser/UI scope | Not applicable; this is a backend-only change |
| Limitation | A second workspace could not be exercised with the same authenticated principal because `qa-user-001` has membership only in `qa-workspace-001`; the API correctly rejected a cursor used with the unpermitted dev workspace before database results were returned |

## 4. Evidence Capture

Evidence files were written outside the repository at:

`/var/folders/zz/d4kl1hfj1j15nxm43d24px300000gn/T/opencode/calendar-qa/`

Files include the login response, page-1/page-2 responses and statuses, malformed-cursor response and status, tied-boundary response and status, cross-workspace responses and statuses, anonymous-request response and status, and a dev-workspace first-page response and status.

Representative live requests used:

```text
POST /api/auth/login
Accept: application/vnd.api.v1+json
Content-Type: application/json
{"email":"qa@profiletailors.com","password":"FinalQaPassword789!"}

GET /api/publishing/social-content/calendar?from=2026-08-01T00:00:00Z&to=2026-08-02T00:00:00Z&limit=2[&cursor=...]
Authorization: Bearer <QA access token>
Accept: application/vnd.api.v1+json
X-Workspace-Id: qa-workspace-001
```

## 5. Scenario Matrix

| ID | Capability | Acceptance scenario | Result | Evidence |
|---|---|---|---|---|
| S1 | Live API/client | A prior page returns a cursor and the next request continues without overlap | PASS | Page 1 returned `qa-post-10`, `qa-post-11` plus a cursor. Page 2 using that cursor returned only `qa-post-12`; no overlap. HTTP 200 for both. |
| S2 | Live API/client | A first request without a cursor returns the earliest deterministic-tuple-ordered page | PASS | First page returned posts in ascending `publishedAt`: 10:00 then 11:00 UTC. HTTP 200. |
| S3 | Live API/client | Multiple posts tied on `published_at` remain stable across pages without overlap | PASS WITH WARNINGS | The endpoint was queried at a boundary with `limit=1`; the first result was deterministic and returned a cursor. The controlled fixture did not include two tied timestamps, so the exact tied-timestamp multi-page branch was not exercised live. Repository BDD/verification evidence covers the tie-key contract. |
| S4 | Live API/client + security | A cursor from workspace `WS-A` cannot be used to read workspace `WS-B` | PASS WITH WARNINGS | Cursor from `qa-workspace-001` used with `X-Workspace-Id: dev-workspace-001` returned HTTP 400 `INVALID_SOCIAL_CONTENT_CURSOR`; no dev post was returned. A permitted second workspace membership was unavailable. |
| S5 | Live API/client + security | Cursor provenance cannot override SQL tenant scope, which must remain the authenticated request workspace | PASS | The repository query binds `workspace_id = :workspaceId` from the request scope and the cursor decoder rejects workspace mismatch. Live QA confirmed mismatch rejection. A permitted second-workspace authenticated comparison was unavailable. |
| S6 | Live API/client | Cursor version and workspace provenance are carried, and malformed/unsupported cursors yield HTTP 400 Problem Details with `INVALID_SOCIAL_CONTENT_CURSOR` | PASS | Malformed cursor returned HTTP 400 with `title: Invalid social content cursor` and `errorCode: INVALID_SOCIAL_CONTENT_CURSOR`. The returned valid cursor decoded to version `1`, workspace `qa-workspace-001`, timestamp, provider, account, and external post tuple. |
| S7 | Live API/client | Delimiter-bearing or blank cursor fields are rejected rather than changing pagination scope | PASS WITH WARNINGS | Malformed/unsupported encoded cursor returned HTTP 400 with `INVALID_SOCIAL_CONTENT_CURSOR`. Delimiter-bearing and blank-field payload variants were not separately sent through the live HTTP harness; codec tests and repository verification cover their rejection. |
| S8 | Live API/client + persistence | `limit + 1` overflow creates a continuation cursor and an exact final page omits it | PASS | With three fixture rows and `limit=2`, page 1 returned a non-null cursor. Page 2 contained the exact remaining row and `nextCursor: null`. |
| S9 | Live API/client + persistence | Repeated continuation requests preserve the same result sequence on a stable snapshot | PASS | The first continuation request returned the same single `qa-post-12` row and null cursor; no data changed during the run. |
| S10 | Live API/client | Interrupted or retried continuation resumes safely from the cursor | PASS WITH WARNINGS | Replaying the captured page-1 cursor returned the remaining row without duplication. An actual network interruption was not injected. |
| S11 | Live API/client + security | Unauthorized or missing workspace context cannot bypass tenant isolation | PASS | Missing bearer token returned HTTP 401. A valid token with a workspace cursor mismatch returned HTTP 400 before cross-tenant data was exposed. |
| S12 | Browser automation | Browser behavior for the calendar cursor flow | NOT APPLICABLE | Backend-only change; no browser surface is in scope. |
| S13 | Accessibility | Accessibility behavior for the calendar cursor flow | NOT APPLICABLE | Backend-only change; no rendered UI target. |
| S14 | Responsive/mobile | Responsive behavior for the calendar cursor flow | NOT APPLICABLE | Backend-only change; no rendered UI target. |
| S15 | Locale/internationalization | Locale behavior for cursor continuation and invalid-cursor errors | NOT APPLICABLE | No locale-dependent API contract is defined for this backend-only behavior. |
| S16 | Persistence/reload | Cursor continuation remains usable across application reload or process restart | PASS WITH WARNINGS | The cursor is self-contained and the replay worked against the live API. A process restart was not performed during this QA run. |

## 6. Findings and Risks

| ID | Severity | Finding | Status |
|---|---|---|---|
| QA-001 | P1 | Prior QA report was blocked solely by lack of a live target; that blocker is resolved for the local API. | Resolved by this live QA run |
| QA-002 | P2 | Exact live tied-`published_at` fixture, delimiter/blank-field HTTP variants, forced interruption, and process-restart checks were not independently exercised. | Accepted warning; repository tests and implementation verification cover the underlying contract |
| QA-003 | P2 | The QA principal is not a member of a second workspace, so cross-workspace live verification could only observe mismatch rejection, not a permitted `WS-B` comparison. | Accepted warning; live SQL binding and BDD/verification evidence cover tenant scoping |
| QA-004 | P2 | Full `just ci` remains inconsistent per `verify-report.md` because of a marketing Playwright startup failure in a later run. | Pre-existing verification warning; unrelated to this backend-only endpoint QA |

No unresolved CRITICAL, P0, or P1 product finding remains from this QA run.

## 7. Verdict

**PASS WITH WARNINGS — archive gate may proceed, subject to the archive agent rechecking the existing verification warning and accepted QA limitations.**

The live endpoint demonstrated cursor continuation, deterministic ordering, overflow/final-page behavior, malformed-cursor Problem Details, authentication enforcement, and cursor workspace provenance rejection. The remaining warnings are explicitly documented and do not represent an unresolved P0/P1 issue.

## 8. Recommended Next Action

Run `/sdd-archive` for `r2dbc-calendar-cursor-continuation`. Do not modify production code based solely on the accepted QA warnings unless a stronger cross-workspace fixture or fault-injection requirement is introduced.
