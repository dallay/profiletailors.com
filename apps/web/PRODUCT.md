# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Users

Three audiences across the web surfaces, in order of product maturity:

1. **Early-access prospects** (marketing surface) — people interested in social media management who
   arrive via search, referrals, or links. Most have not committed; some are actively evaluating.
   Their job: decide whether Profile Tailors is worth joining the waitlist.
2. **Authenticated early-access users** (app surface) — individuals managing social presence for a
   personal brand, freelance work, or small business. Mostly non-technical. Their job: plan and
   schedule LinkedIn content from a focused workspace.
3. **Platform operator** (admin + marketing compliance) — sole admin at early-access stage. Their
   job: manage waitlist, users, permissions, and audit activity; publish legal compliance documents.

## Product Purpose

Profile Tailors is a social media management platform — schedule, publish, analyze, engage, and
collaborate. At early-access stage, the shipped truth is narrower: convert prospects into waitlist
registrations (marketing), let authenticated users create, schedule, and review LinkedIn content
from a clean calendar-driven workspace (app), and give the operator tools to run early-access
operations (admin). Success is measured by fewer tools to juggle and more confidence that content
reaches the right platforms.

## Positioning

Not another generic social media dashboard or scheduler. Profile Tailors is a focused, opinionated
workspace for deliberate content — not an AI content generator, not a discovery tool, not a full
agency suite. LinkedIn is the first integrated platform; other integrations follow only after
validation. The interface earns trust through scanability, precision, and restraint — the
Nothing-inspired aesthetic signals that minimalism is a feature, not a limitation.

## Operating Context

- Three web surfaces, all inheriting the same root design system: marketing (Astro, public),
  app (Vue SPA, authenticated), admin (Vue SPA, operator-only, no public URL)
- Backend is a Spring Boot 4 reactive service (Kotlin, WebFlux, R2DBC) with hexagonal architecture
- EU/UK/ES legal frameworks apply (GDPR, EAA monitoring status, Spanish governing law); consent
  management spans marketing and app via a shared layer
- EU data residency: Oracle Cloud Frankfurt; CDN: Cloudflare global edge
- Early-access stage: publishing integrations are still being validated; waitlist submission is
  client-side only (no backend persistence yet per ADR-0011)
- Licensed under AGPL-3.0; legal documents published on the marketing surface

## Capabilities and Constraints

- **Marketing:** static Astro pages with EN/ES locale routing; waitlist form (email + consent);
  legal docs (privacy, ToS, cookies, AUP, accessibility statement)
- **App:** calendar-driven scheduler (week/day/month + list); create, edit, delete, reorder posts;
  media library; ideas capture; governance takedown workflow; settings (profile, workspace,
  integrations, API keys); auth flows (login, register, password reset, email verify, LinkedIn
  callback)
- **Admin:** dashboard, waitlist review, user administration, audit log review; permission-gated
  routes (`platform.*` scope)
- **Constraints:** LinkedIn is the only connected platform at early-access; publishing is gated
  until integrations are validated; analytics surface exists but data pipeline is TBD; no
  multi-workspace UI yet for non-owner members
- **Undecided:** pricing / business model; roadmap beyond early-access; expansion platform list

## Brand Commitments

- Name: "Profile Tailors"; domain `profiletailors.com` (domain is not the product name)
- Tagline: "Schedule smarter. Post everywhere."
- Aesthetic: Nothing-inspired monochrome, dark-first; Swiss typography + industrial design
  references; Doto (hero), Space Grotesk (body/UI), Space Mono (labels/data)
- Color philosophy: color is an event, not a default
- Tone: honest, restrained, precise, not promotional; copy does not oversell

## Evidence on Hand

- Real i18n strings maintained per surface (`src/i18n/` in marketing and app) — not fabricated
- Real marketing and legal copy in marketing `src/i18n/en.ts` and `src/i18n/es.ts`
- No real customer testimonials, benchmarks, or social proof (absent — future work must not
  fabricate)
- No live pricing (undecided)
- No real analytics data yet (surface built, data pipeline TBD)

## Product Principles

1. Honesty over conversion — do not promise features not yet validated
2. Restraint signals quality — every element earns its pixel; scanability first on operational
   surfaces
3. Compliance is structural — consent, legal documents, and audit trails are accurate and current,
   not boilerplate
4. Accessible by default — WCAG 2.2 AA is a floor across every surface, including internal tools
5. Errors and actions are specific — copy explains what happened and what to do; no generic "something went wrong"

## Accessibility & Inclusion

WCAG 2.2 Level AA target across all web surfaces. Automated axe-core checks gate PRs; manual
screen-reader testing ongoing. Known gaps shared across surfaces: calendar keyboard navigation,
media picker drag-and-drop keyboard alternative, third-party embed contrast. EAA applies as a
monitoring exercise at early-access stage; not yet a binding compliance obligation.
