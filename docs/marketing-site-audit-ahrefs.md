# Marketing Site Audit — Ahrefs Findings and Remediation

## Overview

Audit of `profiletailors.com` (Astro marketing site) against Ahrefs Site Audit project `9293424`
(crawl date `2026-08-26T16:22:12Z`). Documents issues, fixes shipped in the repository, and the
Cloudflare dashboard steps the operator must perform manually because they live outside the repo.

## Source of Truth

| Claim | Canonical owner |
|---|---|
| Marketing copy and meta descriptions | `apps/web/marketing/src/i18n/en.ts` and `es.ts` |
| Legal page structure | `apps/web/marketing/src/pages/_*.astro` |
| Cloudflare zone, edge rules, HSTS | Cloudflare dashboard for `profiletailors.com` |

## Issues Found by Ahrefs

| Issue | Severity | Affected | Resolution Status |
|---|---|---|---|
| Page has links to broken page | Error | 10 | Fixed in repo |
| 404 page (`/cdn-cgi/l/email-protection`) | Error | 1 | Fixed in repo (root cause) |
| 4XX page (Cloudflare email-decode sub-resource) | Error | 1 | Fixed in repo (root cause) |
| Meta description too short | Warning | 10 | Fixed in repo |
| Title too short (`/privacy/`) | Warning | 1 | Fixed in repo |
| Slow page | Warning | 4 (+2 new) | Documented, see below |
| 3XX redirect (trailing slash 308) | Warning | 3 | Informational; Astro auto-handles |
| 302 redirect (`www` → Cloudflare link shortener) | Warning | 1 | Pending Cloudflare dashboard fix |
| Page has only one dofollow incoming internal link | Notice | 2 | Fixed in repo |
| HTTP to HTTPS redirect | Notice | 2 | Pending Cloudflare dashboard fix |
| Redirect chain (`www` → link shortener) | Notice | 1 | Pending Cloudflare dashboard fix |

## Fixes Applied in the Repository

### Email obfuscation root cause (resolves three errors at once)

Cloudflare's automatic "Email Address Obfuscation" feature (default ON) wraps every raw email in
rendered HTML with `<a href="/cdn-cgi/l/email-protection#..." class="__cf_email__">`. That endpoint
returns 404, so every legal page was effectively linking to a broken URL.

**Fix:** convert raw email text in `apps/web/marketing/src/i18n/en.ts` and `es.ts` to markdown
`[contact@profiletailors.com](mailto:contact@profiletailors.com)` syntax. Cloudflare does not
obfuscate emails already wrapped in `<a href="mailto:...">`, so the broken `/cdn-cgi/l/email-protection`
links disappear. The rendered HTML now contains a clickable `mailto:` link instead.

Affected strings (each locale had a copy):

- `legal.privacy.contact`
- `legal.terms.contact`
- `legal.cookies.contact`
- `legal.aup.contact`
- `legal.aup.reporting`
- `legal.accessibility.contact`

Two addresses were involved: `contact@profiletailors.com` (privacy/terms/cookies/aup) and
`accessibility@profiletailors.com` (accessibility statement).

### Meta description length (resolves ten warnings)

Every legal page description was between 69 and 97 characters. SEO best practice for meta
description length is 120 to 160 characters; Ahrefs uses the same threshold for its "too short"
warning. All ten legal page descriptions (`privacy`, `terms`, `cookies`, `aup`, `accessibility`
across `en` and `es`) were expanded to the 120 to 160 range.

### Privacy page title (resolves one warning)

The `privacy.title` was `Privacy Policy` (14 characters), triggering Ahrefs' "title too short"
warning. Updated to `Privacy Policy — Profile Tailors` (32 characters) and the ES equivalent to
`Política de Privacidad — Profile Tailors`.

### Incoming dofollow links on legal pages (resolves two notices)

Legal page components (`_PrivacyPolicy.astro`, `_TermsPage.astro`, `_CookiePolicyPage.astro`,
`_AcceptableUsePage.astro`, `_AccessibilityPage.astro`) rendered only the `<Footer>` legal nav, no
top-level navigation. The EN homepage `<Nav>` only linked to `/es/`, leaving `/es/` with one
incoming dofollow link and ES legal pages without a dofollow link from their EN counterpart.

