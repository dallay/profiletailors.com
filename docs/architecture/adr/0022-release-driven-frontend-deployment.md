# ADR 0022: Release-Driven Frontend Deployment to Cloudflare Pages

**Last Updated:** 2026-09-26

## Overview

Cloudflare Pages production deployments for `app`, `admin`, and `marketing` frontends are gated exclusively behind Release Please component release tags, decoupling deployment from continuous integration merges to `main`.

## Changes

### What changed

- **`deploy-app`, `deploy-admin`, `deploy-landing` jobs** in `.github/workflows/release-please.yml`: each checks out the exact release SHA, injects `GIT_SHA`, builds, uploads to Cloudflare Pages via `wrangler-action`, and verifies the live endpoint serves the correct version badge.
- **`extract-release-info.mjs`**: Node.js ESM helper that resolves version and SHA from Release Please outputs and exports GitHub Actions matrix. Replaces any prior shell-based approaches.
- **wrangler.toml alignment**: Pages project references updated to match canonical Cloudflare targets (`app-profile-tailors`, `profiletailors`, `profiletailors-admin`).
- **`docs/infrastructure/cloudflare-deployment.md`**: operator runbook for dashboard configuration and rollback.
- **`docs/production-secrets.md`**: `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` secret specifications.

## Usage

The `app`, `admin`, and `marketing` (landing) frontends were previously eligible for production deployment automatically upon push or merge to the `main` branch through Cloudflare's Git integration.

In a monorepo utilizing independent component releases via Release Please, this approach compromises release governance: a merge affecting one component (such as a backend service or documentation change) can inadvertently trigger frontend production builds, potentially publishing unversioned or untested changes.

### Decision

We establish GitHub releases created by Release Please as the sole deployment boundary for Cloudflare-hosted frontends:

1. **Explicit Component Deployment Boundary**: A frontend (`apps/web/app`, `apps/web/admin`, `apps/web/marketing`) is built and deployed to Cloudflare Pages production only when Release Please publishes an official release tag (`app@<version>`, `admin@<version>`, or `landing@<version>`).
2. **Decouple Integration from Deployment**: Merging to `main` remains the continuous integration state, but does not deploy static frontends directly to production. Cloudflare automatic production deployments from `main` are disabled across all three Pages projects.
3. **Deterministic SHA-Pinned Builds**: Deployment workflows check out the exact commit SHA associated with the Release Please release tag, eliminating race conditions or drift relative to the latest `main` HEAD.
4. **Traceable Build Metadata**: The exact release commit SHA is exported to `GIT_SHA`, ensuring frontend version badges render the accurate version and short commit SHA corresponding to the release artifact.
5. **Least-Privilege Cloudflare Authentication**: Deployments authenticate through GitHub Actions secrets (`CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`) with access strictly scoped to editing the target Pages projects.
6. **Automated Deployment Verification**: Following upload via Wrangler, the deployment workflow verifies the live endpoint and confirms that the version badge matches the expected release version and short SHA.

### Consequences

#### Positive

- **Strong Release Governance**: Deployments are intentional, auditable, and strictly tied to semantic version releases and changelogs.
- **Component Isolation**: Releasing one component does not rebuild, re-test, or re-deploy unaffected frontends.
- **Deterministic Traceability**: Operators can trace any running production UI directly back to its immutable release tag and Git SHA via the on-screen version badge.
- **Preview Stability**: Pull request preview deployments may remain active without risking unintended production promotion.

#### Negative / Trade-offs

- **Manual Initial Configuration**: Operators must manually disable automatic production branch builds in the Cloudflare Pages dashboard for each project.
- **Multi-Step Release Cycle**: Changes require merging a release pull request before reaching production, rather than immediately deploying on merge to `main`.

## Troubleshooting

### Deployment job not triggered after release

Confirm `release-please` created the component tag (`app@x.y.z`, `admin@x.y.z`, or `landing@x.y.z`). Check the `release-please` workflow run outputs for `app--release_created: true`. If false, the component has no code changes in the release.

### GitHub Actions fails to access Cloudflare secrets

Ensure `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` are configured as **Environment secrets** scoped to the `PROD` environment and that the deploy job declares `environment: PROD`. Repository secrets do not satisfy environment-scoped secret requirements.

### Production verification probe keeps failing

Cloudflare Pages CDN propagation can take up to 75 seconds. The workflow retries 5 times with 15-second intervals. If all attempts fail, check the Cloudflare Pages dashboard for the deployment status and verify the `version` and `short_sha` badge values match the release tag.

### Build fails with missing environment variable

Ensure `PT_PRODUCTION_API_URL` GitHub variable is set for the `PROD` environment, or falls back to the hardcoded `https://api.profiletailors.com` default for the admin SPA.

## References

- [`.github/workflows/release-please.yml`](../../../.github/workflows/release-please.yml)
- `scripts/extract-release-info.mjs`
- `docs/infrastructure/cloudflare-deployment.md`
- `docs/production-secrets.md`
