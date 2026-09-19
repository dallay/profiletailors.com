# Apply Progress Report — dallay-597-deploy-cloudflare-frontends

**Status**: ✅ Implementation complete  
**Date**: 2026-09-18  
**Phase**: apply

## Completed Tasks

### Infrastructure and Configuration
- ✅ Updated `apps/web/app/wrangler.toml` to use `app-profile-tailors` project name
- ✅ Updated `apps/web/marketing/wrangler.toml` to use `profiletailors` project name
- ✅ Created `apps/web/admin/wrangler.toml` with `profiletailors-admin` project name and production API URL
- ✅ Extended `.github/workflows/release-please.yml` outputs block with 8 new release metadata keys (tag_name, sha for app/admin/landing)

### Deployment Automation
- ✅ Created TDD `scripts/extract-release-info.mjs` script with full test coverage
- ✅ Implemented `deploy-app` job: SHA-pinned checkout, build, Cloudflare Pages deploy, production verification
- ✅ Implemented `deploy-admin` job: SHA-pinned checkout, build, Cloudflare Pages deploy, production verification
- ✅ Implemented `deploy-landing` job: SHA-pinned checkout, build, Cloudflare Pages deploy, production verification
- ✅ All deployment jobs include 5-retry production verification against version badge

### Documentation
- ✅ Created `docs/infrastructure/cloudflare-deployment.md` runbook with operator actions, credentials, rollback procedure
- ✅ Updated `docs/production-secrets.md` with `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` entries
- ✅ Created `docs/architecture/adr/0022-release-driven-frontend-deployment.md` with decision context and consequences
- ✅ Updated `docs/architecture/adr/README.md` index with ADR 0022

## Validation Results

### Test Results
- ✅ `node --test scripts/release-image-smoke.test.mjs` — 3/3 passed
- ✅ `node --test scripts/extract-release-info.test.mjs` — 7/7 passed
- ✅ Manual execution of `extract-release-info.mjs` produces correct version, gitSha, shortSha, and GitHub output format

### Isolation Verification
- ✅ No server/smp files touched
- ✅ No Gradle, Docker, or Compose files touched
- ✅ Backend image build and push job (`build-and-push-smp`) remains unchanged

### Changed Files
```text
.github/workflows/release-please.yml | 201 ++++++++++++++++++++++++++++++++++-
apps/web/app/wrangler.toml           |   2 +-
apps/web/marketing/wrangler.toml     |   2 +-
apps/web/admin/wrangler.toml         |   NEW
docs/architecture/adr/README.md      |   1 +
docs/architecture/adr/0022-*.md      |   NEW
docs/infrastructure/cloudflare-*.md  |   NEW
docs/production-secrets.md           |  20 ++++
scripts/extract-release-info.mjs     |   NEW
scripts/extract-release-info.test.mjs|   NEW
```

## Implementation Highlights

1. **SHA-Pinned Deterministic Builds**: Every deployment job checks out the exact release tag SHA, eliminating race conditions with `main`.

2. **Metadata Resolution**: `extract-release-info.mjs` script extracts version and SHA from Release Please outputs, exports them as GitHub Actions outputs, and sets `GIT_SHA` for frontend builds.

3. **Production Verification**: All three deploy jobs run a 5-retry verification probe that confirms the version badge on the live endpoint matches the expected `v{version} · {shortSha}`.

4. **Least-Privilege Auth**: Uses dedicated `CLOUDFLARE_API_TOKEN` scoped only to Pages edit permissions, not account admin.

5. **Operator Manual Actions Required**: Cloudflare dashboard automatic production deployments from `main` branch must be disabled for `app-profile-tailors`, `profiletailors`, and `profiletailors-admin` projects.

## Outstanding Work (Not in Scope)

- GitHub secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` must be configured by repository administrator
- Repository variables `PT_PRODUCTION_APP_URL`, `PT_PRODUCTION_ADMIN_URL`, `PT_PRODUCTION_MARKETING_URL` may optionally override default Pages domains
- Cloudflare Pages projects must exist with correct names before first deployment
- Manual disabling of automatic production builds in Cloudflare dashboard per project

## Next Steps

1. Repository administrator configures GitHub secrets
2. Repository administrator disables automatic production builds in Cloudflare Pages dashboard for all three projects
3. Merge implementation PR to `main`
4. Trigger Release Please component releases for app, admin, or landing
5. Observe first automated deployment and verify production endpoints
6. Archive this change via `sdd-archive`

## Recommendation

Implementation is complete and ready for review. Acceptance criteria AC1–AC10 remain deferred until first-merge verification (live Cloudflare Pages deployment); AC11 and AC12 pass based on repository artifacts. Backend isolation is preserved, and durable operational guidance is in place.
