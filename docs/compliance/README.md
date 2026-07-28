# Compliance — Future Baseline Reference

> **Status: Future baseline reference — not current controls.**

The documents in this directory represent a **comprehensive compliance model** intended for the
future roadmap. They cover scenarios (GDPR Articles 27-49, DPA, SCCs, breach notification, etc.)
that are **not applicable** to the current operational model of this project.

## Current controls

For the current operator-hosted instance (open-source AGPL-3.0 software), the legally effective
policies are published at:

- [Privacy Policy](/privacy)
- [Terms of Service](/terms)
- [Acceptable Use Policy](/acceptable-use)
- [Cookie Policy](/cookies)

These are maintained in `apps/web/marketing/src/i18n/{en,es}.ts` under the `legal.*` keys. The
leading slash in each link is intentional: these are deployed marketing routes, not files inside
the repository, so filesystem-only Markdown link checkers may report them as unresolved.

## When to use these documents

These templates and registers are the compliance target for a future where the project operates
as a multi-tenant B2B service with employees, DPA counterparties, and formal privacy
governance. Until then, they serve as:

1. **Reference** — structure to consult when expanding obligations.
2. **Planning** — input for future budget or resourcing decisions.
3. **Traceability** — evidence that the topics have been considered and deferred deliberately.

## Updating

If the operational model expands (e.g., the operator takes on subprocessors, enters DPAs, or
processes data under Article 27), these documents should be reviewed against the new reality and
either adopted or archived accordingly.
