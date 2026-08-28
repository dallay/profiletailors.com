# Proposal: seo-issues — Ahrefs 2026-08-27 (9293424)

## Intent

Ahrefs reports 15 issues on 12 marketing URLs (6 routes ×2 locales). PR #869 fixed the 10-page batch (Cloudflare `cdn-cgi/l/email-protection` 404 via mailto, orphan via Nav) but snapshot re-flagged it. Pin invariants, prove with E2E, disposition gaps.

## Scope

### In Scope

- Pin title (>=30/branded suffix), description 120-160, single H1 (EN/ES) + regressions
- Link hygiene E2E: no 404/4XX, no `http://` href lint, sitemap-vs-graph parity (12 URLs)
- Robots: explicit per-bot `Allow: /` + sitemap line, docs for allow-all decision
- SEO E2E `seo.spec.ts` for title/meta/H1/canonical/hreflang/og/robots/sitemap
- Perf budget spec (measure only)
- Docs: runbook for 301/HSTS/re-crawl

### Out of Scope

- IndexNow (intentionally absent; sitemap + Search Console sufficient)
- Bulk-redirect/HSTS execution (operator)
- `shared/web`, `server/smp`, `apps/web/app`, `admin`
- Perf code w/o baseline

## Capabilities

### New Capabilities

- None

### Modified Capabilities

- `marketing-a11y-seo`: SEO invariants (title/meta/H1, canonical/hreflang, robots/sitemap, link hygiene, perf budget)

## Issue Disposition (15)

| # | Issue | Bucket | Disposition |
|---|-------|--------|-------------|
|1|10 broken links|FIX|Fixed 7eaf7639; pin graph-crawl|
|2|1×404|FIX|Same `cdn-cgi` dest; pin|
|3|1×4XX|FIX|Same; pin|
|4|6 slow (+2)|INVESTIGATE|Measure-only Lighthouse budget; no perf fix in this change|
|5|3×3XX|PLATFORM|Operator bulk 301|
|6|1×302|PLATFORM|302→301|
|7|12 inconsistent AI|FIX|Per-bot `Allow: /` docs|
|8|12 blocked AI|FIX|Same|
|9|10 IndexNow|ACCEPT|Not implemented|
|10|10 H1 changed|ACCEPT|Accepted; pin H1 invariant|
|11|10 meta changed|ACCEPT|Accepted; pin 120-160|
|12|10 title changed|ACCEPT|Accepted; pin suffix|
|13|2 HTTP→HTTPS|PLATFORM|No `http://` href; bulk 301|
|14|1 chain|PLATFORM|Same|
|15|1 orphan|FIX|Fixed; pin parity|

## Approach

Minimal + AI docs. Keep allow-all, add `Allow: /` for OAI-SearchBot, GPTBot, PerplexityBot, ClaudeBot, Google-Extended/-Other, Bingbot. Pin spec + `seo.spec.ts`.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `src/pages/robots.txt.ts` | Modified | Per-bot Allow + sitemap |
| `src/i18n/utils.test.ts` | Modified | Extend pins |
| `tests/e2e/seo.spec.ts` | New | 12-URL crawl checks |
| `marketing-a11y-seo` | Modified | Invariants |
| `docs/marketing/seo.md` | New | 301/HSTS runbook |
| `astro.config.mjs`/`Layout.astro`/`sitemap.xml.ts` | Verify | No code change |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Stale Ahrefs re-report | High | E2E proof + manual re-crawl |
| Platform 301 unenforceable | Med | Lint only `http://`; docs decision |
| i18n re-drift | Med | CI gate spec + E2E |
| Perf without data | Med | Require Lighthouse artifact |

## Rollback Plan

`git revert` on `seo-issues`. Static assets only, no migration.

## Dependencies

- Ahrefs 9293424; skills `seo`, `core-web-vitals`
- `just frontend-check/test/e2e/build`

## Success Criteria

- [ ] 15 issues mapped FIX/ACCEPT/PLATFORM
- [ ] No 404/4XX; no `http://`; sitemap=12 with canonical/hreflang
- [ ] Titles/meta/H1 pinned and E2E green
- [ ] `robots.txt` per-bot Allow + sitemap
- [ ] Lighthouse budget + baseline
- [ ] Runbook exists
