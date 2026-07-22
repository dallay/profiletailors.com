# Verification Report

## Change

- **Change**: `2026-07-01-publication-edit-not-found-contract`
- **Mode**: openspec
- **Final Verdict**: PASS WITH WARNINGS

> Narrow scope and correctness are confirmed; only the HTTP-boundary runtime test remains as a
> follow-up. Per orchestrator direction this change is NOT marked PASS — it is PASS WITH WARNINGS
> because the spec scenarios' covering runtime proof stops at the advice/exception layer and does
> not
> yet exercise the real WebFlux `ProblemDetail` response for the production endpoints (
`PATCH /api/publishing/publications/{id}`, sibling update-only operations). The blocker is
> documented inline in `PublishingControllersTest.kt` (lines 443–450): the production controller
> uses
> Spring API versioning (`@Version` + `ApiVersionStrategy`) which needs additional Spring Boot
> infrastructure to stand up in a standalone `ApplicationContext`. Future work can address this by
> extending
`server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/IntegrationTestBase.kt` with
> a publishing-scoped WebTestClient test.

## Completeness

| Artifact   | Status | Notes                                                                                                                  |
|------------|--------|------------------------------------------------------------------------------------------------------------------------|
| Proposal   | ✅      | Intent, scope, and rollback plan match the implementation.                                                             |
| Spec delta | ✅      | Add Requirement "Update-Only Publication Misses Return HTTP 404" with three scenarios (edit, sibling, create-capable). |
| Design     | ✅      | Narrow advice-layer mapping matches the design. Runtime proof at the HTTP boundary is a follow-up.                     |
| Tasks      | ✅      | All phases 1–4 marked complete; companion test evidence documented inline.                                             |

## Build / Test Evidence

| Command                                                                                                                        | Result | Notes                                                                                                                                                                                                              |
|--------------------------------------------------------------------------------------------------------------------------------|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `./gradlew :server:smp:test --tests "*PublishingProblemDetailsHandlerTest" --tests "*PublishingControllersTest" --rerun-tasks` | ✅ Pass | 10/10 advice tests passed; 14/14 controller tests passed; 0 failures, 0 errors.                                                                                                                                    |
| `./gradlew :server:smp:compileKotlin :server:smp:compileTestKotlin`                                                            | ✅ Pass | Publishing HTTP code and tests compile (warnings only, no errors).                                                                                                                                                 |
| Run-records                                                                                                                    | ✅ Pass | `server/smp/build/test-results/test/TEST-com.profiletailors.smp.publishing.infrastructure.http.PublishingProblemDetailsHandlerTest.xml` reports 10/10 green; `…PublishingControllersTest.xml` reports 14/14 green. |

## Spec Compliance Matrix

| Requirement / Scenario                                           | Evidence                                                                                                                                                                                                                                                                                                                                                                |                                                                            Runtime Test Passed | Judge                                                                              |
|------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------:|------------------------------------------------------------------------------------|
| Update-only edit miss returns HTTP 404, not 500                  | `PublishingProblemDetailsHandler.kt` (lines 45–51) maps `PublicationNotFoundException` to `404 ProblemDetail` with title "Publication not found"; `PublishingProblemDetailsHandlerTest.kt` (lines 53–61) asserts status/title/detail; `PublishingHandlers.kt` keeps the edit handler (line 458–459) throwing `PublicationNotFoundException` for active-workspace misses | Yes for advice mapping; not through real HTTP transport (blocker: `ApiVersionStrategy` wiring) | ⚠️ PARTIAL — advice proven, HTTP-boundary runtime test is follow-up                |
| Sibling update-only operations share the same not-found contract | `Delete` (line 575–576), `Cancel` (line 603–604), `Retry` (line 636–637), `Reschedule` (line 680–681) all throw `PublicationNotFoundException` after the same `findByWorkspaceAndId` lookup                                                                                                                                                                             |                                Yes for advice + handler-level; not through real HTTP transport | ⚠️ PARTIAL — shared exception path proven; HTTP-boundary runtime test is follow-up |
| Create-capable save flows remain unchanged                       | `CreatePublicationHandler.handle()` (line 286+) is create-capable, never throws `PublicationNotFoundException`; `PublishingControllersTest.create endpoint preserves create-capable flow semantics` keeps the create dispatch on `CreatePublicationCommand`                                                                                                             |                                                                                            Yes | ✅ COMPLIANT                                                                        |

## Correctness

| Area                                        | Result | Notes                                                                                                                                                                                                                           |
|---------------------------------------------|--------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Advice mapping narrowness                   | ✅      | Only `PublicationNotFoundException` gained 404 mapping. Other exception classes untouched.                                                                                                                                      |
| Update-only handler semantics               | ✅      | `Edit`, `Delete`, `Cancel`, `Retry`, `Reschedule` keep `findByWorkspaceAndId(...) ?: throw PublicationNotFoundException(...)` unchanged.                                                                                        |
| Create-capable flow isolation               | ✅      | `CreatePublicationHandler` is separate and does not use the new mapping.                                                                                                                                                        |
| HTTP contract proof at the WebFlux boundary | ⚠️     | Advice + manual controller exception path is proven; no `WebTestClient` proof that Spring serializes the 404 `ProblemDetail` end-to-end. Blocked by `ApiVersionStrategy` wiring — documented inline and in Engram memory #1500. |

