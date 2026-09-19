# Cloudflare Pages Release-Driven Deployment

**Last Updated:** 2026-09-19

## Overview

Profile Tailors deploys its three static web frontends to Cloudflare Pages exclusively from Release Please component releases and tags. Merging to `main` acts as integration only and does not deploy frontends directly to production.

Target projects:

| Workspace | Component | Cloudflare Pages Project | Default Pages Domain |
|---|---|---|---|
| `apps/web/app` | `app` | `app-profile-tailors` | `https://app-profiletailors.pages.dev` |
| `apps/web/admin` | `admin` | `profiletailors-admin` | `https://profiletailors-com-bx5.pages.dev` |
| `apps/web/marketing` | `landing` | `profiletailors` | `https://profiletailors-com.pages.dev` |

## Deployment Model

```text
PR merged to main
       ↓
CI checks & quality gate
       ↓
Release Please creates component release
       ↓
GitHub Actions release-please workflow runs
       ↓
Checkout exact release tag
       ↓
Resolve commit SHA and version
       ↓
Build frontend artifact with GIT_SHA
       ↓
Deploy artifact via wrangler to Cloudflare Pages
       ↓
Post-deploy verification probe checks version badge
```

## Cloudflare Dashboard Operator Actions

Automatic production deployments triggered by Cloudflare's GitHub integration must be disabled for each project to ensure GitHub Actions is the sole deployment orchestrator.

### Disable Production Auto-deploy

For each of `app-profile-tailors`, `profiletailors`, and `profiletailors-admin`:

1. Navigate to **Cloudflare Dashboard > Workers & Pages**.
2. Select the Pages project.
3. Open **Settings > Builds & deployments**.
4. Under **Configure Production deployments**, disable automatic builds for the `main` branch (or set deployment branch to a non-existent branch / disable automated Git deployments).
5. Ensure Preview deployments remain available for pull requests if needed, without promoting to production.

## Credentials and Secrets

Deployment requires least-privilege credentials configured in GitHub Actions secrets and repository variables.

### GitHub Secrets

- `CLOUDFLARE_API_TOKEN`: Cloudflare API token scoped specifically to Pages deployments for the three frontend projects.
- `CLOUDFLARE_ACCOUNT_ID`: Cloudflare Account identifier where the Pages projects reside.

### GitHub Variables (Optional overrides)

- `PT_PRODUCTION_APP_URL`: Target production URL for app verification. Defaults to `https://app-profiletailors.pages.dev`.
- `PT_PRODUCTION_ADMIN_URL`: Target production URL for admin verification. Defaults to `https://profiletailors-com-bx5.pages.dev`.
- `PT_PRODUCTION_MARKETING_URL`: Target production URL for marketing verification. Defaults to `https://profiletailors-com.pages.dev`.

### Token Permission Scope

Create the API token via **My Profile > API Tokens > Create Token > Custom Token**:

- **Account Permissions**:
  - `Cloudflare Pages`: `Edit`
- **Account Resources**:
  - Include specific account holding Profile Tailors projects.

Do not grant account-wide administrator privileges or zone DNS permissions.

## Production Verification

After deployment, the workflow queries the live endpoint with retry logic (up to 5 attempts, 15 seconds apart) to ensure CDN cache propagation has occurred and the expected version badge is rendered:

```bash
expected="v${EXPECTED_VERSION} · ${EXPECTED_SHORT_SHA}"
curl -fsSL "${TARGET_URL}" | grep -F "${expected}"
```

For marketing, verification probes `/legal/terms` where the version badge is permanently present in the footer layout.

## Rollback Procedure

To roll back a faulty production deployment:

1. In the Cloudflare Pages dashboard for the affected project, navigate to **Deployments**.
2. Locate the previous known-good deployment associated with the preceding release tag.
3. Select **Manage deployment > Rollback to this deployment**.
4. If necessary, re-run or revert the corresponding component commit via Git and trigger a patch release through Release Please.

## Changes

## Usage

## Troubleshooting

### Cloudflare API token secret not found

Ensure `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` are configured as **Environment secrets** scoped to the `PROD` GitHub environment. The deploy jobs declare `environment: PROD`, so repository secrets alone are insufficient.

### Deployment succeeds but verification probe fails

CDN propagation on Cloudflare Pages can take up to 75 seconds. The workflow retries 5 times with 15-second intervals. If all attempts fail, verify the deployment appears in the Cloudflare Pages dashboard and that the version badge renders the correct `v{version} · {sha}` format.

### Automatic production deploy still triggering after disabling

Confirm the change was saved in Cloudflare Pages dashboard. The setting lives under **Settings > Builds & deployments > Configure Production deployments**. Check that the correct branch (`main`) is targeted and that the toggle is fully disabled, not just set to a different branch.

## References

- `.github/workflows/release-please.yml`
- `scripts/extract-release-info.mjs`
- `docs/production-secrets.md`
- `docs/architecture/adr/0022-release-driven-frontend-deployment.md`
