# SEO Runbook — Profile Tailors Marketing

## Overview

Covers operator-owned PLATFORM fixes for Ahrefs 2026-08-27 (9293424) and AI-crawler policy. Code pins title/meta/H1 invariants and robots/sitemap; redirects/HSTS live outside the repo (Cloudflare edge).

## Changes

- Titles `>=30` + suffix ` — Profile Tailors` unique per 12 URLs (`en.ts`/`es.ts` → `Layout.astro`)
- Meta `120–160` unique per URL
- Single `h1` per URL; legal pages `h1===title`
- `robots.txt` explicit `Allow: /` for `*` + `OAI-SearchBot`, `GPTBot`, `PerplexityBot`, `ClaudeBot`, `Google-Extended`, `GoogleOther`, `Bingbot` + `Sitemap: https://profiletailors.com/sitemap.xml`
- `sitemap.xml` 12 URLs (`/` + `privacy`/`terms`/`cookies`/`acceptable-use`/`accessibility` ×2 locales) weekly changefreq
- No `http://` href/src and no `cdn-cgi` (mailto link guard)
- IndexNow intentionally absent — sitemap + Search Console is discovery
- Lighthouse baseline measure-only under `docs/marketing/lighthouse/baseline.json`

## 301 Matrix — Cloudflare Bulk Redirects (PLATFORM)

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

## AI Crawler Policy

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
3. Confirm `0` broken/orphan, `0` `http://` href, `12` sitemap, per-bot Allow present

## Troubleshooting

- `cdn-cgi` reappears: raw email leaked — ensure `mailto:` link format `[a@b.com](mailto:a@b.com)` in `en.ts`/`es.ts`
- Orphan still flagged: stale Ahrefs snapshot — re-crawl after 10 min; E2E proves parity
- 3XX still flagged: operator Bulk Redirect not deployed — check curl `location` headers

## References

- `openspec/changes/seo-issues/specs/marketing-a11y-seo/spec.md`
- `openspec/changes/seo-issues/design.md`
- `apps/web/marketing/src/pages/robots.txt.ts`
- `apps/web/marketing/src/pages/sitemap.xml.ts`
- `apps/web/marketing/src/i18n/utils.test.ts`
- `apps/web/marketing/tests/e2e/seo.spec.ts`
- `docs/marketing/lighthouse/baseline.json`
