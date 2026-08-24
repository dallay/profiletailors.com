# Legal Publication Gate

> **Classification:** Internal — Legal and Compliance
> **Status:** Active — publication approved for current operator-hosted policies
> **Review date:** 2026-07-17

## Overview

This gate prevents draft legal pages from being treated as production-ready because they build,
render, or satisfy an internal content specification. It applies to the Privacy Policy, Terms of
Service, Cookie Policy, Acceptable Use Policy, legal notice, DPA, subprocessor list, AI notice,
consumer notices, and country addenda.

The current Profile Tailors legal pages for the operator-hosted instance are approved for public
rendering. The controlling status in `apps/web/marketing/src/legal/legal-publication.ts` is set to
`approved`.

This gate remains the control point for future legal changes. Any material update (entity,
jurisdiction, providers, commercial model, or data practice) requires a new approval cycle and may
require temporarily returning publication status to `blocked` until approvals complete.

## Changes

| Version | Date       | Description                                                                     |
|---------|------------|---------------------------------------------------------------------------------|
| 2.1     | 2026-07-31 | Reconciled gate status with approved legal publication runtime state            |
| 2.0     | 2026-07-17 | Added the enforced blocked-render state and reconciled corrected draft findings |
| 1.0     | 2026-07-17 | Added factual, operational, jurisdictional, and approval gates                  |

## Usage

### Required approvals

A policy version may be published only when all four approvals are recorded:

| Approval        | Required signer                                     | What the signer confirms                                                                                                                    |
|-----------------|-----------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| Product truth   | Product owner                                       | Features, audience, billing, integrations, and user flows match the text.                                                                   |
| Technical truth | Engineering or security owner                       | Data flows, vendors, regions, cookies/storage, retention, deletion, rights, and security controls have linked evidence.                     |
| Business truth  | Authorised representative of the contracting entity | Entity, address, registration, contacts, prices, taxes, support, and commercial promises are correct.                                       |
| Legal approval  | Qualified counsel for the enabled markets           | Applicable law, local language, rights, transfers, consumer terms, dispute clauses, and required representatives or filings are acceptable. |

Approval records must contain the policy name, immutable version or commit, languages, enabled
countries, signer, date, review expiry, and qualifications or open risks.

### Blocking checklist

Any unchecked item blocks publication:

- [ ] Contracting entity's exact legal name, legal form, registration/tax identifiers, registered
  address, and authorised representative are recorded.
- [ ] `privacy@`, `legal@`, `abuse@`, security reporting, and support contacts exist, are monitored,
  and have response owners and service levels.
- [ ] B2B/B2C scope, minimum age, enabled countries, language, pricing, billing, tax, cancellation,
  refund, and withdrawal decisions are final.
- [ ] Every named vendor is the selected production vendor; its role, service, data, country/region,
  DPA, subprocessors, and transfer mechanism are verified.
- [ ] Cookie, SDK, pixel, browser storage, CDN/security-cookie, and analytics behaviour has been
  measured in production-like builds before and after consent choices.
- [ ] Each processing purpose has the correct role, lawful basis, data categories, source,
  recipients, transfer mechanism, and implemented retention criterion.
- [ ] Access, correction, deletion, export, objection/opt-out, consent withdrawal, and appeal flows
  required by enabled markets have tests and accountable operators.
- [ ] Retention promises are backed by deletion/anonymisation jobs, backup treatment, exception
  handling, and test evidence.
- [ ] Contract acceptance records policy version, timestamp, actor/principal, workspace, locale,
  and the immutable accepted text.
- [ ] Terms distinguish AGPL-3.0 software rights from the SaaS service, trademarks, logos, domain,
  and non-code content, and the deployed source-availability obligation is satisfied.
- [ ] Incident procedures map the strictest applicable assessment and notification deadlines and
  have a tested escalation route.
- [ ] AI features, models, vendors, inputs, outputs, training settings, disclosures, prohibited
  uses, and human-review controls are documented.
- [ ] Each enabled market has an approval row in
  [`global-legal-readiness.md`](global-legal-readiness.md) and a qualified-counsel reference.
- [ ] EN and ES versions are substantively equivalent, all internal links preserve locale, and no
  heading, banner, path, or operative clause is accidentally left in the other language.
- [ ] There are no placeholders, slash-separated vendor alternatives, future providers, planned
  safeguards stated as current, or absolute compliance/security claims.

### Findings register

