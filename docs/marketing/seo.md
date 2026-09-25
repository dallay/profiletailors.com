# SEO Runbook — Profile Tailors Marketing

## Overview

Covers operator-owned PLATFORM fixes for Ahrefs 2026-08-27 (9293424) and AI-crawler policy. Code pins title/meta/H1 invariants and robots/sitemap; redirects, HSTS, and edge HTML transformations live outside the repo (Cloudflare edge).

## Changes

- Titles `>=30` + suffix `— Profile Tailors` unique per 12 URLs (`en.ts`/`es.ts` → `Layout.astro`)
- Meta `120–160` unique per URL
- Single `h1` per URL; legal pages `h1===title`
- `robots.txt` explicit `Allow: /` for `*` + `OAI-SearchBot`, `GPTBot`, `PerplexityBot`, `ClaudeBot`, `Google-Extended`, `GoogleOther`, `Bingbot` + `Sitemap: https://profiletailors.com/sitemap.xml` (no `Disallow:` workarounds)
- `sitemap.xml` 12 URLs (`/` + `privacy`/`terms`/`cookies`/`acceptable-use`/`accessibility` ×2 locales) weekly changefreq
- No `http://` href/src and clean `mailto:` links without `cdn-cgi` in origin build artifacts
- IndexNow intentionally absent — sitemap + Search Console is discovery
- Lighthouse baseline measure-only under `docs/marketing/lighthouse/baseline.json`

### Build-Time (Origin) vs. Edge Invariants

To maintain reliable SEO auditing while protecting user email addresses, SEO invariants are divided into two distinct scopes:

1. **Build-Time / Origin Invariants (Repository-Controlled)**
   - Generated static build artifacts (`dist/`) contain clean `mailto:` links (e.g. `[contact@profiletailors.com](mailto:contact@profiletailors.com)`).
   - `robots.txt` is an allow-all per-bot configuration with no `Disallow:` directives.
   - Verified via unit (`pnpm test`) and E2E Playwright tests against local builds.

2. **Edge Invariants (Cloudflare-Controlled)**
   - Cloudflare Email Address Obfuscation operates dynamically at the edge post-build. For standard client traffic, Cloudflare rewrites `mailto:` links to `/cdn-cgi/l/email-protection#...`.
   - Ahrefs Site Audit executes via the `AhrefsSiteAudit` bot crawler. When Cloudflare rewrites email links for `AhrefsSiteAudit`, the audit crawler discovers generated `/cdn-cgi/` internal links and reports them as broken/4XX errors.
   - Previous repository attempts (such as adding `Disallow: /cdn-cgi/` in `robots.txt` for `AhrefsBot`) were ineffective because `AhrefsSiteAudit` is a separate crawler and edge transformations execute after HTML generation.
   - **Remediation**: A Cloudflare Configuration Rule bypasses Email Obfuscation exclusively for verified `AhrefsSiteAudit` requests, returning original `mailto:` links to the auditor without modifying normal user traffic.

### Cloudflare Configuration Rule (PLATFORM)

Operator executes in Cloudflare Dashboard:

1. Dashboard → Rules → Configuration Rules → Create rule `Bypass Email Obfuscation for Ahrefs Site Audit`
2. Set Expression: `cf.client.bot and http.user_agent contains "AhrefsSiteAudit"`
3. Set Option: **Email Obfuscation** = **Off**
4. Save and Deploy rule.

### 301 Matrix — Cloudflare Bulk Redirects (PLATFORM)

Operator executes; repo cannot prove edge redirects.

| Source | Target | Code |
|--------|--------|------|
| `http://profiletailors.com/*` | `https://profiletailors.com/$1` | 301 |
| `http://www.profiletailors.com/*` | `https://profiletailors.com/$1` | 301 |
| `https://www.profiletailors.com/*` | `https://profiletailors.com/$1` | 301 |
| Any `302` found on site | `301` to same target | 301 |
| Redirect chains (`/*` → `/*` → `/*`) | Collapse to single 301 | 301 |

