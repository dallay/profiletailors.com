// src/i18n/en.ts
export const en = {
  nav: {
    name: 'Profile Tailors',
    langSwitch: 'ES',
  },
  hero: {
    label: 'EARLY ACCESS PREVIEW',
    headline: 'Plan clearly,\npublish deliberately.',
    sub: 'Plan and organise social content from one clean workspace. Publishing integrations are still being validated before early access opens.',
    status: 'Early-access registration is not open yet.',
  },
  features: {
    label: 'WHY PROFILE TAILORS',
    items: [
      {
        tag: '01 — PLAN',
        title: 'Shape content before it ships.',
        desc: 'Draft and organise content in a focused workspace while publishing connections are validated.',
      },
      {
        tag: '02 — OVERVIEW',
        title: 'Review the full pipeline.',
        desc: 'Use a clear calendar and workflow to review what is planned and what still needs attention.',
      },
      {
        tag: '03 — EARLY ACCESS',
        title: 'A product still being validated.',
        desc: 'Features, integrations, markets, and commercial terms will be announced only after they are ready.',
      },
    ],
  },
  footer: {
    copy: 'Profile Tailors — early access preview.',
    tagline: 'A social content workspace in development.',
    legalLinks: [
      { label: 'Privacy Policy', href: '/privacy/' },
      { label: 'Terms of Service', href: '/terms/' },
      { label: 'Cookie Policy', href: '/cookies/' },
      { label: 'Acceptable Use', href: '/acceptable-use/' },
    ],
  },
  meta: {
    title: 'Profile Tailors — Social content planning in development',
    description:
      'Preview Profile Tailors, a social content planning workspace currently in development. Early-access registration is not open yet.',
  },
  legal: {
    publication: {
      unavailableTitle: 'Legal document not yet available',
      unavailableBody:
        'This document is undergoing factual and qualified legal review. It is not in effect and has not been approved for publication. Profile Tailors is not accepting reliance on or agreement to a draft policy.',
      unavailableAction: 'Return to the home page',
      unavailableHref: '/',
    },
  },
  waitlist: {
    formAriaLabel: 'Early access waitlist form',
    emailLabel: 'EMAIL',
    emailInput: {
      placeholder: 'Email address',
      ariaLabel: 'Email address',
    },
    consentEarly: {
      label: 'I want early product access.',
      ariaLabel: 'Early access consent',
    },
    consentMarketing: {
      label: 'I agree to receive marketing emails.',
      ariaLabel: 'Marketing consent',
    },
    submit: 'REQUEST ACCESS',
    errorValidEmail: 'Please enter a valid email.',
    errorConsentRequired: 'Early-access consent is required.',
    errorTooManyRequests: 'Too many requests. Try again in a minute.',
    errorGeneric: 'Could not register you. Please try again.',
    success: "You're on the list.",
  },
} as const