| ID      | Status                                                     | Finding                                                                                                                                                             | Required resolution or evidence                                                                                      |
|---------|------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| LPG-001 | Open — publication blocker                                 | The operating legal entity, registered address, registration details, and authorised representative are unresolved.                                                 | Record the actual legal person and all mandatory corporate disclosures.                                              |
| LPG-002 | Draft corrected; operational blocker remains               | The EN/ES draft no longer names unsupported hosting, identity, storage, monitoring, or social providers. Production vendors and contracts are still unselected.     | Select and contract the production stack, then generate the recipient disclosure from verified evidence.             |
| LPG-003 | Draft corrected; measurement blocker remains               | The cookie draft now reflects repository evidence and Ahrefs' default cookieless behaviour. Production-like browser and network measurement has not been completed. | Run the storage scan for every representative state and approve consent classifications.                             |
| LPG-004 | Draft corrected; control blocker remains                   | Unsupported fixed schedules were removed. General deletion and anonymisation controls remain absent for most activities.                                            | Implement and test controls or legally approve purpose-based criteria supported by operations.                       |
| LPG-005 | Draft corrected; source-offer blocker remains              | The Terms draft now preserves AGPL-3.0 permissions and separates service, marks, and customer content conceptually.                                                 | Verify the deployed corresponding-source route and obtain legal approval of the final clause.                        |
| LPG-006 | Open — publication blocker                                 | Governing law, B2B/B2C scope, countries, consumer terms, pricing, refunds, liability, and dispute rules are unresolved.                                             | Decide the entity, customer model, and launch countries; obtain market-specific counsel approval.                    |
| LPG-007 | Open — publication blocker                                 | There is no demonstrated clickwrap or immutable version-acceptance record.                                                                                          | Implement versioned acceptance evidence before account creation, paid use, or reliance on the Terms/AUP.             |
| LPG-008 | Corrected                                                  | The archived false-positive verification is superseded and the canonical spec separates technical evidence from approval.                                           | Preserve the archive warning and require the four approval classes for every publication.                            |
| LPG-009 | Corrected as a draft register; operational blockers remain | The inventory, ROPA, and processor matrix now label provider, basis, transfer, and retention evidence explicitly.                                                   | Resolve every pending or unverified field before deriving public claims.                                             |
| LPG-010 | Open — publication blocker                                 | No country in the EU/EEA, Americas, or Asia has a recorded activation approval.                                                                                     | Complete the country activation checklist for the intended launch scope.                                             |
| LPG-011 | Resolved for current publication baseline                  | Draft bodies previously rendered at production routes despite a warning comment.                                                                                    | Keep `legalPublicationStatus` aligned with approved baseline and re-run this gate before each material legal change. |

### Evidence record template

```text
Policy:
Version or commit:
Languages:
Enabled countries:
Product approval (name/date/reference):
Technical approval (name/date/reference):
Business approval (name/date/reference):
Legal approval (counsel/date/reference):
Known limitations:
Next review date:
Rollback owner and procedure:
```

### Separation of verification responsibilities

| Verification             | Can establish                                                                       | Cannot establish                                                                                   |
|--------------------------|-------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------|
| Build and tests          | Routes render, translations have matching keys, links and markup satisfy assertions | Legal applicability, factual vendor use, enforceability, consent validity, or operational deletion |
| Product/technical review | Behaviour, data flows, vendors, storage, and implemented controls                   | Local-law interpretation or enforceability                                                         |
| Legal review             | Applicability and wording for documented facts and markets                          | Whether undocumented product behaviour or controls actually exist                                  |
| Release approval         | All evidence and approvals are present for a version and market set                 | Permanent compliance after product, vendor, law, or market changes                                 |

## Troubleshooting

- **The page has a draft banner:** Verify publication status and approval evidence before enabling
  production rendering.
- **Counsel approved an earlier version:** Approval does not carry over automatically. Produce a
  legal-text and product-behaviour diff and obtain confirmation for the new immutable version.
- **A vendor is undecided:** Use a non-public template or internal placeholder. Do not name vendor
  alternatives in a public policy.
- **A deadline or retention period is unknown:** State the purpose-based criterion internally,
  obtain counsel input, and do not invent a fixed public promise.
- **A technical test passes but this checklist fails:** Do not publish new legal text until all
  required approvals are complete.

## References

- [`global-legal-readiness.md`](global-legal-readiness.md)
- [`data-inventory.yaml`](data-inventory.yaml)
- [`controller-processor-matrix.md`](controller-processor-matrix.md)
- [`ropa.md`](ropa.md)
- [European Commission — information that organisations must provide](https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/obligations_en)
- [US FTC — privacy and security guidance](https://www.ftc.gov/business-guidance/privacy-security)
