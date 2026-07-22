# Data Inventory

> **Classification:** Internal — Legal and Compliance
> **Status:** Draft — production publication blocked
> **Schema version:** 2.0
> **Last verified:** 2026-07-17

## Overview

This document summarises the personal-data processing evidenced in the Profile Tailors repository.
The machine-readable source of truth is [`data-inventory.yaml`](data-inventory.yaml).

The contracting and processing legal person is unresolved. “Profile Tailors” and “Dallay” are
brand or project identifiers and MUST NOT be substituted for the controller's legal identity.
No production host, CDN, PostgreSQL host, object-storage provider, or managed observability
provider is selected or evidenced in this repository.

Version 2.0 separates four concepts that the previous inventory mixed together:

- **Implemented behaviour:** code and configuration prove that a data flow or control exists.
- **Configurable integration:** an adapter exists but production activation is not proved.
- **Selected provider:** an executed production arrangement is evidenced.
- **Promised control:** a legal or retention statement approved for public use.

Only the first category is broadly available today. A configurable adapter is not a selected
provider, and a database field or proposed duration is not an implemented deletion control.

## Changes

| Version | Date       | Description                                                                                                |
|---------|------------|------------------------------------------------------------------------------------------------------------|
| 1.0     | 2026-07-17 | Initial inventory containing unverified providers and retention promises                                   |
| 2.0     | 2026-07-17 | Revalidated against code; added evidence states and browser storage; removed unsupported production claims |

## Usage

Use the YAML inventory for privacy notices, DPAs, transfer mapping, DPIA screening, incident
assessment, and retention implementation only after checking each activity's `evidence_status`,
`legal_review_status`, recipient activation, agreement status, and retention-control status.

An activity with `legal_review_status: pending` MUST NOT supply a public lawful-basis statement.
A recipient with `activation_status` other than `selected` MUST NOT be named as a current
production recipient. A retention period with `control_status` other than `implemented` MUST NOT
be described as an operational guarantee.

### Evidence summary

| ID     | Activity                           | Evidence      | Retention control | Principal finding                                                                                                                                           |
|--------|------------------------------------|---------------|-------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| pa-001 | Accounts, authentication, sessions | Partial       | Partial           | Local/federated JWT and Resend adapter exist; Auth0/Clerk do not appear as current providers; account erasure is missing.                                   |
| pa-002 | Social publishing and scheduling   | Partial       | Partial           | Only LinkedIn has real backend adapters; unpublished hard deletion exists; time-based post-termination cleanup is missing.                                  |
| pa-003 | Hosting and delivery               | Not evidenced | Missing           | The repository produces static/app artifacts but does not select Vercel, Cloudflare, or another production host.                                            |
| pa-004 | Waitlist                           | Partial       | Missing           | Backend consent fields and persistence exist, but the public collection form is disabled; withdrawal, anonymisation, and an approved schedule do not exist. |
| pa-005 | Workspaces and membership          | Partial       | Missing           | Authorization exists; workspace/member erasure and the claimed 30-day anonymisation do not.                                                                 |
| pa-006 | LinkedIn connection credentials    | Partial       | Missing           | AES-GCM credential protection exists; automatic erasure after revocation is not evidenced.                                                                  |
| pa-007 | API keys                           | Partial       | Missing           | BCrypt verification and revocation exist; the claimed 90-day purge does not.                                                                                |
| pa-008 | Media storage and import           | Verified      | Implemented       | Physical media objects are garbage-collected after seven days; database rows remain with a terminal status.                                                 |
| pa-009 | Publication delivery               | Partial       | Partial           | Worker and delivery records exist; the claimed seven/90-day cleanup does not.                                                                               |
| pa-010 | Audit and governance logs          | Partial       | Missing           | Insert/read paths exist and hooks are configurable; no one/five-year control or database immutability proof exists.                                         |
| pa-011 | Analytics and observability        | Partial       | Missing           | Prometheus metrics and conditional Ahrefs exist; Sentry/Vercel Analytics and claimed retention periods do not.                                              |
| pa-012 | Cookies and browser storage        | Verified      | Partial           | The previous inventory omitted authentication/UI cookies and local storage, including locally persisted publication content.                                |

### Verified provider and integration state

| Function            | Repository evidence                                                                                        | Production conclusion                                                                                  |
|---------------------|------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| Authentication      | Local HS256 JWT issuer, configurable federated JWT validation, BCrypt credentials, HttpOnly refresh cookie | No external identity provider is selected; do not name Auth0 or Clerk.                                 |
| Transactional email | Resend adapter conditional on `SMP_RESEND_API_KEY`                                                         | Configurable, not proved active or contracted.                                                         |
| Database            | PostgreSQL through R2DBC and Liquibase                                                                     | Technology verified; hosting company, country, DPA, and transfer mechanism unknown.                    |
| Media storage       | Local filesystem default; S3 and Cloudflare R2 adapters                                                    | Adapter support verified; production provider and bucket region unknown.                               |
| Social publishing   | LinkedIn OAuth, profile, asset upload, publish, and refresh adapters                                       | LinkedIn is configurable; other social platforms are not evidenced as production backend integrations. |
| External media      | Unsplash adapter disabled by default                                                                       | Conditional, not proved active or contracted.                                                          |
| Marketing analytics | Ahrefs script conditional on `AHREFS_ANALYTICS_KEY`                                                        | Conditional. Provider role, contract, region, and observed production behaviour remain unverified.     |
| Metrics             | Spring Actuator/Prometheus endpoint and application metrics                                                | Collection capability exists; no managed metrics recipient or retention schedule is selected.          |
| Error tracking      | Application logs                                                                                           | No Sentry integration was located. Log destination and retention are unknown.                          |
| Hosting/CDN         | Static Astro and Vue build configuration                                                                   | No production provider selection is evidenced.                                                         |

