# Compliance — Future Baseline Reference

> **Status: Future baseline reference — not current controls.**

The documents in this directory represent a **comprehensive compliance model** intended for the
future roadmap. They cover scenarios (GDPR Articles 27-49, DPA, SCCs, breach notification, etc.)
that are **not applicable** to the current operational model of this project.

## Current controls

For the current operator-hosted instance (open-source AGPL-3.0 software), the legally effective
policies are published at:

- [Privacy Policy](https://profiletailors.com/privacy)
- [Terms of Service](https://profiletailors.com/terms)
- [Acceptable Use Policy](https://profiletailors.com/acceptable-use)
- [Cookie Policy](https://profiletailors.com/cookies)

These are maintained in `apps/web/marketing/src/i18n/{en,es}.ts` under the `legal.*` keys. Links use
absolute URLs on purpose so automated link checkers verify them against the published site.

Reference mapping from Awesome Legal categories to current and future Profile Tailors legal
artifacts is documented in
[`marketing-legal-baseline.md`](marketing-legal-baseline.md).

Status naming conventions for all compliance artifacts are defined in
[`status-taxonomy.md`](status-taxonomy.md).

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

## Register

Full file inventory grouped by use. Start with `status-taxonomy.md`, then the register you need.

| Group | Document |
| --- | --- |
| Entry points | [Status Taxonomy](./status-taxonomy.md), [Legal Document Register](./legal-document-register.md), [Legal Acceptance Record](./legal-acceptance-record.md), [Legal Publication Gate](./legal-publication-gate.md) |
| Processing records | [ROPA](./ropa.md), [Data Inventory](./data-inventory.md) (`data-inventory.yaml`), [Controller-Processor Matrix](./controller-processor-matrix.md), [Consent and Preference Register](./consent-and-preference-register.md) |
| Rights and retention | [Rights Request Runbook](./rights-request-runbook.md), [Retention and Erasure Control Plan](./retention-and-erasure-control-plan.md), [Underage Account Procedure](./underage-account-procedure.md) |
| Breach and incident | [Incident Response Runbook](./incident-response-runbook.md) (`incident-sla-table.yaml`), [Breach Authority Template](./breach-notification-authority-template.md), [Breach Subject Template](./breach-notification-subject-template.md) |
| Transfers and DPIA | [International Transfer Template](./international-transfer-assessment-template.md), [DPIA Screening](./dpia-screening-and-assessment.md), [Customer DPA Template](./customer-dpa-template.md) |
| Vendors | [Subprocessor Register](./subprocessor-register.md), [Vendor Due Diligence](./vendor-due-diligence-checklist.md), [Contributor Copyright Map](./contributor-copyright-map.md), [AGPL Source Offer](./agpl-source-offer.md) |
| Market activation | [Global Legal Readiness](./global-legal-readiness.md), [LATAM Matrix](./latam-applicability-matrix.md), [Brazil](./country-activation-br.md), [Canada](./country-activation-ca.md), [Mexico](./country-activation-mx.md), [Record Template](./country-activation-record-template.md) |
| Market packs | [US Privacy Pack](./us-privacy-market-pack.md), [Asia Entry](./market-entry-asia.md), [Japan Checklist](./readiness-checklist-japan.md), [Singapore Checklist](./readiness-checklist-singapore.md) |
| Marketing mapping | [Marketing Legal Baseline](./marketing-legal-baseline.md) |
