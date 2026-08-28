## Change Archived

**Change**: `seo-issues`
**Branch**: `seo-issues`
**Archived to**: `openspec/changes/archive/2026-08-28-seo-issues/`
**Date**: 2026-08-28
**Mode**: openspec (openspec-only)
**Verify verdict**: PASS WITH WARNINGS (2026-08-28, fallback manual evidence preserved)
**QA verdict**: PASS WITH WARNINGS (2026-08-28, fallback manual evidence preserved, 2 BLOCKED PLATFORM with docs/config-only rationale)
**PR**: #895 updated with commit 44689bae

### Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| marketing-a11y-seo | Updated | 5 added, 1 modified, 0 removed requirements. Added: SEO Invariants — 12 URLs, Link Hygiene — No Broken/Orphan, IndexNow Intentionally Absent, Performance Budget — Measure Only, Redirect/HTTPS — Platform Decision. Modified: Robots and Sitemap Routes (SEO) — expanded from minimal allow-all (2 scenarios) to per-bot Allow for 7 bots + Sitemap line + Does not block AI (4 scenarios). Preserved: Focus Management via tabindex="-1", Reduced-Motion Axe Context, TDD Requirement. |

### What Was Implemented

Ahrefs 2026-08-27 (Project 9293424) 15 issues dispositioned (7 FIX, 4 ACCEPT, 4 PLATFORM) on 12 marketing URLs (6 routes ×2 locales):

- **Pinned PR #869 fixes**: `cdn-cgi/l/email-protection` 404 via mailto conversion + orphan via Nav language switch — proven via `seo.spec.ts` crawl `<400` over 12 URLs on 3 consecutive chromium runs.
- **Robots per-bot Allow**: `src/pages/robots.txt.ts` now serves `User-agent: *` + 7 bots (`OAI-SearchBot`, `GPTBot`, `PerplexityBot`, `ClaudeBot`, `Google-Extended`, `GoogleOther`, `Bingbot`) each `Allow: /` + `Sitemap: https://profiletailors.com/sitemap.xml` (text/plain). Allow-all deliberate, no `Disallow`.
- **Link hygiene**: `src/i18n/utils.test.ts` extended (title ≥30 + suffix ` — Profile Tailors` + unique, desc 120-160 + unique, no `http://`/`cdn-cgi`/`api.indexnow.org` guards, 43 tests). `tests/e2e/seo.spec.ts` NEW 215 lines, 7 tests: head invariants, crawl `<400`, no http/cdn-cgi, sitemap parity 12 loc + inbound ≥2, per-bot Allow, index,follow, IndexNow absent.
- **SEO invariants**: `src/i18n/en.ts`/`es.ts` titles reordered to suffix ` — Profile Tailors` (≥30, unique 12), descriptions 120-160 unique 12, single H1 `h1===title` for 10 legal (home headline separate per design), canonical `https://profiletailors.com${path}` https+slash, hreflang `en`/`es`/`x-default`, `og:*` via `Layout.astro`.
- **Measure-only perf**: `docs/marketing/lighthouse/baseline.json` 12 URLs vs budget LCP 2500/CLS 0.1/INP 200 (synthetic placeholder — WARNING QA-F02, guard holds).
- **Runbook**: `docs/marketing/seo.md` NEW 92 lines — Cloudflare Bulk Redirects matrix (`http→https`, `www→apex`, `302→301`, chain collapse), HSTS `max-age=63072000; includeSubDomains; preload`, `curl -I` verification, Ahrefs re-crawl steps (also renamed per review).

Code fixes in PR #895 commit 44689bae: seo.spec robustness (waitForLoadState, sitemap loc counting, inbound dedup), utils.test types (strict typing for i18n guards), robots coverage (per-bot Allow assertions), seo.md rename (lighthouse/budget clarity), spec thresholds (title ≥30, desc 120-160 pinned).

### Verification Evidence (PASS WITH WARNINGS)

| Command | Result | Evidence |
|---------|--------|----------|
| `pnpm --filter marketing check` (astro check) | PASS | 64 files, 0 errors 0 warnings |
| `pnpm --filter marketing test -- --run` (vitest) | PASS | 11 files, 113 passed (incl. utils.test.ts 43) |
| `pnpm --filter marketing exec biome check .` | PASS | 63 files, 0 issues |
| `pnpm --filter marketing build` (astro build) | PASS | 12 pages, 14 routes, 377ms, dist/ verified |
| `dist/robots.txt` | PASS | 8× `User-agent:`, 8× `Allow: /`, 1× `Sitemap:` |
| `dist/sitemap.xml` | PASS | 12 `<loc>`, https+slash, weekly |
| `grep http:// / cdn-cgi / api.indexnow.org` | PASS | 0 hits outside expectations |
| `playwright seo.spec.ts --project=chromium` | PASS | 7 passed 8.0s (No broken 7.4s, No http 1.6s, Sitemap 1.8s, per-bot 22ms, index,follow 1.6s, Single H1 1.9s, No IndexNow 8ms) |
| `playwright accessibility + consent + seo` | PASS | 26 passed 11.6s (a11y 12 + consent + seo 7, no regression) |
| `docs/marketing/seo.md` | PASS | 92 lines Bulk Redirects + HSTS + curl |
| `docs/marketing/lighthouse/baseline.json` | PASS | 12 URLs vs budget (synthetic — see QA-F02) |

