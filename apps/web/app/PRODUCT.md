# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Vue 3 SPA (Vue Router 5, Pinia), shadcn-vue + Tailwind CSS 4, vue-i18n, Zod, Vitest, Playwright E2E.

## Users

Authenticated early-access users managing social media presence for personal brand, freelance work, or small business. Primary use case: plan and schedule LinkedIn content from a focused workspace. Most users are non-technical. One operator account exists for platform management.

## Product Purpose

Let users create, schedule, and review social content from a clean workspace. The scheduler is the core product — a calendar-driven view of the publishing pipeline. Analytics, media library, ideas capture, and governance tools are supporting surfaces. The user measures success by having fewer tools to juggle and more confidence that content reaches the right platforms.

## Positioning

Not a generic social media dashboard. Profile Tailors is a workspace for deliberate content — not an AI content generator, not a discovery tool, not a full agency suite. LinkedIn is the first integrated platform; other integrations follow after validation. The interface earns trust through scanability and precision, not novelty.

## Operating Context

- Authenticated users access via `https://pt-app.localhost` (production: `https://app.profiletailors.com`)
- Users connect one or more LinkedIn accounts via OAuth
- Content creation and scheduling happen in-browser; publishing executes server-side via stored OAuth tokens
- Dashboard state (workspace, theme, sidebar) persists in localStorage
- Session is maintained via HttpOnly refresh cookie (`pt_refresh`)
- EU data residency: Oracle Cloud Frankfurt; CDN: Cloudflare global edge
- Analytics loading is gated by user consent (banner → localStorage → `window.__PT_CONSENT_ANALYTICS`)

## Capabilities and Constraints

**Authenticated surfaces:**
- Dashboard home: welcome, quick-create post, pipeline overview
- Scheduler: calendar (week/day/month) + list view; create, edit, delete, reorder posts
- Analytics: publication performance metrics (planned — surface exists, data connection TBD)
- Media Library: upload, browse, organise uploaded assets
- Ideas: capture and organise content ideas (planned — surface exists)
- Governance: content takedown request workflow
- Settings: profile, workspace, integrations (LinkedIn OAuth), API keys
- Auth flows: login, register, forgot-password, reset-password, verify-email, LinkedIn callback

**Constraints:**
- LinkedIn is the only connected platform at early-access stage
- Publishing is gated until integrations are validated
- No multi-workspace UI yet for non-owner members
- Admin surface is a separate app (`@profiletailors/admin`) — not reachable from this app

## Brand Commitments

- Name: "Profile Tailors" (app shell shows product name, not app-level branding)
- Aesthetic: inherits root DESIGN.md — Nothing-inspired monochrome, dark-first, Space Grotesk / Space Mono / Doto
- Tone: functional, precise. No gamification, no celebration animations
- No third-party branding in UI chrome — integrations show their own logos

## Evidence on Hand

- Real i18n strings maintained in `src/i18n/` (not fabricated)
- No real analytics data yet (surface built, data pipeline TBD)
- No real user-generated content at early-access stage

## Product Principles

1. Scanability first — the dashboard answers "what is planned and what needs attention" in one glance
2. Consistent conventions — every surface follows the same component language, spacing, and interaction model
3. Consent is structural — analytics cannot load without a valid consent receipt; the app degrades gracefully
4. Errors are specific — copy explains what went wrong and what to do; never generic "something went wrong"
5. Auth is secure by default — HttpOnly cookies, no token in URL, session hydration on every load

## Accessibility & Inclusion

Marketing PRODUCT.md targets WCAG 2.2 AA. Same accessibility commitment extends here. Additional app-specific concerns: calendar keyboard navigation, media picker drag-and-drop keyboard alternative, form validation announcements.