### Browser storage register

| Name                       | Mechanism                  | Content                                          | Default lifetime    | Current control                                                    |
|----------------------------|----------------------------|--------------------------------------------------|---------------------|--------------------------------------------------------------------|
| `pt_refresh`               | HttpOnly cookie            | Refresh-session secret                           | Seven days          | Revoked and cleared on logout; Secure by default; `/api/auth` path |
| `sidebar_state`            | JavaScript-readable cookie | Sidebar open/closed boolean                      | Seven days          | No SameSite or Secure attribute is set by the component            |
| `theme`                    | Local storage              | Marketing theme                                  | No automatic expiry | User choice or browser clearing                                    |
| `pt_settings_v1`           | Local storage              | Dashboard locale and theme                       | No automatic expiry | Overwritten by user settings or browser clearing                   |
| `pt_active_workspace_id`   | Local storage              | Workspace identifier                             | No automatic expiry | Store exposes reset, but logout integration is not evidenced       |
| `pt_active_workspace_name` | Local storage              | Workspace name                                   | No automatic expiry | Store exposes reset, but logout integration is not evidenced       |
| `pt_publications`          | Local storage              | Publication content, channel and scheduling data | No automatic expiry | Overwritten by dashboard actions or browser clearing               |
| `pt-dashboard-new`         | Local storage              | Feature-layout preference                        | No automatic expiry | Overwritten by user toggle or browser clearing                     |

The Cookie Policy must describe device storage by function, not only traditional cookies. Whether
each item is strictly necessary requires market-specific legal review; storage that is convenient
or used for a development feature is not automatically necessary.

### Retention implementation register

| Control                                      | State                  | Evidence or missing work                                                                         |
|----------------------------------------------|------------------------|--------------------------------------------------------------------------------------------------|
| Seven-day physical media deletion            | Implemented            | Hourly `BlobGarbageCollector`; five failures require manual intervention; database row persists. |
| Unpublished publication deletion             | Implemented on request | Transaction removes jobs, asset links, delivery attempts, and publication.                       |
| Refresh-session expiry/revocation            | Implemented            | Default seven-day expiry, rotation/revocation state, cookie clearing on logout.                  |
| Account erasure after deletion               | Missing                | No account deletion workflow or 30-day erasure job located.                                      |
| Waitlist withdrawal/anonymisation            | Missing                | No withdrawal endpoint or 30-day anonymisation job located.                                      |
| Workspace/member erasure                     | Missing                | No general deletion/anonymisation workflow located.                                              |
| Social credential purge                      | Missing                | Revocation states exist; automatic record/payload erasure was not located.                       |
| API-key purge                                | Missing                | Revocation exists; 90-day purge was not located.                                                 |
| Publishing job/delivery-log purge            | Missing                | Relationship deletion exists; no seven/90/180-day time-based job located.                        |
| Audit retention and legal hold               | Missing                | No one/five-year archive/delete control or legal-hold implementation located.                    |
| Analytics, application logs, errors, metrics | Missing                | Destinations and 30-day/90-day/13-month controls are not selected or implemented.                |
| Local-storage expiry                         | Missing                | Most keys persist until overwrite or browser clearing.                                           |

## Troubleshooting

- **A provider is configured in production outside the repository:** add the executed agreement,
  region, role, subprocessor and transfer evidence to the controlled evidence store, then change
  its inventory state to `selected`.
- **An adapter exists:** keep it `configurable` or `conditional`. Adapter code alone is not proof of
  production processing.
- **A duration appears in a policy or older ROPA:** do not copy it. Locate the deletion job and
  test;
  otherwise mark the control missing and use a counsel-approved purpose criterion.
- **YAML and Markdown differ:** update and validate the YAML first, then update this summary, ROPA,
  processor matrix, and affected public-policy drafts in the same change.
- **The legal person is decided:** replace the unresolved entity only after business and counsel
  evidence are recorded; a brand name is insufficient.

## References

- [`data-inventory.yaml`](data-inventory.yaml)
- [`ropa.md`](ropa.md)
- [`controller-processor-matrix.md`](controller-processor-matrix.md)
- [`legal-publication-gate.md`](legal-publication-gate.md)
- [`global-legal-readiness.md`](global-legal-readiness.md)
- [European Commission — records and controller obligations](https://commission.europa.eu/law/law-topic/data-protection/information-business-and-organisations/obligations_en)
- [Ahrefs — Web Analytics privacy information](https://help.ahrefs.com/en/articles/10247870-about-ahrefs-web-analytics)