Verify warnings (2 non-blocking): synthetic Lighthouse placeholder (requires `npx lighthouse` refresh before next perf PR), partial E2E lane in verify (full lane re-run in QA 26/26, 8 waitlist failures are backend-missing out-of-scope).

### QA Acceptance (PASS WITH WARNINGS — 19 scenarios)

- **P0 hygiene (QA-S01–S03, FIX #1–3, #15)**: PASS — crawl `<400`, no cdn-cgi/http, sitemap 12 + inbound ≥2 proven chromium.
- **P1 AI bots (QA-S04–S05)**: PASS — per-bot Allow + Sitemap + no Disallow + index,follow on 12.
- **P2 invariants (QA-S06–S09)**: PASS — title ≥30/suffix/unique, desc 120-160/unique, h1==1 + canonical/hreflang/og, locale EN/ES parity.
- **Negative/ACCEPT (QA-S10)**: PASS — IndexNow absent.
- **P2 measure-only (QA-S11)**: PASS — Lighthouse budget recorded (QA-F02 warning).
- **PLATFORM (QA-S12–S13, PLATFORM #5/#6/#13/#14)**: Repo `no http://` guard PASS; edge 301/HSTS docs-only — BLOCKED QA-S12/QA-F01 is expected PLATFORM handoff, not code defect, allowed per docs/config-only exception with explicit rationale and visible warning. Original BLOCKED verdict preserved.
- **State/UX (QA-S14–S19)**: PASS — repeated crawl stable, chromium 26/26, a11y axe green, security allow-all deliberate; QA-S17 responsive NOT TESTED (not acceptance-relevant for head-only change); QA-S18 Ahrefs live re-crawl BLOCKED pending deploy.

No CRITICAL, P0, or P1 findings. P2 QA-F01 accepted PLATFORM, P3 QA-F02–F05 warnings/suggestions (synthetic baseline, partial lane, Layout fallback title order, sitemap grep quirk).

### Acceptance Gate Decision

Archive allowed per `openspec/config.yaml` qa.archive_blockers (blocks CRITICAL/P0/P1 and normally acceptance-relevant BLOCKED/NOT TESTED, with docs/config-only exception allowed with explicit rationale and visible warning).

- Verify: PASS WITH WARNINGS — 17/17 scenarios PASS, 2 warnings non-blocking.
- QA: PASS WITH WARNINGS — 15 PASS, 2 BLOCKED PLATFORM (QA-S12 edge redirects, QA-S18 Ahrefs re-crawl) both docs/config-only, dispositioned as PLATFORM per proposal, operator runbook exists (`docs/marketing/seo.md` curl steps). No unresolved CRITICAL/P0/P1.
- **WARNING preserved**: BLOCKED scope is operator-owned and credentials/permissions absent in worktree; must be verified post-deploy via `curl -I http(s)://…` and Ahrefs Dashboard Re-crawl before closing external audit loop. This archive does NOT claim live edge or Ahrefs green.

### Archive Contents

- proposal.md ✅ (15 dispositions FIX/ACCEPT/PLATFORM)
- specs/marketing-a11y-seo/spec.md ✅ (delta 5 ADDED + 1 MODIFIED merged)
- design.md ✅ (4 decisions, pin-and-prove)
- exploration.md ✅ (230 lines, 15 issues grounded, PR #869 traced)
- tasks.md ✅ (5 phases, 14 tasks, 14/14 100% [x], single-pr Low)
- verify-report.md ✅ (PASS WITH WARNINGS, 17/17, 2 warnings)
- qa-report.md ✅ (PASS WITH WARNINGS, 19 scenarios, 2 BLOCKED PLATFORM preserved)
- archive-report.md ✅ (this file)
- state.yaml ✅ (updated to archive)

### Source of Truth Updated

- `openspec/specs/marketing-a11y-seo/spec.md` — now reflects seo-issues (5 added + 1 modified). Preserved: tabindex, reduced-motion, TDD Requirement.

### Follow-up Operator Handoff (post-merge)

1. Configure Cloudflare Bulk Redirects + HSTS per `docs/marketing/seo.md`, then `curl -I http://profiletailors.com/`, `http://www.profiletailors.com/privacy/`, `https://www.profiletailors.com/` for 301 + `Strict-Transport-Security: max-age=63072000; includeSubDomains; preload`.
2. Deploy to `https://profiletailors.com` and trigger Ahrefs 9293424 Site Audit Re-crawl; confirm 0 broken/orphan/http, 12 sitemap, per-bot Allow.
3. Optionally refresh `docs/marketing/lighthouse/baseline.json` via `npx lighthouse --output=json` per `seo.md` Usage and align `Layout.astro:13` fallback title order.

### SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived. The docs/config-only PLATFORM items remain as operator runbook handoff, not code defects. Ready for the next change.
