# Proposal: LinkedIn Publishing MVP

## Intent

Enable the first real social publishing vertical slice for Profile Tailors by introducing LinkedIn
personal-profile publishing with durable scheduling, immediate publish, queue priority, retry
handling, and media delivery. This change turns the current IAM/workspace backend foundation into a
product-facing capability that proves the platform can manage connected social accounts and execute
workspace-scoped outbound publishing work.

The user need is clear: a workspace member should be able to connect a LinkedIn profile, create a
post with supported LinkedIn content formats, publish it now or later, and recover gracefully from
failed delivery without leaving the platform. The technical need is equally important: we need a
simple queue and provider-adapter model that is production-usable now but can scale later to
additional networks and stronger async infrastructure.

## Scope

### In Scope

- Add a new backend publishing bounded context for workspace-scoped social publishing behavior.
- Support LinkedIn OAuth connection for personal profiles with real provider integration plus
  test/development fakes.
- Persist connected social accounts, provider credentials metadata, publication drafts, publication
  jobs, delivery attempts, and media assets.
- Support publication modes `NOW`, `SCHEDULED_AT`, `NEXT_SLOT`, and priority queue ordering.
- Support editing and cancellation while a publication remains draft, queued, or scheduled and not
  yet claimed for delivery.
- Execute automatic retries for delivery failures, then mark the publication as failed and allow
  manual retry or rescheduling.
- Support backend-managed uploads and external media URLs for LinkedIn-supported formats in the MVP
  contract.
- Prepare the model for future approval workflows and LinkedIn page publishing without implementing
  them yet.

### Out of Scope

- LinkedIn page publishing in this change.
- Additional social providers beyond LinkedIn.
- Full approval workflow enforcement; only forward-compatible model seams are required.
- Advanced queue infrastructure such as Kafka, SQS, RabbitMQ, or hosted job systems.
- Rich analytics, inbox/community behavior, or cross-network content adaptation.
- Frontend marketing-site work or a full product UI unless needed only as API contract
  documentation.

## Approach

Adopt a hybrid multichannel-ready architecture: introduce a small generic `publishing` domain with
provider-agnostic contracts for social connections, social accounts, assets, publications,
schedules, jobs, attempts, and provider delivery adapters, then implement LinkedIn as the first
concrete provider adapter. Use Spring Modulith-compatible package boundaries, repo-local CQRS
handlers, R2DBC persistence, Liquibase migrations, and a simple database-backed scheduler/worker
design that can later be replaced by external queue infrastructure with minimal domain churn.

The first executable slice targets LinkedIn personal profiles only. OAuth, profile discovery, media
registration/upload, and publication delivery live in LinkedIn infrastructure adapters. Publication
state management, retry policy, queue ordering, and workspace authorization remain provider-neutral
in the publishing domain.

## Affected Areas

| Area                                                            | Impact   | Description                                                                                             |
|-----------------------------------------------------------------|----------|---------------------------------------------------------------------------------------------------------|
| `server/smp/src/main/kotlin/com/profiletailors/smp/publishing/` | New      | New publishing bounded context with domain, application, infrastructure, and module metadata.           |
| `server/smp/src/main/kotlin/com/profiletailors/smp/platform/`   | Modified | Wire scheduler/worker bootstrap and shared request or clock seams as needed.                            |
| `server/smp/src/main/kotlin/com/profiletailors/smp/identity/`   | Modified | Reuse authenticated principal seams and add provider-auth integration hooks where needed.               |
| `server/smp/src/main/kotlin/com/profiletailors/smp/tenancy/`    | Modified | Reuse active workspace resolution and enforce workspace-scoped publishing ownership.                    |
| `server/smp/src/main/resources/application.yaml`                | Modified | Add publishing and LinkedIn provider configuration properties.                                          |
| `server/smp/src/main/resources/db/changelog/`                   | Modified | Add Liquibase migrations for publishing, assets, jobs, attempts, and social connections.                |
| `server/smp/src/test/kotlin/com/profiletailors/smp/publishing/` | New      | Add unit, repository, controller, and integration tests for the new slice.                              |
| `openspec/specs/iam/spec.md`                                    | Modified | Workspace authorization may need to acknowledge the new publishing capability.                          |
| `openspec/specs/tenancy/spec.md`                                | Modified | Tenancy behavior may need to clarify workspace ownership of connected social accounts and publications. |
| `openspec/changes/linkedin-publishing-mvp/`                     | New      | Proposal, specs, design, and tasks for this change.                                                     |

## Risks

| Risk                                                                    | Likelihood | Mitigation                                                                                                                             |
|-------------------------------------------------------------------------|------------|----------------------------------------------------------------------------------------------------------------------------------------|
| LinkedIn API constraints or permission gaps differ between environments | High       | Keep provider logic isolated behind adapters, use explicit capability mapping, and keep fakes for deterministic development/testing.   |
| Queue semantics become too LinkedIn-specific                            | Medium     | Keep scheduling, retry, and job state in provider-neutral domain types and treat LinkedIn format rules as adapter concerns.            |
| Media workflows expand scope unexpectedly                               | Medium     | Define a stable asset contract now while limiting executable MVP behavior to formats proven in tests and documented capability checks. |
| Scheduler polling causes duplicate claims under concurrency             | Medium     | Use database-backed claim semantics with explicit job status transitions and lock ownership timestamps.                                |
| Approval-workflow deferral leaks into the first implementation          | Medium     | Model publication lifecycle extensibility points without enforcing review states yet.                                                  |
| Secrets and OAuth credentials complicate local development              | Medium     | Add fake adapters and test fixtures so local development does not require live LinkedIn credentials.                                   |

## Rollback Plan

If the change causes instability, revert the new publishing module, Liquibase changelog entries,
configuration properties, and publishing endpoints together. Because the feature is additive,
rollback can disable the module cleanly by removing the new Spring wiring and database migrations
from the master changelog before release; if already applied in a shared environment, rollback
should disable endpoints and worker scheduling first, then follow a forward-fix migration strategy
instead of destructive table removal.

## Dependencies

- LinkedIn developer application credentials and approved scopes for personal-profile publishing.
- Existing authenticated USER principal flow and workspace-context enforcement in `server/smp`.
- PostgreSQL/R2DBC persistence and Liquibase baseline migration workflow.
- Spring WebFlux HTTP client support for provider integration.
- Existing test infrastructure for H2, Testcontainers PostgreSQL, WebTestClient, and Modulith
  verification.

## Success Criteria

- [ ] A workspace-authenticated user can connect a LinkedIn personal profile through the supported
  OAuth flow and persist the resulting connected account in workspace scope.
- [ ] The backend accepts a publication draft with LinkedIn-targeted content and can enqueue it for
  immediate, scheduled, next-slot, or priority delivery.
- [ ] A simple database-backed worker can claim due jobs, attempt LinkedIn delivery, record
  attempts, retry failures automatically, and mark exhausted jobs as failed.
- [ ] A queued or scheduled publication can be edited or cancelled before delivery claim, and a
  failed publication can be retried manually or rescheduled.
- [ ] The implementation includes deterministic fake provider paths for local development and
  automated tests without live LinkedIn credentials.
- [ ] The architecture keeps page publishing, approvals, extra providers, and external queue
  migration explicitly deferred without blocking the MVP slice.
