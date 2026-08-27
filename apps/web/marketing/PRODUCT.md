# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Astro 6 (EN + ES via locale routing), Tailwind CSS 4, Vue 3 island (waitlist), Biome.

## Users

Early-access prospects — people interested in social media management who discover the product through search, referrals, or links. Most have not committed. Some are early adopters actively evaluating. Operator (Yuniel Acosta) also uses this surface for legal compliance publication.

## Product Purpose

Convert early-access prospects into registered users on the waitlist. Serve legal documents that govern the operator-hosted instance and the software under AGPL-3.0. The site earns trust, manages expectations (publishing is still being validated), and collects consent for future contact.

## Positioning

Not another social media scheduler landing page. Profile Tailors is a focused, opinionated workspace — deliberately minimal, non-generative, not trying to be everything. The Nothing-inspired aesthetic signals that restraint is a feature. "Early access preview" manages expectations honestly: publishing integrations are still being validated.

## Operating Context

- Prospects arrive via direct search, SEO, or referral links
- Mobile and desktop visitors browse the landing page and legal pages
- Some visitors complete the waitlist form; others read the legal docs
- No authenticated user experience exists yet — this is the only public surface
- EU/UK/ES legal frameworks apply (GDPR, EAA monitoring status, Spanish governing law)

## Capabilities and Constraints

- Static Astro pages with locale routing (EN default, ES under `/es/`)
- Waitlist form collects email + consent; submission is client-side only (no backend yet per ADR-0011)
- No commercial transactions on this surface
- LinkedIn OAuth is planned but not yet live
- Legal documents (privacy policy, ToS, cookies, AUP, accessibility statement) are published here
- Accessibility target: WCAG 2.2 Level AA; automated axe-core checks on PRs; manual review ongoing
- No advertising, tracking, or analytics cookies by default (minimal localStorage + theme preference only)
- Consent is disclosed in a fixed, non-modal bottom banner that never blocks page navigation; detailed category choices open inline from "Customize" or the footer settings link

## Brand Commitments

- Name: "Profile Tailors"
- Tagline: "Schedule smarter. Post everywhere."
- Aesthetic: Nothing-inspired monochrome, dark-first; Swiss typography + industrial design references
- Fonts: Doto (hero), Space Grotesk (body/UI), Space Mono (labels/data)
- Color philosophy: Color is an event, not a default
- Tone: Honest, restrained, not promotional. Copy does not oversell.

## Evidence on Hand

- Marketing copy and legal copy maintained in `src/i18n/en.ts` and `src/i18n/es.ts`
- No real customer testimonials, benchmarks, or social proof (absent — future work must not fabricate)
- No live pricing (not yet decided)

## Product Principles

1. Honesty over conversion — do not promise features not yet validated
2. Restraint signals quality — every element earns its pixel
3. Compliance is structural — legal documents are accurate and current, not boilerplate
4. Accessible by default — WCAG 2.2 AA is a floor, not a target
5. Language respects the reader — no filler copy, no aggressive CTAs

## Accessibility & Inclusion

WCAG 2.2 Level AA target. Automated axe-core checks gate every PR. Manual screen-reader testing ongoing. Known gaps: calendar keyboard navigation, media picker drag-and-drop, third-party embed contrast (see accessibility statement). EAA applies as a monitoring exercise at early-access stage; not yet a binding compliance obligation.
