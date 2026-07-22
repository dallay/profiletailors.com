# Design: Publish Privacy Policy, Terms, Cookie Policy and Acceptable Use Policy

## Technical Approach

Static SSG policy pages following the existing thin-route → shared-component pattern (src:
`pages/index.astro` → `_HomePage.astro`). Policy content lives in typed i18n objects (Option A,
confirmed). Compliance data from `docs/compliance/` feeds Privacy Policy sections. Footer gains a
legal link group. Layout.astro gets dynamic canonical/hreflang support and `sameAs` JSON-LD.

## Architecture Decisions

| Option                             | Tradeoff                                                   | Decision                                                                          |
|------------------------------------|------------------------------------------------------------|-----------------------------------------------------------------------------------|
| i18n objects vs Markdown files     | Markdown needs separate renderer, breaks typed pattern     | i18n objects — keeps `as const` type safety across 4 policies × 2 locales         |
| Footer links as prop vs hardcoded  | Hardcoded is simpler but rigid                             | Prop `legalLinks` — Footer stays reusable; each page passes its own links         |
| Layout canonical override          | New prop vs auto-detect                                    | Add optional `canonicalPath` prop to Layout — minimal change, backward compatible |
| AUP standalone vs Terms subsection | Subsection reduces routes but harder to link from footer   | **Standalone** — common SaaS pattern, cleaner footer links                        |
| JSON-LD type per page              | WebApplication is wrong for legal docs; WebPage is correct | Pass optional `jsonLdType` prop to Layout; default stays `WebApplication`         |

## Component Tree

```
pages/{privacy,terms,cookies,acceptable-use}.astro   ← EN route wrapper
  └─ _PrivacyPolicy | _TermsPage | _CookiePolicyPage | _AcceptableUsePage.astro
       ├─ Layout(lang, title, description, canonicalPath, jsonLdType)
       │    ├─ hreflang/canonical/OG → uses canonicalPath
       │    └─ JSON-LD → uses jsonLdType
       └─ <article> policy content from i18n[locale].legal.{policy}
            └─ sections rendered from subsection keys

pages/es/{...}.astro                                    ← ES route wrapper (same component, locale="es")
Footer(tagline, copy, legalLinks)                       ← rendered on every page
  └─ <div class="legal-group">{legalLinks.map(...)}</div>
```

## i18n Shape

New top-level `legal` key in both `en.ts` and `es.ts` (existing pattern: `as const` objects,
Typescript infers through `typeof en`):

```typescript
// Inside the main export object — new top-level key
legal: {
  privacy: {
    title: string
    description: string
    lastUpdated: string
    intro: string
    dataController: string
    dataCollected: string       // references pa-001..pa-011 categories
    dataUsage: string
    dataSharing: string         // references controller-processor-matrix.md
    dataRetention: string       // references ropa.md retention schedules
    internationalTransfers: string
    yourRights: string           // GDPR/CCPA rights
    cookies: string              // cookie types + provider list
    policyChanges: string
    contact: string
  }
  terms: {
    title: string
    description: string
    lastUpdated: string
    // ... section keys per Terms structure
    serviceDescription: string
    accountTerms: string
    acceptableUse: string
    feesPayment: string
    intellectualProperty: string
    thirdPartyServices: string
    limitationLiability: string
    termination: string
    governingLaw: string
    contact: string
  }
  cookies: {
    title: string
    description: string
    lastUpdated: string
    whatAreCookies: string
    essentialCookies: string     // Vercel, Cloudflare
    analyticsCookies: string
    thirdPartyCookies: string
    manageCookies: string
    contact: string
  }
  aup: {
    title: string
    description: string
    lastUpdated: string
    prohibitedActivities: string
    enforcement: string
    reporting: string
    contact: string
  }
}
```

Footer links addition (append to existing `footer` key):

```typescript
footer: {
  copy: string
  tagline: string
  // NEW — static links same across locales except label translation
  legalLinks: Array<{ label: string; href: string }>
}
```

## Route Design

| URL                   | File                                                               | Locale |
|-----------------------|--------------------------------------------------------------------|--------|
| `/privacy/`           | `pages/privacy.astro` → `_PrivacyPolicy locale="en"`               | en     |
| `/terms/`             | `pages/terms.astro` → `_TermsPage locale="en"`                     | en     |
| `/cookies/`           | `pages/cookies.astro` → `_CookiePolicyPage locale="en"`            | en     |
| `/acceptable-use/`    | `pages/acceptable-use.astro` → `_AcceptableUsePage locale="en"`    | en     |
| `/es/privacy/`        | `pages/es/privacy.astro` → `_PrivacyPolicy locale="es"`            | es     |
| `/es/terms/`          | `pages/es/terms.astro` → `_TermsPage locale="es"`                  | es     |
| `/es/cookies/`        | `pages/es/cookies.astro` → `_CookiePolicyPage locale="es"`         | es     |
| `/es/acceptable-use/` | `pages/es/acceptable-use.astro` → `_AcceptableUsePage locale="es"` | es     |

