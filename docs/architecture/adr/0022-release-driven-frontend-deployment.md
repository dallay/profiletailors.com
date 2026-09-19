# ADR 0022: Release-Driven Frontend Deployment to Cloudflare Pages

## Context

The `app`, `admin`, and `marketing` (landing) frontends were previously eligible for production deployment automatically upon push or merge to the `main` branch through Cloudflare's Git integration. 

In a monorepo utilizing independent component releases via Release Please, this approach compromises release governance: a merge affecting one component (such as a backend service or documentation change) can inadvertently trigger frontend production builds, potentially publishing unversioned or untested changes.

## Decision

We establish GitHub releases created by Release Please as the sole deployment boundary for Cloudflare-hosted frontends:

1. **Explicit Component Deployment Boundary**: A frontend (`apps/web/app`, `apps/web/admin`, `apps/web/marketing`) is built and deployed to Cloudflare Pages production only when Release Please publishes an official release tag (`app@<version>`, `admin@<version>`, or `landing@<version>`).
2. **Decouple Integration from Deployment**: Merging to `main` remains the continuous integration state, but does not deploy static frontends directly to production. Cloudflare automatic production deployments from `main` are disabled across all three Pages projects.
3. **Deterministic SHA-Pinned Builds**: Deployment workflows check out the exact commit SHA associated with the Release Please release tag, eliminating race conditions or drift relative to the latest `main` HEAD.
4. **Traceable Build Metadata**: The exact release commit SHA is exported to `GIT_SHA`, ensuring frontend version badges render the accurate version and short commit SHA corresponding to the release artifact.
5. **Least-Privilege Cloudflare Authentication**: Deployments authenticate through GitHub Actions secrets (`CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`) with access strictly scoped to editing the target Pages projects.
6. **Automated Deployment Verification**: Following upload via Wrangler, the deployment workflow verifies the live endpoint and confirms that the version badge matches the expected release version and short SHA.

## Consequences

### Positive

- **Strong Release Governance**: Deployments are intentional, auditable, and strictly tied to semantic version releases and changelogs.
- **Component Isolation**: Releasing one component does not rebuild, re-test, or re-deploy unaffected frontends.
- **Deterministic Traceability**: Operators can trace any running production UI directly back to its immutable release tag and Git SHA via the on-screen version badge.
- **Preview Stability**: Pull request preview deployments may remain active without risking unintended production promotion.

### Negative / Trade-offs

- **Manual Initial Configuration**: Operators must manually disable automatic production branch builds in the Cloudflare Pages dashboard for each project.
- **Multi-Step Release Cycle**: Changes require merging a release pull request before reaching production, rather than immediately deploying on merge to `main`.
