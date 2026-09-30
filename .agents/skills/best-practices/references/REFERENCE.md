# Web Review Reference

Use this checklist with `.agents/skills/best-practices/SKILL.md`. Repository rules and focused
local skills remain authoritative.

## Security

- HTTPS is used consistently, with no mixed content.
- Security headers are set at the server or edge boundary and match the deployed architecture.
- The obsolete `X-XSS-Protection` header is not treated as a security control.
- Untrusted text is rendered safely; HTML injection has a reviewed sanitization boundary where
  required.
- Secrets remain on the server and outside source control and browser bundles.
- Dependency advisories are reviewed against the repository's package manager, manifests, and
  compatibility policy before upgrades.

## Accessibility and behavior

- Controls have semantic roles, accessible names, visible focus, and keyboard behavior.
- Forms provide specific errors and announce them appropriately.
- Loading, empty, success, and failure states are understandable for the task.
- Motion respects the user's reduced-motion preference.
- Images reserve their layout space and have appropriate alternative text.
- Event listeners, observers, subscriptions, and timers are cleaned up with their owner.

## Local tools

Use the existing repository commands. Profile Tailors uses pnpm; inspect `package.json` and the
package manifest for the exact script. Do not introduce a second package manager or run bulk
dependency upgrades as a default response to an advisory.

For a surface-specific review, combine this checklist with its `PRODUCT.md`, `.agents/DESIGN.md`,
and the most specific local skill.
