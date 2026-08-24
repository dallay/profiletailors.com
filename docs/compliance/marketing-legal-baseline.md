# Marketing Legal Baseline Mapping

## Overview

This document maps the marketing legal documentation to the Awesome Legal reference guide and
clarifies what is currently active for the operator-hosted Profile Tailors instance.

It is a practical implementation map, not legal advice.

## Changes

| Version | Date       | Description                                                                                        |
|---------|------------|----------------------------------------------------------------------------------------------------|
| 1.0     | 2026-07-31 | Added Awesome Legal to Profile Tailors mapping for marketing legal pages and supporting templates. |

## Usage

### Current active marketing policies

The following policies are published and active for the operator-hosted instance:

- Privacy Policy: `/privacy/` and `/es/privacy/`
- Terms of Service: `/terms/` and `/es/terms/`
- Cookie Policy: `/cookies/` and `/es/cookies/`
- Acceptable Use Policy: `/acceptable-use/` and `/es/acceptable-use/`

Source of truth for policy text:

- `apps/web/marketing/src/i18n/en.ts`
- `apps/web/marketing/src/i18n/es.ts`

Publication control:

- `apps/web/marketing/src/legal/legal-publication.ts` must stay aligned with approved legal state.

### Awesome Legal category mapping

The table below maps the Awesome Legal categories to this project's current state.

| Awesome Legal category    | Profile Tailors implementation status                                       | Main artifact(s)                                                                         |
|---------------------------|-----------------------------------------------------------------------------|------------------------------------------------------------------------------------------|
| Site Policies             | Implemented for active operator-hosted instance                             | `apps/web/marketing/src/i18n/en.ts`, `apps/web/marketing/src/i18n/es.ts`                 |
| Customer Agreements       | Planned/future baseline depending on commercial model                       | `docs/compliance/customer-dpa-template.md`, `docs/compliance/legal-document-register.md` |
| Employee Agreements       | Future baseline (not part of current operator-hosted legal publication set) | `docs/compliance/legal-document-register.md`                                             |
| Consulting Agreements     | Future baseline (activate only when applicable to workforce/vendor model)   | `docs/compliance/legal-document-register.md`                                             |
| Advisor Agreements        | Future baseline (activate only when applicable)                             | `docs/compliance/legal-document-register.md`                                             |
| Investor Agreements       | Out of scope for marketing legal pages; corporate/legal operations topic    | `docs/compliance/legal-document-register.md`                                             |
| Founder Agreements        | Out of scope for marketing legal pages; corporate/legal operations topic    | `docs/compliance/legal-document-register.md`                                             |
| Non-Disclosure Agreements | Future baseline and contracting support, not public marketing policy page   | `docs/compliance/legal-document-register.md`                                             |
| Libraries                 | Used as drafting references only; not direct legal authority                | External references list in this document                                                |

### Recommended external drafting references

Use these references as drafting input with legal review, especially for plain-language structure:

- GitHub site policy examples
- Basecamp policy examples
- Automattic legal repository
- Common Paper standards (terms, cloud service agreement, professional services agreement, NDA)
- Cooley startup legal generators
- Orrick startup forms library
- Y Combinator SAFE and startup documents
- NVCA model legal documents

### Practical rules for this repository

- Keep legal text in marketing i18n files only; avoid duplicating operative policy text across docs.
- Keep EN and ES legal structures in strict key parity.
- Treat `docs/compliance/` as planning and control evidence unless a document is explicitly marked
  as
  active and published for users.
- Any new market-specific legal requirement must be staged in `docs/compliance/` first, then
  promoted
  to active marketing policy text after product, technical, business, and legal approvals.

## Troubleshooting

- If a legal route shows unavailable content, verify publication status in
  `apps/web/marketing/src/legal/legal-publication.ts`.
- If EN and ES legal pages diverge, run frontend tests and fix key parity in
  `apps/web/marketing/src/i18n/{en,es}.ts`.
- If compliance docs conflict with published policy wording, treat the published marketing policy
  pages
  as operative for the current operator-hosted instance and open a documentation reconciliation
  task.

## References

- Awesome Legal: <https://github.com/openlawlibrary/awesome-legal>
- Marketing app README: `apps/web/marketing/README.md`
- Compliance index: `docs/compliance/README.md`
- Consent implementation: `docs/consent-management.md`
