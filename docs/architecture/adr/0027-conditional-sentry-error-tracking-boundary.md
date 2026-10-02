# ADR-0027: Conditional Sentry Error-Tracking Boundary

- Status: Accepted
- Date: 2026-10-02
- Decision owners: Principal Architect
- Scope: `apps/web/app`, `apps/web/admin`, `server/smp` infrastructure, release workflows, and `shared/web` sanitizer
- Supersedes: None
- Superseded by: None
- Related:
  - [ADR-0018: Regional Data Residency and Controlled Transfer Architecture](0018-regional-data-residency-and-controlled-transfer-architecture.md)
  - [ADR-0021: Shared Operational Event Safety Boundary](0021-operational-event-safety-boundary.md)
  - [ADR-0022: Release-Driven Frontend Deployment to Cloudflare Pages](0022-release-driven-frontend-deployment.md)
  - [Issue #1273](https://github.com/dallay/profiletailors.com/issues/1273)
  - [Sentry Error Tracking Runbook](../../monitoring/sentry-runbook.md)

## Context

Profile Tailors needs issue-oriented grouping and release correlation for unexpected browser and
backend errors. The existing Prometheus/Micrometer, Grafana, Loki, structured logs, and
`OperationalEventSink` remain the owners of metrics, dashboards, logs, and framework-free
operational events. Sentry must not become a business-event bus or a dependency of core application
logic.

The customer app and SMP API are cross-origin in production. Distributed browser-to-API Sentry
tracing would require trace-header propagation and matching CORS support. Production Sentry
projects, DSNs, organizational scrubbing, processing region, agreements, retention, and alert
configuration are external facts and are not evidenced by this repository.

## Decision drivers

- Capture unexpected errors with a stable component release identifier.
- Keep issue tracking separate from operational events, metrics, and logs.
- Minimize personal and confidential data before telemetry leaves the application.
- Preserve hexagonal dependency direction and the independent marketing surface.
- Keep production activation and Replay subject to explicit operator configuration and privacy
  evidence.

## Decision

Profile Tailors MAY use Sentry as a DSN-gated issue tracker for the customer Vue app, admin Vue app,
and SMP backend. The Astro marketing site MUST remain uninstrumented by this integration.

Sentry SDKs and adapters MUST remain in frontend bootstrap/build infrastructure or SMP
infrastructure/configuration. They MUST NOT be added to Vue feature domain/application code, SMP
domain/application modules, `shared/observability`, or the `OperationalEventSink` contract. The
framework-neutral `shared/web` package MAY contain only an SDK-independent sanitizer shared by the
two Vue applications.

Each surface MUST sanitize its event data before transmission. Request/response bodies, cookies,
sensitive headers, URL query strings/fragments, raw IP addresses, emails, credentials, and
unbounded free-form fields MUST be removed or redacted. Only explicitly allowlisted bounded opaque
identifiers may remain. SMP MUST clear user identity, request metadata, breadcrumbs, arbitrary
extras, and non-allowlisted tags before sending an event.

SMP Sentry performance tracing, logs, metrics, profiling, Cron Monitoring, and distributed
trace-header propagation MUST remain disabled. Browser route tracing MAY be used without sending
Sentry trace headers to the cross-origin API. Micrometer/Prometheus, Grafana, Loki, and
`OperationalEventSink` MUST retain their existing behavior.

Release identifiers MUST include the component version and exact full build SHA. Frontend source
maps MAY be uploaded by release CI to the matching Sentry project only; they MUST be hidden during
build, deleted after upload, and absent from public assets. The Sentry upload token MUST remain a
CI-only secret and MUST NOT enter browser assets, Docker build arguments, or runtime images.

Session Replay MUST be disabled by default. Admin MUST NOT enable Replay. The customer app MAY
enable error-only Replay only after privacy and quota approval and a synthetic Replay check proves
that initial/visited URLs and recorded fields do not expose queries, fragments, emails, credentials,
or token-bearing path segments. In the installed `@sentry/replay@11` API, `beforeAddRecordingEvent`
does not process rrweb DOM/meta events; if Replay URL privacy cannot be verified independently, the
Replay flag MUST remain false.

Code-level integration MUST NOT be represented as an active production processor. Production
activation requires approved Sentry projects and DSNs, organization/project-side data and IP
scrubbing, region and retention review, contractual approval, alert ownership, and verification
evidence recorded in the compliance inventory and runbook.

## Scope and boundaries

| Surface | Sentry responsibility | Boundary |
| --- | --- | --- |
| `apps/web/app` | Browser errors, route tracing, release, optional error-only Replay | App bootstrap, shared sanitizer, and release build only |
| `apps/web/admin` | Browser errors, route tracing, release | Admin bootstrap and release build; no Replay |
| `server/smp` | Unexpected WebFlux exceptions and release | Spring Boot 4 infrastructure configuration and event sanitizer |
| `apps/web/marketing` | None | No Sentry SDK or telemetry integration |
| `shared/web` | SDK-independent sanitization | No Sentry imports or surface-specific behavior |
| `shared/observability` | Existing framework-free operational event contract | No Sentry SDK or error-tracking transport |

The Level 2 C4 diagram is not changed until an external Sentry recipient and production activation
are evidenced. The current diagram describes the selected production topology; conditional code
and its privacy boundary are recorded here, in the observability contracts, and in compliance
documentation.

## Alternatives considered

### Extend `OperationalEventSink`

- Description: Send exception issues through the shared operational-event contract.
- Advantages: Reuses an existing abstraction.
- Disadvantages: Couples framework-free event semantics to a provider issue model and exception
  transport.
- Reason rejected: The existing contract is for operational events and must remain provider-neutral.

### Replace metrics and logs with Sentry

- Description: Use Sentry as the primary observability backend.
- Advantages: One vendor interface for several observability features.
- Disadvantages: Replaces established local monitoring contracts, adds unnecessary data flows, and
  expands scope beyond issue-oriented errors.
- Reason rejected: Prometheus/Micrometer, Grafana, Loki, and `OperationalEventSink` already own
  these responsibilities.

### Enable distributed tracing across browser and SMP

- Description: Propagate Sentry trace headers from the browser to the API and correlate backend
  spans.
- Advantages: End-to-end trace correlation.
- Disadvantages: Requires cross-origin trace targets and CORS header policy; SMP performance
  tracing is not part of this initial slice.
- Reason rejected: Keep the first slice focused on errors and release correlation; use existing
  backend metrics for performance.

### Keep application logs only

- Description: Continue relying on logs without an issue tracker.
- Advantages: No new provider dependency or telemetry recipient.
- Disadvantages: Does not provide reliable grouping, release correlation, or symbolicated frontend
  stack traces.
- Reason rejected: Does not satisfy the error-investigation requirement in Issue #1273.

## Consequences

### Positive

- Unexpected errors can be grouped and correlated to exact component releases when Sentry is
  configured.
- Existing metrics, logs, operational events, and application boundaries remain intact.
- Sanitization is tested independently from SDK types and applied at each transport boundary.

### Negative

- Frontend source-map upload and backend/browser event delivery require separately provisioned
  projects, DSNs, permissions, and operator configuration.
- Sentry is not considered an active processor until external configuration and legal evidence are
  reviewed.

### Risks

- A misconfigured server-side scrubber, an unreviewed Replay activation, or an unexpected SDK
  payload field could expose sensitive data.
- Unknown Sentry region, retention, agreements, and recipient configuration prevent production
  activation until resolved.

### Accepted trade-offs

- Sentry source-map uploads are conditional on a configured DSN and CI credentials. A configured
  DSN with invalid or missing upload configuration fails the release build; without a DSN, the
  integration remains disabled and source maps are not generated.
- Replay remains disabled until a real, synthetic privacy check is recorded; this may delay Replay
  availability without delaying error-only event tracking.

## Compliance and enforcement

- `shared/web/utils/sentry-sanitizer.test.ts` verifies frontend removal of sensitive payloads,
  credentials, IPs, emails, and URL query/fragment data.
- `server/smp/src/test/kotlin/com/profiletailors/smp/observability/infrastructure/sentry/SentryEventPrivacySanitizerTest.kt`
  verifies SMP event sanitization before its `BeforeSendCallback`.
- App/admin Vite configuration validates DSN, project, organization, and token requirements before
  source-map upload. The release workflows keep the token out of build arguments and browser
  assets.
- The compliance inventory and Sentry runbook record conditional status and external activation
  evidence.

## Verification

- Unit tests, type checks, builds, and static analysis pass for the changed surfaces before merge.
- Production assets contain the full component release and no public `.map` files or CI-only
  secrets.
- Sentry test projects receive synthetic errors with readable source-mapped frontend stacks and
  privacy-safe event payloads before production DSNs are enabled.
- Replay activation requires separate URL/data scrubbing evidence; otherwise its flag remains
  disabled.

## Migration or remediation

No existing Sentry events or provider contract are migrated. To disable delivery, remove the
affected DSN and redeploy. Remove or clear the app Replay flag independently. Existing metrics,
logs, and operational-event adapters require no migration.

## Follow-up actions

- [ ] Provision and review Sentry projects, DSNs, server-side scrubbing, processing region,
  retention, contractual terms, and alert ownership before production activation.
- [ ] Verify source-map upload and event symbolication using a non-production Sentry project.
- [ ] Keep app Replay disabled until a synthetic privacy test verifies URLs and recorded fields.

## Revisit conditions

Revisit this decision before enabling cross-origin trace propagation, adding Sentry event transport
to the operational-event boundary, activating Replay without the stated privacy evidence, selecting
a provider region that conflicts with ADR-0018, or adding a new Sentry surface such as Marketing.