## Design Coherence

| Design Decision                                      | Result | Notes                                                                                                                                                                                                           |
|------------------------------------------------------|--------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Map at `PublishingProblemDetailsHandler`             | ✅      | Implemented exactly as designed.                                                                                                                                                                                |
| Keep scope limited to `PublicationNotFoundException` | ✅      | No broader 404 mapping added.                                                                                                                                                                                   |
| Reuse Spring `ProblemDetail`                         | ✅      | Implemented with title/detail only.                                                                                                                                                                             |
| Verify via controller/WebFlux test                   | ⚠️     | Controller-level exception-path tests pass, but the design's stronger "controller/WebFlux" verification (real HTTP 404 from `WebTestClient`) is a follow-up because of `ApiVersionStrategy` wiring constraints. |

## Issues

### CRITICAL

None. The 404 contract is correctly wired at the advice layer and proven by passing runtime tests.

### WARNING

- **HTTP-boundary runtime test still missing**: the strongest runtime proof — a `WebTestClient`
  exercise against the real `PublishingPublicationController` PATCH/DELETE/cancel/retry/reschedule
  endpoints asserting status=404, title="Publication not found", and JSON `ProblemDetail` body — is
  not included. The blocker is documented inline in `PublishingControllersTest.kt` (lines 443–450):
  the production controller uses Spring `@Version` + `ApiVersionStrategy`, which requires additional
  Spring Boot infrastructure to stand up in a standalone `ApplicationContext`. Per orchestrator
  direction this is recorded as a follow-up rather than a CRITICAL because the advice mapping and
  the controller-level exception path are both proven, and the implementation is intentionally
  narrow. Action item: extend
  `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/IntegrationTestBase.kt`
  with a publishing-scoped WebTestClient test (or equivalent full-stack wiring), then rerun the
  targeted backend suite.

### SUGGESTION

- Consider whether publishing should follow the same `errorCode` extension pattern as
  `MediaServiceUnavailableException` (`MEDIA_SERVICE_UNAVAILABLE`) / `AssetNotReadyException` (
  `ASSET_NOT_READY`) so the frontend can disambiguate without parsing the title. (Open question from
  `design.md`.)
- When the HTTP-boundary WebTestClient test is added, also assert the response body is
  `application/problem+json` so a future serialization regression is caught.

## Verdict Table

| Finding                                                                                                                                                 | Judge A | Judge B | Severity | Status                |
|---------------------------------------------------------------------------------------------------------------------------------------------------------|---------|---------|----------|-----------------------|
| `PublishingProblemDetailsHandler` maps `PublicationNotFoundException` to 404 with title "Publication not found"                                         | ✅       | ✅       | INFO     | Confirmed             |
| Advice mapping unit test asserts status/title/detail (`PublishingProblemDetailsHandlerTest`)                                                            | ✅       | ✅       | INFO     | Confirmed             |
| Update-only handlers still throw workspace-scoped `PublicationNotFoundException`                                                                        | ✅       | ✅       | INFO     | Confirmed             |
| Create-capable `CreatePublicationHandler` is unchanged and not in the 404 mapping path                                                                  | ✅       | ✅       | INFO     | Confirmed             |
| Controller-level tests pass (14/14) without HTTP transport proof                                                                                        | ✅       | ✅       | INFO     | Confirmed             |
| Real WebFlux `WebTestClient` proof of `PATCH / DELETE → 404 ProblemDetail` is blocked by `ApiVersionStrategy` wiring in standalone `ApplicationContext` | ✅       | ✅       | WARNING  | Confirmed — follow-up |

## Follow-up

| Item                                                                                                                                                                                        | Why                                                                                                                             | How                                                                                                                                                                                                                                                                                             |
|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Add full-stack WebTestClient regression for `PATCH /api/publishing/publications/{id}` (and one sibling) returning 404 `ProblemDetail` when mediator surfaces `PublicationNotFoundException` | Closes the spec/Design "controller/WebFlux" verification gap and protects future refactors of `PublishingProblemDetailsHandler` | Extend `server/smp/src/test/kotlin/com/profiletailors/smp/integration/support/IntegrationTestBase.kt` with a publishing-scoped wiring (or a sibling `IntegrationTestBase`-derived class) and assert `expectStatus().isNotFound` plus `expectBody().jsonPath("title", "Publication not found")`. |
| Reconsider publishing-specific `errorCode` (e.g., `PUBLICATION_NOT_FOUND`)                                                                                                                  | Aligns with media's `errorCode`-bearing problem details                                                                         | Add `setProperty("errorCode", "PUBLICATION_NOT_FOUND")` in the advice handler and update the spec/design open question.                                                                                                                                                                         |