**Fix:** import the existing `<Nav>` component into all five legal page components, with the
language switcher pointed at the same-path ES counterpart. This adds a logo link back to the apex
home plus a language switcher that links to the ES counterpart. After the fix, every legal page
links to every other legal page plus its language counterpart and the apex home.

## Pending Cloudflare Dashboard Steps

These changes live outside the repository and require operator action in the Cloudflare
dashboard.

### 1. Configure the `www` subdomain

Current behavior (verified via `curl -I` on 2026-08-27):

```text
http://www.profiletailors.com/    → 301 → https://www.profiletailors.com/
https://www.profiletailors.com/   → 302 → https://profiletailors-com.l.ink/
```

The `www` zone does not resolve to the marketing site; Cloudflare falls back to its link
shortener. This causes the "302 redirect" warning, the "Redirect chain" notice, and the duplicate
"HTTP to HTTPS redirect" notice for the same path.

**Resolution:** in the Cloudflare DNS for `profiletailors.com`, add a CNAME for `www` pointing to
the apex (or to the same Cloudflare Pages target). Then add a Bulk Redirect Rule of type
`Dynamic` matching `https://www.profiletailors.com/$1` and redirecting to
`https://profiletailors.com/$1` with status 301. After this, the only redirect from `www` should
be the single 301 to the apex.

### 2. Enable HSTS Preload

Cloudflare issues HTTP→HTTPS redirects on the first request from a client that has never visited
the site over HTTPS. Ahrefs flags every such redirect as a "HTTP to HTTPS redirect" notice.

**Resolution:** in the Cloudflare dashboard for `profiletailors.com`:

- SSL/TLS → Edge Certificates → enable `Strict-Transport-Security` with
  `max-age=63072000; includeSubDomains; preload`
- Submit the domain to <https://hstspreload.org> so the next Chrome/Firefox/Safari release ships
  the rule baked into the browser, eliminating the redirect entirely.

## Performance Observations

Lab LCP measured via Chrome DevTools on the production site (no throttling):

| Page | LCP | Render Delay | Notes |
|---|---|---|---|
| `/` | 1093 ms | 1002 ms | Hero animations (`apps/web/marketing/src/scripts/hero-animations.ts`) start hero elements at `opacity:0` and reveal them with `await animation.finished` chains. Inside the "good" Web Vitals budget but flagged by Ahrefs on field data. |
| `/privacy/` | 171 ms | 73 ms | Well within budget. |

The home page render delay is by design — the hero animations are progressive enhancement, with
a `<noscript>` fallback that reveals content if JavaScript fails to load. Changing the inline
`opacity:0` would alter the visual reveal sequence. The recommended long-term improvement is to
adopt CSS-driven entrance animations (e.g., `view-transition-name` or `@starting-style`) that do
not require JavaScript to set the initial state, eliminating the render delay entirely. Defer
that work to a dedicated UX/performance change rather than this SEO audit.

## Regression Tests Added

`apps/web/marketing/src/i18n/utils.test.ts` — new `describe('legal SEO (Ahrefs site audit)')`
block. Locks in:

- Every legal page description in both locales is between 120 and 160 characters.
- `/privacy/` titles in both locales are at least 20 characters.
- Every `@profiletailors.com` address in legal copy is wrapped in a markdown mailto link (no raw
  emails that Cloudflare's obfuscator could rewrite to broken `/cdn-cgi/l/email-protection`
  hrefs).

Run with `just frontend-test`.

## Verification Commands

```sh
just frontend-check   # Astro type check
just frontend-lint    # Biome check
just frontend-test    # Vitest suite (99/99 passing)
just frontend-build   # Static build to apps/web/marketing/dist
```

Manual render verification after deploy:

```sh
grep -r "cdn-cgi/l/email-protection" apps/web/marketing/dist/   # must return no matches
```

## References

- Ahrefs Site Audit project `9293424` — crawl history and full issue list at
  <https://app.ahrefs.com/site-audit/9293424/issues>
- Cloudflare Email Address Obfuscation — see Cloudflare dashboard under
  **Scrape Shield → Email Obfuscation** for the feature flag that produces this behaviour.
- Cloudflare HSTS Preload — <https://hstspreload.org>
