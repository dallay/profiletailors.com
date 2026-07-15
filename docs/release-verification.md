# Release Verification

**Last Updated:** 2026-07-15
**Status:** Active

## Overview

This document defines the verification process for Profile Tailors MVP release
readiness. It covers CI pipeline verification, local release verification, and
manual smoke test instructions for the core publishing journey.

The release gate is tracked in
[DALLAY-466 — MVP Release Checklist](https://linear.app/dallay/issue/DALLAY-466/launch-readiness-mvp-release-checklist).

## Changes

This document was created as part of MVP launch readiness to document the CI
and local verification steps required before declaring the product
release-ready.

## CI Verification

### Full CI Pipeline

Run the complete CI pipeline before any release push:

```bash
just ci
```

This runs the full pipeline: gitleaks, lint, unit tests, build, and E2E tests.
This is mandatory before pushing to `main` or merging release PRs.

### Fast Local CI

For quick local iteration without E2E or Postgres BDD:

```bash
just ci-local
```

This covers: Biome lint, Vitest unit tests, backend detekt, backend fast tests,
and production builds. Use this for rapid feedback during development.

### Full Local CI (with Postgres)

When Postgres BDD tests are needed locally:

```bash
just infra-up        # Start Postgres + services
just ci-full         # ci-local + Postgres BDD suite
just infra-down      # Stop containers after
```

### CI Check Coverage

The full CI pipeline verifies:

| Check | Scope | Tool |
|-------|-------|------|
| Secret leaks | All commits | gitleaks |
| Lint | Frontend | Biome |
| Lint | Backend | detekt + spotless |
| Unit tests | Frontend | Vitest (900+ tests) |
| Unit tests | Backend | Gradle test |
| BDD tests | Backend | Cucumber (fast + Postgres) |
| Build | Frontend | Vite production build |
| Build | Backend | Gradle build |
| E2E | Marketing site | Playwright |
| E2E | Dashboard (4 shards) | Playwright mocked |
| E2E | Media library | Playwright mocked |
| Security | Backend | CodeQL, Semgrep, Trivy |
| Security | Frontend | Semgrep, Biome security |
| Coverage | Patch | Codecov |
| Quality | Full | SonarCloud |

### Pre-commit Hooks

Lefthook hooks run automatically on commit and push:

- **pre-commit**: backend-detekt, backend-spotless, frontend-biome, gitleaks
- **pre-push**: backend-spotless, backend-test, gitleaks-range

If hooks are disabled, CI catches the equivalent checks. No risk in skipping
hooks locally.

## Local Release Verification

### Prerequisites

```bash
just setup          # Install deps, hooks, agentsync
cp .env.example .env  # If not already done
./bin/setup-env.sh    # Create subproject symlinks
```

### Step 1: Backend Tests

```bash
just backend-test-fast
```

Fast unit tests exclude BDD. For full coverage including BDD:

```bash
just infra-up
just backend-bdd-postgres
just backend-bdd-fast
just infra-down
```

### Step 2: Frontend Tests

```bash
just frontend-test
just frontend-lint
```

### Step 3: E2E Tests

```bash
just frontend-test-e2e
```

For the scheduler-specific E2E config:

```bash
npx playwright test -c e2e/playwright.scheduler.config.ts
```

### Step 4: Full Build

```bash
just frontend-build
just backend-build
```

### Step 5: Dev Server Smoke

```bash
just serve
```

Verify the dashboard loads at `https://pt-app.localhost` (requires
[Portless](portless-setup.md)) and the backend responds at
`http://localhost:7638`.

## Manual Smoke Test

The manual smoke test validates the core publishing journey end-to-end with a
clean test account. This verifies the primary product loop works without
mocked dependencies.

### Prerequisites

- LinkedIn OAuth app configured with valid `SMP_LINKEDIN_CLIENT_ID` and
  `SMP_LINKEDIN_CLIENT_SECRET` in `.env`
- `SMP_PUBLISHING_WORKER_ENABLED=true` in `.env`
- `PUBLISHING_CREDENTIALS_KEY` set (32-byte base64 key)
- `SMP_CORS_ALLOWED_ORIGINS` includes the dashboard URL
- Postgres running: `just infra-up`
- Backend running: `just backend-run`
- Frontend running: `just dev-frontend`

### Test Steps

| Step | Action | Expected Result |
|------|--------|------------------|
| 1 | Register a new account or log in | Dashboard loads |
| 2 | Verify email if not verified | Email status shows verified |
| 3 | Navigate to Scheduler | Scheduler view loads with empty state or existing posts |
| 4 | Click "Connect LinkedIn profile" | LinkedIn OAuth redirect initiates |
| 5 | Complete LinkedIn OAuth | Channel appears in sidebar with LinkedIn badge |
| 6 | Click "Create Post" | Composer opens |
| 7 | Write post content (short, ~100 chars) | LinkedIn preview shows content |
| 8 | Write long content (>3000 chars) | Preview shows "See more" truncation |
| 9 | Attach an image (JPEG, <10MB) | Image preview appears |
| 10 | Select "Schedule for later" and pick a time | Date/time picker saves value |
| 11 | Save the scheduled post | Calendar shows the post on the selected date |
| 12 | Open the scheduled post from the calendar | Post detail modal shows content, media, schedule |
| 13 | Click "Publish Now" | Status transitions to PROCESSING then PUBLISHED |
| 14 | Verify on LinkedIn | Post appears on the connected LinkedIn profile |
| 15 | Create another post and publish immediately | Post appears on LinkedIn within seconds |
| 16 | Create a post with an unsupported media type (PDF) | Validation error rejects the file |
| 17 | Disconnect LinkedIn, then attempt to publish | Publication transitions to BLOCKED with reconnect CTA |
| 18 | Reconnect LinkedIn | Connection restores, blocked publication can be retried |

### Failure Path Verification

| Step | Action | Expected Result |
|------|--------|------------------|
| 1 | Publish with rate-limited provider (simulate 429) | Failure modal shows localized "provider busy" message with retry action |
| 2 | Publish with expired LinkedIn token | Failure modal shows "reconnect your account" with reconnect CTA |
| 3 | Publish with deleted media | Failure modal shows "media not found" with re-upload guidance |
| 4 | Retry a failed publication | Retry action triggers new delivery attempt |
| 5 | Delete a failed publication | Publication removed from calendar |
| 6 | Open a historical failed publication (pre-DALLAY-484) | Safe generic failure message shown, no raw exception names |

### Acceptance Criteria

The smoke test passes when:

- All happy path steps complete without errors
- LinkedIn post appears on the connected profile
- All failure paths show localized, safe messages
- No raw exception names, stack traces, or internal identifiers are visible
- Every failure provides a recommended recovery action
- Scheduled posts appear on the calendar at the correct time
- Calendar layout does not break with long post content

### Evidence Capture

After completing the smoke test, capture:

1. **Screenshots** of key steps (dashboard, composer, calendar, failure modal)
2. **Test run output** from E2E suites
3. **Backend logs** showing successful publication webhooks

Attach the evidence to
[DALLAY-465](https://linear.app/dallay/issue/DALLAY-465/e2e-validate-mvp-core-publishing-journey-end-to-end)
or the release PR.

## Troubleshooting

### LinkedIn OAuth redirect fails

Verify `SMP_LINKEDIN_REDIRECT_URI` matches the LinkedIn app's authorized
redirect URI. Check `SMP_CORS_ALLOWED_ORIGINS` includes the dashboard URL.

### Publishing worker does not process

Confirm `SMP_PUBLISHING_WORKER_ENABLED=true` in `.env`. The worker polls at
`SMP_PUBLISHING_WORKER_POLL_INTERVAL` (default 30s). Wait for the next poll
cycle or restart the backend.

### Publication stays in PROCESSING

Check backend logs for delivery attempt errors. The worker may be retrying with
backoff. If retries are exhausted, the publication transitions to `FAILED`
with a canonical category.

### Dashboard does not load

Ensure Portless is running (`portless proxy start`). The dashboard uses named
local HTTPS URLs, not ports.

## References

- [Getting Started](getting-started.md) — local setup and bootstrap
- [Production Secrets](production-secrets.md) — required secrets and env vars
- [Publishing Failure Modes](publishing-failure-modes.md) — failure taxonomy
- [Test Tags and Environment](testing/test-tags-and-env.md) — test
  configuration
- [Portless Setup](portless-setup.md) — named local URLs
- [AGENTS.md](../AGENTS.md) — full command reference