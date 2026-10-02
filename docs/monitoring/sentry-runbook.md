# Sentry Error Tracking Runbook

## Overview

Sentry is an optional issue-tracking integration for the customer dashboard, platform admin, and
SMP backend. It does not replace Prometheus, Micrometer, Grafana, Loki, or `OperationalEventSink`.
The Astro marketing site remains uninstrumented. Backend Sentry performance tracing, Sentry Logs,
Metrics, Profiling, and Cron Monitoring are not enabled.

The code is conditional. A Sentry project or SDK dependency is not evidence that production events
are being sent or that Sentry is an approved current processor.

## Changes

- `app`, `admin`, and `smp` use separate projects: `profiletailors-app`, `profiletailors-admin`,
  and `profiletailors-smp`.
- Frontend releases use `<component>@<package-version>+<full-build-SHA>`. The SMP image receives
  `SENTRY_RELEASE` from the release tag and exact source revision.
- App and admin sanitize events, breadcrumbs, spans, and optional Replay custom events before
  submission. SMP removes request bodies, cookies, headers, query strings, user data, breadcrumbs,
  free-form extras, and unsafe tags before error events are sent.
- Session Replay is unavailable in admin. In app, it stays off unless the production build receives
  `VITE_SENTRY_REPLAY_ON_ERROR=true`; privacy and quota approval must precede that setting.

## Usage

### Provisioning prerequisites

Before enabling production traffic:

1. Create the three projects using the slugs above and assign project ownership and alert owners.
2. Enable organization- and project-side data scrubbing and raw-IP scrubbing. Record the selected
   Sentry region, retention, subprocessors, contractual terms, and approval evidence in the
   controlled compliance record.
3. Configure the repository secret `SENTRY_AUTH_TOKEN` with only the project release-upload and
   organization-read scopes. Configure repository variables `SENTRY_ORG`, `SENTRY_APP_DSN`, and
   `SENTRY_ADMIN_DSN`. These are needed by both the Cloudflare Pages release jobs and the dashboard
   image build; each workflow pins its project slug to the corresponding app or admin build.
   Optionally set `SENTRY_APP_REPLAY_ON_ERROR=true` only after privacy and quota approval; otherwise
   leave it unset or false.
4. For Compose or Swarm SMP deployments, set `SENTRY_DSN` and `SENTRY_ENVIRONMENT=production` in
   the deployment environment. The SMP release image provides `SENTRY_RELEASE` using the SMP
   component version and full build SHA.

The browser DSNs are public ingestion identifiers, not credentials. `SENTRY_AUTH_TOKEN` is a
private CI secret: never prefix it with `VITE_`, pass it as a Docker build argument, add it to a
runtime environment, or allow it into static assets. An empty SMP DSN disables event delivery;
development and test profiles explicitly disable Sentry.

### Verify frontend releases and source maps

1. Use an approved non-production deployment or release-equivalent CI build configured with isolated
   test Sentry projects before enabling production DSNs. Never change production Sentry variables
   just to test a release that will be promoted to production. If no safe non-production path is
   available, defer live source-map/event verification and production activation until one is
   agreed. The SMP image release also builds the self-hosted dashboard and uses a BuildKit secret
   mount for `SENTRY_AUTH_TOKEN`.
2. Confirm the build reports the expected component release, such as
   `app@0.3.16+<full-SHA>` or `admin@0.0.15+<full-SHA>`.
3. Confirm source maps upload to the matching project. If the DSN is configured but token,
   organization, project, or upload is invalid, the build must fail and the deployment must not
   proceed. Without a DSN, the build emits a warning and produces no source maps.
4. Inspect the deployed `dist` output and the immutable Pages deployment. No `.map` files should be
   present after a successful upload.
   For the self-hosted dashboard image, confirm `.map` files are absent from the final image and the
   CI token is not in the Docker image configuration or filesystem.
5. From the isolated deployment, trigger a synthetic, non-sensitive browser exception and verify
   that its event has the expected release and a source-mapped stack trace.
6. Use only synthetic values to inspect the stored event for query parameters, credentials, email,
   IP addresses, cookies, and request/response bodies. Client sanitization tests are mandatory even
   when organization-side scrubbing is enabled.

Replay has a stricter gate: in `@sentry/replay@11`, `beforeAddRecordingEvent` applies only to custom
Replay events; rrweb DOM/meta recording events, including URL metadata, do not pass through it. Keep
`SENTRY_APP_REPLAY_ON_ERROR` unset/false unless a Replay-specific synthetic capture proves the
initial and visited URLs contain no query strings, fragments, email addresses, credentials, or
token-bearing path segments. If the configured Sentry project cannot remove those values from
Replay metadata, leave Replay disabled; ordinary error events remain available.

### Verify SMP capture and privacy

1. In a non-production deployment with the `profiletailors-smp` DSN, trigger a controlled
   unexpected exception using an approved test harness. Do not add a production diagnostic route.
2. Confirm the event has the `smp@<version>+<full-SHA>` release and the expected environment.
3. Inspect the event and confirm request bodies, query strings, cookies, headers, user identity,
   breadcrumbs, and arbitrary extras are absent; only bounded safe identifiers may remain.
4. Exercise expected validation, authentication, authorization, not-found, conflict, and rate-limit
   outcomes. They should remain API outcomes rather than deliberately captured issues.
5. Confirm the existing Prometheus endpoint, structured logs, `OperationalEventSink`, and Grafana/Loki
   dashboards still behave as before. SMP Sentry tracing stays disabled.

### Configure low-noise alerts

Create per-project production alerts for new issues and regressions. Add frequency or affected-session
thresholds after a production baseline exists. Do not mirror existing Prometheus SLO or error-budget
alerts unless Sentry supplies a distinct incident signal. Verify delivery with a synthetic issue and
record the alert destination and owner.

## Troubleshooting

- **Frontend build warns that Sentry is disabled:** the corresponding DSN environment variable is
  absent. No source maps are generated or uploaded.
- **Frontend build fails before deployment:** when a DSN is configured, check the `PROD` environment
  secret `SENTRY_AUTH_TOKEN`, variable `SENTRY_ORG`, the fixed per-app project slug, and the Sentry
  upload error. Do not weaken the build gate or publish the `.map` files as a workaround.
- **Events have no readable source locations:** compare the event release and debug ID with the
  uploaded artifacts in the matching Sentry project.
- **SMP events have no release:** confirm the image was built through `release-image.yml` with its
  `SENTRY_RELEASE` build argument. Do not derive the SMP release from a moving `latest` image tag.
- **Unexpected data appears:** disable the relevant DSN, retain the synthetic event as evidence,
  review both the client sanitizer and server-side data scrubbing, then verify again before
  re-enabling traffic.

## Rollback

Unset the affected app DSN, SMP `SENTRY_DSN`, and app Replay flag, then redeploy. This stops new
Sentry delivery without changing application behavior or the existing metrics/logging stack. Keep
the upload token limited to CI and revoke it if it is exposed.

## References

- [Observability Contracts](../observability-contracts.md)
- [Shared Observability Usage](../observability-usage.md)
- [Data Inventory](../compliance/data-inventory.md)
- [Controller–Processor Matrix](../compliance/controller-processor-matrix.md)
- [Production Docker Compose](../infrastructure/production-docker-compose.md)
- [Production Docker Swarm](../infrastructure/production-docker-swarm.md)