Cloudflare steps:

1. Dashboard → Rules → Bulk Redirects → Create list `profiletailors-redirects`
2. Add rules from matrix; enable `Preserve query string`, `Preserve path suffix`
3. Deploy Bulk Redirect rule (status `Enabled`, `302→301` fixed)
4. Purge cache → `Purge everything`

HSTS:

1. Dashboard → SSL/TLS → Edge Certificates → HTTP Strict Transport Security (HSTS)
2. Enable HSTS, max-age `63072000` (2 years), includeSubDomains `on`, preload `on`, noSniffHeader `on`
3. Submit to `hstspreload.org` after confirming `https://` canonical on all 12 URLs

Verification:

```bash
curl -I http://profiletailors.com/ | grep -i location
curl -I http://www.profiletailors.com/privacy/ | grep -i location
curl -I https://www.profiletailors.com/ | grep -i location
curl -I https://profiletailors.com/ | grep -i strict-transport
```

### Deployed Edge Verification (Cloudflare Runbook)

The Email Obfuscation bypass requires both `cf.client.bot` and the `AhrefsSiteAudit` user agent. Validate the bypass with an actual Ahrefs Site Audit crawl, or another request method that satisfies both conditions. A spoofed user-agent curl request cannot prove this behavior; use it only for behavior independent of `cf.client.bot`.

In the Ahrefs crawl results, confirm that the privacy page contains `mailto:` links and no `/cdn-cgi/` email-protection rewrites.

For the independent `robots.txt` check:

```bash
curl -s https://profiletailors.com/robots.txt | grep -i "Disallow"
# Expected output: (empty, exit code 1)
```

### AI Crawler Policy

Allow-all deliberate: `Layout.astro` sets `robots=index,follow` on all 12 URLs. `robots.txt` explicit per-bot `Allow: /` silences Ahrefs AI heuristic (12 inconsistent + 12 blocked). No `Disallow`, no `llms.txt`. If policy changes, update `src/pages/robots.txt.ts` and this doc together.

## Usage

Local verification:

```bash
just frontend-check
just frontend-test
just frontend-test-e2e
just frontend-build
grep -R "http://" apps/web/marketing/src --include="*.ts" --include="*.astro" | grep -v "https://"
grep -R "api.indexnow.org" . --include="*.ts" --include="*.astro" --include="*.html"
cat docs/marketing/lighthouse/baseline.json | jq .urls
curl -s http://localhost:4321/robots.txt | grep Allow
curl -s http://localhost:4321/sitemap.xml | grep -c "<loc>"
```

Re-crawl:

1. Push to `main` → Vercel/Cloudflare Pages deploy
2. Ahrefs Dashboard → Project 9293424 → Site Audit → Re-crawl
3. Confirm `0` broken/orphan, `0` `http://` href, `12` sitemap, per-bot Allow present, and `0` `/cdn-cgi/` broken link errors.

## Troubleshooting

- `cdn-cgi` reappears for AhrefsSiteAudit: Cloudflare Configuration Rule inactive or user agent match failed — verify rule `cf.client.bot and http.user_agent contains "AhrefsSiteAudit"` with Email Obfuscation = Off in Cloudflare Dashboard.
- `cdn-cgi` in origin build artifacts: raw email leaked — ensure `mailto:` link format `[a@b.com](mailto:a@b.com)` in `en.ts`/`es.ts`.
- Orphan still flagged: stale Ahrefs snapshot — re-crawl after 10 min; E2E proves parity.
- 3XX still flagged: operator Bulk Redirect not deployed — check curl `location` headers.

## References

- `openspec/specs/marketing-a11y-seo/spec.md`
- `docs/infrastructure/cloudflare-deployment.md`
- `apps/web/marketing/src/pages/robots.txt.ts`
- `apps/web/marketing/src/pages/sitemap.xml.ts`
- `apps/web/marketing/src/i18n/utils.test.ts`
- `apps/web/marketing/tests/e2e/seo.spec.ts`
- `docs/marketing/lighthouse/baseline.json`