**hreflang/canonical**: Pass `canonicalPath` to Layout. Layout computes:

- `canonicalEN = new URL('/privacy/', Astro.site).href` for `/privacy/`
- `canonicalES = new URL('/es/privacy/', Astro.site).href` for ES variant

## Data Flow

```
docs/compliance/data-inventory.yaml
  └─ pa-001..pa-011 → data categories, recipients, retention → Privacy Policy sections
docs/compliance/controller-processor-matrix.md
  └─ Third-party processor table → dataSharing section + cookie provider list
docs/compliance/ropa.md
  └─ Retention schedules → dataRetention section; rights descriptions → yourRights

src/i18n/en.ts + src/i18n/es.ts     ← drafted from compliance sources above
  └─ useTranslations(locale).legal.{policy}.{section}
       └─ _PrivacyPolicy.astro renders section content inside <Layout>
```

## Footer Design

The legal link group renders as a third row in the Footer, after tagline and copyright:

```
<footer>
  <div>PROFILE TAILORS · {tagline}</div>
  <p>{copy}</p>
  <nav aria-label="Legal">
    {legalLinks.map(link => <a href={link.href}>{link.label}</a>)}
  </nav>
</footer>
```

**Footer Props**:

```typescript
interface Props {
  tagline: string        // existing
  copy: string           // existing
  legalLinks: Array<{    // NEW — required
    label: string
    href: string
  }>
}
```

EN links:
`[{ label: 'Privacy Policy', href: '/privacy/' }, { label: 'Terms of Service', href: '/terms/' }, { label: 'Cookie Policy', href: '/cookies/' }, { label: 'Acceptable Use', href: '/acceptable-use/' }]`

ES links: same hrefs, translated labels.

## Layout Changes

- **New optional prop** `canonicalPath` (`string`): overrides the hardcoded root URLs in
  hreflang/canonical. Default: `'/'`.
- **New optional prop** `jsonLdType` (`string`): overrides `@type` in JSON-LD. Default:
  `'WebApplication'`. Policy pages pass `'WebPage'`.
- **New JSON-LD field** `sameAs`: add social profile URLs to the Organization block inside the
  JSON-LD (independent of page type).
- **Per-page meta**: each shared component passes `title` and `description` from
  `i18n.legal.{policy}.title` / `description`. OG image for policy pages uses a generic
  `/og-{lang}.png` or a dedicated legal OG image.

## Content References

| Policy Section                    | Compliance Source                                                          | Processing Activities                |
|-----------------------------------|----------------------------------------------------------------------------|--------------------------------------|
| Privacy – data collected          | `data-inventory.yaml` categories field per activity                        | pa-001..pa-011                       |
| Privacy – data usage              | `data-inventory.yaml` purposes field                                       | pa-001..pa-011                       |
| Privacy – data sharing            | `controller-processor-matrix.md` processor + independent controller tables | All third parties listed             |
| Privacy – retention               | `ropa.md` retention schedule column                                        | pa-001..pa-011                       |
| Privacy – international transfers | `controller-processor-matrix.md` location columns                          | Vercel US, social platforms US       |
| Privacy – your rights             | `ropa.md` legal basis column (Art. 6)                                      | GDPR Art. 15–22 rights               |
| Cookies – provider list           | `controller-processor-matrix.md` processor table                           | Vercel (CDN), Cloudflare (R2), Auth0 |
| Cookies – retention               | `data-inventory.yaml` pa-003 retention                                     | Access logs, analytics               |

## Implementation Order

1. **i18n** — add `legal` key to `en.ts` + `es.ts` (types flow automatically via `as const`). Draft
   content from compliance sources. Add `footer.legalLinks` to both files.
2. **Layout.astro** — add `canonicalPath` and `jsonLdType` props; compute hreflang dynamically; add
   `sameAs` to JSON-LD.
3. **Shared components** — create `_PrivacyPolicy.astro`, `_TermsPage.astro`,
   `_CookiePolicyPage.astro`, `_AcceptableUsePage.astro` in `src/pages/`.
4. **Route wrappers** — create 8 route files (4 EN + 4 ES). Each is ~4 lines: import + component
   with locale.
5. **Footer.astro** — add `legalLinks` prop; render nav group below existing content.
6. **Wire pages** — update `_HomePage.astro` and all 8 policy pages to pass `legalLinks` to Footer.
7. **Meta tests** — update `i18n/utils.test.ts` if new locale patterns added.

Steps 1–2 enable 3; steps 3–4 are parallel; step 5 blocks 6.
