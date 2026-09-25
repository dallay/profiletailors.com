# Cloudflare Pages Release-Driven Deployment

**Last Updated:** 2026-09-24

## Overview

Profile Tailors deploys its three static web frontends to Cloudflare Pages exclusively from Release Please component releases and tags. Merging to `main` acts as integration only and does not deploy frontends directly to production.

Target projects:

| Workspace | Component | Cloudflare Pages Project | Default Pages Domain |
|---|---|---|---|
| `apps/web/app` | `app` | `app-profile-tailors` | `https://app-profiletailors.pages.dev` |
| `apps/web/admin` | `admin` | `profiletailors-admin` | `https://profiletailors-com-bx5.pages.dev` |
| `apps/web/marketing` | `landing` | `profiletailors` | `https://profiletailors-com.pages.dev` |

## Changes

### Deployment Model

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

## Usage

### Cloudflare Dashboard Operator Actions

Automatic production deployments triggered by Cloudflare's GitHub integration must be disabled for each project to ensure GitHub Actions is the sole deployment orchestrator.

#### Disable Production Auto-deploy

For each of `app-profile-tailors`, `profiletailors`, and `profiletailors-admin`:

1. Navigate to **Cloudflare Dashboard > Workers & Pages**.
2. Select the Pages project.
3. Open **Settings > Builds & deployments**.
4. Under **Configure Production deployments**, disable automatic builds for the `main` branch (or set deployment branch to a non-existent branch / disable automated Git deployments).
5. Ensure Preview deployments remain available for pull requests if needed, without promoting to production.

#### Ahrefs Site Audit Email Obfuscation Configuration Rule

To prevent Cloudflare Email Address Obfuscation from rewriting `mailto:` links into `/cdn-cgi/l/email-protection` URLs during Ahrefs Site Audit crawls:

1. Navigate to **Cloudflare Dashboard > Rules > Configuration Rules**.
2. Select **Create rule**.
3. Set Name: `Bypass Email Obfuscation for Ahrefs Site Audit`.
4. Set Match Expression: `http.user_agent contains "AhrefsSiteAudit/" and http.user_agent contains "/robot/site-audit"`.
5. Under Settings, select **Email Obfuscation** and set it to **Off**.
6. Save and deploy the rule.

This rule matches the verified desktop and mobile AhrefsSiteAudit user-agent strings across minor versions per [Ahrefs bots](https://ahrefs.com/robot). It ensures `AhrefsSiteAudit` receives clean `mailto:` links without triggering false-positive 4XX broken link errors, while keeping global Email Obfuscation active for regular site visitors.

### Credentials and Secrets

Deployment requires least-privilege credentials configured in GitHub Actions secrets and repository variables.

#### GitHub Secrets

- `CLOUDFLARE_API_TOKEN`: Cloudflare API token scoped specifically to Pages deployments for the three frontend projects.
- `CLOUDFLARE_ACCOUNT_ID`: Cloudflare Account identifier where the Pages projects reside.

#### GitHub Variables (Optional overrides)

- `PT_PRODUCTION_APP_URL`: Target production URL for app verification. Defaults to `https://app.profiletailors.com`.
- `PT_PRODUCTION_ADMIN_URL`: Target production URL for admin verification. Defaults to `https://admin.profiletailors.com`.
- `PT_PRODUCTION_MARKETING_URL`: Target production URL for marketing verification. Defaults to `https://profiletailors.com`.
- `PT_PRODUCTION_API_URL`: Backend origin consumed at build time by `app`, `admin`, and `marketing` (Astro `WAITLIST_API_BASE`). Defaults to `https://api.profiletailors.com`.
- `PT_PRODUCTION_WAITLIST_ENABLED`: Toggle forwarded to the Astro build as `WAITLIST_ENABLED` so `astro:env/client` ships a boolean instead of an empty default. Defaults to `true`.
- `PT_PRODUCTION_AHREFS_ANALYTICS_KEY`: Site identifier forwarded to the Astro build as `AHREFS_ANALYTICS_KEY`. Empty by default. Exposed client-side by design (`access: 'public'` in the schema).

#### Token Permission Scope

Create the API token via **My Profile > API Tokens > Create Token > Custom Token**:

- **Account Permissions**:
  - `Cloudflare Pages`: `Edit`
- **Account Resources**:
  - Include specific account holding Profile Tailors projects.

Do not grant account-wide administrator privileges or zone DNS permissions.

### Production Verification

After deployment, the workflow queries the live endpoint with retry logic (up to 5 attempts, 15 seconds apart) to ensure CDN cache propagation has occurred and the expected build metadata is published.

For the Vue SPAs, the probe downloads the JavaScript assets referenced by the root HTML and checks for the expected version, SHA, and ` · ` separator. The badge is client-rendered, so it is not expected in the initial HTML response.

For marketing, verification probes `/terms/` where the version badge is statically rendered in the footer layout.

### Rollback Procedure

To roll back a faulty production deployment:

1. In the Cloudflare Pages dashboard for the affected project, navigate to **Deployments**.
2. Locate the previous known-good deployment associated with the preceding release tag.
3. Select **Manage deployment > Rollback to this deployment**.
4. If necessary, re-run or revert the corresponding component commit via Git and trigger a patch release through Release Please.

## Troubleshooting

### Cloudflare API token secret not found

Ensure `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` are configured as **Environment secrets** scoped to the `PROD` GitHub environment. The deploy jobs declare `environment: PROD`, so repository secrets alone are insufficient.

### Deployment succeeds but verification probe fails

CDN propagation on Cloudflare Pages can take up to 75 seconds. The workflow retries 5 times with 15-second intervals. If all attempts fail, verify the deployment appears in the Cloudflare Pages dashboard and that the version badge renders the correct `v{version} · {sha}` format.

### Automatic production deploy still triggering after disabling

Confirm the change was saved in Cloudflare Pages dashboard. The setting lives under **Settings > Builds & deployments > Configure Production deployments**. Check that the correct branch (`main`) is targeted and that the toggle is fully disabled, not just set to a different branch.

### Dashboard variables exist but the deployed bundle ignores them

Since Release Please owns the build, the build-time env vars consumed by Vite and Astro are injected by the GitHub Actions job, not by the Cloudflare Pages dashboard. The dashboard's **Variables and secrets** view still receives `wrangler pages deploy` input, but for static frontends it only affects Pages Functions at runtime; the static bundle is already baked at that point.

If the dashboard contains entries like `PUBLIC_WAITLIST_API_BASE` or `PUBLIC_WAITLIST_ENABLED` for the `profiletailors` project, treat them as legacy artifacts and remove them. The Astro schema in `apps/web/marketing/astro.config.mjs` reads `WAITLIST_API_BASE`, `WAITLIST_ENABLED`, and `AHREFS_ANALYTICS_KEY` without the `PUBLIC_` prefix, so dashboard entries prefixed with `PUBLIC_` are silently ignored by the static build. Renaming them inside the dashboard would not change that, because GitHub Actions drives the build, not the dashboard. Configure build-time values through the GitHub Variables listed above instead.

## References

- `.github/workflows/release-please.yml`
- `scripts/extract-release-info.mjs`
- `docs/production-secrets.md`
- `docs/architecture/adr/0022-release-driven-frontend-deployment.md`
