# Proposal: Version badge in UI frontends

## Overview

Operators need to identify which deployed build is in front of them without leaving the UI. Today
the deployed app version, git SHA, and build time are only available in Dokploy release notes and CI
artifacts, which makes it harder to confirm which artifact is running in production during incident
triage or QA.

## Changes

Surface a compact build identifier in every authenticated UI surface so operators can read it at a
glance.

Affected frontends:

- `apps/web/app` — sidebar footer area, always visible while authenticated
- `apps/web/admin` — sidebar footer, always visible while authenticated
- `apps/web/marketing` — body footer, on every page except the landing pages

The implementation reads build-time constants only. No backend endpoint, no analytics, no user
interaction.

Out of scope:

- Public landing page of `apps/web/marketing` (no badge)
- Backend `/api/version` endpoint (not requested)
- Copy-to-clipboard or click interaction (only static reading)
- Different content per environment (build-time, not runtime)

## Usage

The badge shows the deployed version from each frontend's `package.json` and the short git SHA of
the build. Hovering the badge exposes the build timestamp.

The landing page is excluded because it is the unauthenticated marketing surface and intentionally
omits implementation details.

## Troubleshooting

If the displayed build information is unexpected, verify the frontend's build-time constants and the
`GIT_SHA` override used for that build. The badge does not query a runtime or backend version
endpoint.

## References

- [Implementation tasks](./tasks.md)
- [`scripts/compute-build-info.mjs`](../../../scripts/compute-build-info.mjs)
