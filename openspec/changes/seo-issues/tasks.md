# Tasks: seo-issues — Ahrefs 2026-08-27 (9293424)

## Review Workload Forecast

| Field | Value |
|-------|-------|
| Estimated changed lines | 220–300 |
| 400-line budget risk | Low |
| Chained PRs recommended | No |
| Suggested split | Single PR |
| Delivery strategy | single-pr |
| Chain strategy | single-pr |

Decision needed before apply: No
Chained PRs recommended: No
Chain strategy: single-pr
400-line budget risk: Low

### Suggested Work Units

| Unit | Goal | PR | Notes |
|------|------|----|-------|
| 1 | Pin + prove SEO invariants | PR 1 | Base `main`; robots, seo.spec.ts, utils.test.ts, SEO.md, Lighthouse |

## Phase 1: P0 Link Hygiene — Broken/4XX/Orphan (FIX #1-3, #15)

- [x] 1.1 RED: `tests/e2e/seo.spec.ts` — crawl 12 URLs, collect internal `a[href]` excl. `mailto/#`/ext, `page.request.get` → `<400` + no `cdn-cgi` — traces `No broken or obfuscated href`
- [x] 1.2 GREEN: Run on `astro preview`; confirm PR #869 mailto holds — no code if green
- [x] 1.3 RED: Scan `href/src` → zero `http://` — traces `No http href` + `Repo rejects http`
- [x] 1.4 RED: Sitemap parity — `GET /sitemap.xml` → 12 `<loc>` + inbound `>=2` — traces `Sitemap parity`
- [x] 1.5 GREEN: Verify `src/pages/sitemap.xml.ts` already 12 — lock via test

## Phase 2: AI Bot Policy — robots per-bot Allow (FIX #7-8)

- [x] 2.1 RED: `GET /robots.txt` → `Allow: /` for `*`+7 bots + `Sitemap:` — traces `per-bot Allow` + `Does not block AI`
- [x] 2.2 GREEN: Edit `src/pages/robots.txt.ts` — add 7 `Allow: /` blocks + `Sitemap:`; no `Disallow`
- [x] 2.3 Check `Layout.astro` meta `robots=index,follow` on 12 URLs — no edit

## Phase 3: SEO Invariants — Title/Meta/H1/Canonical (ACCEPT #10-12)

- [x] 3.1 RED: Extend `src/i18n/utils.test.ts` — `title>=30` + suffix ` — Profile Tailors` + unique; `desc` 120–160 unique — traces `Titles/meta`
- [x] 3.2 GREEN: Verify `en.ts/es.ts` compliant — fix only if RED fails
- [x] 3.3 RED: `seo.spec.ts` 12 URLs: title/desc bands, `h1==1` + `h1===title`, canonical https+slash, hreflang `en/es/x-default`, robots `index,follow`, `og:*` — traces `Single H1` + `Canonical`
- [x] 3.4 GREEN: Confirm `Layout.astro` canonical/hreflang correct — proof only

## Phase 4: Perf Budget + IndexNow Guard (FIX #4, ACCEPT #9)

- [x] 4.1 RED: `grep api.indexnow.org` + `dist/*.txt` → 0 — traces `No IndexNow artifacts`
- [x] 4.2 GREEN: Confirm absent — ACCEPT locked
- [x] 4.3 RED: Lighthouse scaffold — preview 12 URLs → JSON; guard blocks perf without artifact — traces `Lighthouse recorded` + `Reject unmeasured fix`
- [x] 4.4 GREEN: Run lighthouse, commit JSON under `docs/marketing/lighthouse/` — measure-only

## Phase 5: Docs / Runbook + Verification (PLATFORM #5-6, #13-14)

- [x] 5.1 Create `docs/marketing/SEO.md` — 301 matrix (`http→https`, `www→apex`, `302→301`), HSTS, Cloudflare steps, re-crawl — traces `Runbook`
- [x] 5.2 Verify: `just frontend-check && just frontend-test && just frontend-test-e2e && just frontend-build`; attach crawl + Lighthouse + robots/sitemap

Order: 1→2→3→4→5; TDD RED before GREEN; no `shared/web`/`server/smp`. Effort: ~8–11h (Ph1 2h, Ph2 1h, Ph3 2h, Ph4 3h, Ph5 1h).
