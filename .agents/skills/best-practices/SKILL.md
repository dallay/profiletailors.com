---
name: best-practices
description: Use for a focused web security, compatibility, or code-quality review when no more specific local skill owns the topic.
license: MIT
metadata:
  category: governance
  family: none
  source: local
  version: 2026-09-30
---
# Web Best Practices

Use this skill as a short review checklist. Repository instructions, product constraints, and
specialized local skills take precedence. Verify current browser, framework, dependency, and
security guidance from the project's manifests and authoritative documentation when the task
depends on changing facts.

## Security

- Serve application pages and assets over HTTPS. Avoid mixed content and protocol-relative URLs;
  use explicit HTTPS URLs for external resources.
- Set security headers at the server or edge boundary. Use a restrictive Content Security Policy,
  `Strict-Transport-Security` where HTTPS is enforced, `X-Content-Type-Options: nosniff`, an
  appropriate framing policy, `Referrer-Policy`, and `Permissions-Policy` based on the product's
  actual requirements.
- Do not recommend `X-XSS-Protection`; it is obsolete and modern browsers no longer use it as a
  security control.
- Prefer safe text and framework rendering for untrusted content. If rendering user-provided HTML
  is a real requirement, use a maintained sanitizer with a narrow policy and review the injection
  boundary.
- Keep secrets out of browser bundles, source control, logs, and error messages. Use the existing
  server-side secret management and deployment configuration.
- Request browser permissions only in a clear user flow and only when the feature needs them.

## Dependencies and commands

- Use the package manager and audit commands declared by the repository. Profile Tailors uses pnpm;
  read the root and package manifests before selecting a package filter or script.
- Do not run automatic dependency upgrades as a generic security fix. Review the advisory,
  compatibility, lockfile diff, and project update policy first.
- Prefer maintained browser features and explicit feature support policy. Do not add a polyfill CDN
  or use `polyfill.io`; choose a supported native feature or a reviewed, locally managed fallback.

## Browser and application quality

- Use semantic HTML and native controls when they fit the interaction. Preserve keyboard access,
  visible focus, accessible names, and useful error announcements.
- Give images intrinsic dimensions or an aspect ratio to prevent layout shifts, and provide
  meaningful alternative text when the image conveys information.
- Clean up event listeners, subscriptions, observers, and timers when their owner is removed.
- Handle empty, loading, success, failure, and permission-denied states where the feature needs
  them. Keep errors specific and actionable.
- Check reduced-motion preferences and avoid animation that blocks task completion.

## Review references

For Profile Tailors, read `.agents/AGENTS.md`, the surface `PRODUCT.md`, and `.agents/DESIGN.md`.
Use the local accessibility, security, frontend architecture, framework, and performance skills
when they own the issue. This checklist does not replace those instructions.
