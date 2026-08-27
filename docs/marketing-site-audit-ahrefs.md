# Marketing Site Audit — Ahrefs Findings and Remediation

## Overview

Audit of `profiletailors.com` (Astro marketing site) against Ahrefs Site Audit project `9293424`
(crawl date `2026-08-26T16:22:12Z`). Documents issues, fixes shipped in the repository, and the
Cloudflare dashboard steps the operator must perform manually because they live outside the repo.

## Changes

### Email obfuscation root cause (fixes three errors at once)

Cloudflare's automatic "Email Address Obfuscation" feature (default ON) wraps every raw email in
rendered HTML with `<a href="/cdn-cgi/l/email-protection#..." class="__cf_email__">`. That endpoint
returns 404, so every legal page was effectively linking to a broken URL.

**Fix:** convert raw email text in `apps/web/marketing/src/i18n/en.ts` and `es.ts` to markdown
`[contact@profiletailors.com](mailto:contact@profiletailors.com)` syntax. Cloudflare does not
obfuscate emails already wrapped in `<a href="mailto:...">`, so the broken
`/cdn-cgi/l/email-protection` links disappear.

Two addresses were involved: `contact@profiletailors.com` (privacy/terms/cookies/aup) and
`accessibility@profiletailors.com` (accessibility statement).

### Meta description length (fixes ten warnings)

Every legal page description was between 69 and 97 characters. SEO best practice for meta
description length is 120 to 160 characters; Ahrefs uses the same threshold for its "too short"
warning. All ten legal page descriptions (`privacy`, `terms`, `cookies`, `aup`, `accessibility`
across `en` and `es`) were expanded to the 120 to 160 range.

### Privacy page title (fixes one warning)

The `privacy.title` was `Privacy Policy` (14 characters), triggering Ahrefs' "title too short"
warning. Updated to `Privacy Policy — Profile Tailors` (32 characters) and the ES equivalent to
`Política de Privacidad — Profile Tailors`.

### Incoming dofollow links on legal pages (fixes two notices)

Legal page components rendered only the `<Footer>` legal nav, no top-level navigation. The EN
homepage `<Nav>` only linked to `/es/`, leaving `/es/` with one incoming dofollow link and ES
legal pages without a dofollow link from their EN counterpart.

**Fix:** import the existing `<Nav>` component into all five legal page components (`_PrivacyPolicy`,
`_TermsPage`, `_CookiePolicyPage`, `_AcceptableUsePage`, `_AccessibilityPage`), with the language
switcher pointed at the same-path ES counterpart. Each legal page now links to the apex home, every
other legal page, and its language counterpart.

## Pending Cloudflare Dashboard Steps

These changes live outside the repository and require operator action in the Cloudflare dashboard.

### 1. Configure the `www` subdomain

Current behavior (verified via `curl -I` on 2026-08-27):

```
http://www.profiletailors.com/  → 301 → https://www.profiletailors.com/
https://www.profiletailors.com/ → 302 → https://profiletailors-com.l.ink/
```

The `www` zone does not resolve to the marketing site; Cloudflare falls back to its link
shortener. This causes the "302 redirect" warning, the "Redirect chain" notice, and the duplicate
"HTTP to HTTPS redirect" notice for the same path.

**Resolution:** in the Cloudflare DNS for `profiletailors.com`, add a CNAME for `www` pointing to
the apex (or to the same Cloudflare Pages target). Then add a Bulk Redirect Rule of type
`Dynamic` matching `https://www.profiletailors.com/$1` and redirecting to
`https://profiletailors.com/$1` with status 301. After this, the only redirect from `www` should
be a single 301 to the apex.

### 2. Enable HSTS Preload

Cloudflare issues HTTP→HTTPS redirects on the first request from a client that has never visited
the site over HTTPS. Ahrefs flags every such redirect as a "HTTP to HTTPS redirect" notice.

**Resolution:** in the Cloudflare dashboard for `profiletailors.com`, go to **SSL/TLS → Edge
Certificates → Strict-Transport-Security** and enable HSTS with:

```
max-age=63072000; includeSubDomains; preload
```

Then submit the domain to <https://hstspreload.org> so the rule ships baked into browser
releases, eliminating the redirect entirely.

### 3. Review Cloudflare Email Obfuscation setting

Cloudflare Email Address Obfuscation is ON by default. After the repo fix (mailto links instead of
raw emails), the feature should no longer interfere with legal page links. Confirm in the
Cloudflare dashboard under **Scrape Shield → Email Obfuscation** that the feature remains enabled
or disable it if it is no longer needed.

## Troubleshooting

**Q:** After deploying, emails still appear as Cloudflare-obfuscated `/cdn-cgi/l/email-protection`
links.
**A:** Cloudflare caches pages at the edge. Purge the cache in the Cloudflare dashboard
**Overview → Caching → Configuration → Purge Everything**, then re-fetch a legal page with
`curl -s https://profiletailors.com/privacy/ | grep email-protection`. If the link still appears,
the Obfuscation feature is still active — see step 3 above.

**Q:** LCP on the home page is ~1100ms.
**A:** The hero animations (`apps/web/marketing/src/scripts/hero-animations.ts`) set initial
`opacity:0` and reveal elements with `await animation.finished` chains. This is progressive
enhancement with a `<noscript>` fallback. The long-term fix is CSS-driven entrance animations
(`view-transition-name` or `@starting-style`) that do not require JavaScript for the initial
state. Defer to a dedicated UX/performance change.

## Verification Commands

```sh
just frontend-check   # Astro type check
just frontend-lint    # Biome check
just frontend-test    # Vitest suite (99/99 passing)
just frontend-build   # Static build to apps/web/marketing/dist
```

Manual render verification after deploy:

```sh
grep -r "cdn-cgi/l/email-protection" dist/   # must return no matches
```

## References

- Ahrefs Site Audit project `9293424` — sign in at <https://app.ahrefs.com> to view crawl history and full issue list
- Cloudflare Email Address Obfuscation — **Scrape Shield → Email Obfuscation** in the
  Cloudflare dashboard
- Cloudflare HSTS Preload — <https://hstspreload.org>
