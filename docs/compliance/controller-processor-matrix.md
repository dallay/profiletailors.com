# Controller–Processor Matrix

> **Classification:** Internal — Legal and Compliance
> **Status:** Draft — no production subprocessor list is approved
> **Version:** 2.0
> **Last verified:** 2026-07-17

## Overview

This matrix separates Profile Tailors' role by processing purpose from the status of external
providers. It MUST NOT be used as a public subprocessor list until the contracting legal person,
production providers, regions, agreements, and transfer mechanisms are evidenced.

The previous matrix incorrectly treated vendor examples and available adapters as current
providers. Version 2.0 uses these states:

| State             | Meaning                                                                                            |
|-------------------|----------------------------------------------------------------------------------------------------|
| **Selected**      | Production use and executed contractual evidence are recorded.                                     |
| **Configurable**  | A real adapter exists, but production activation and contract are not proved.                      |
| **Conditional**   | Integration loads only when a feature flag or credential is supplied.                              |
| **Not selected**  | The function is needed, but the production provider is unknown.                                    |
| **Not evidenced** | A previous document named the provider, but current code/configuration does not support the claim. |

No external provider currently qualifies as **Selected** based on repository evidence alone.

## Changes

| Version | Date       | Description                                                                          |
|---------|------------|--------------------------------------------------------------------------------------|
| 1.0     | 2026-07-17 | Initial matrix containing provider alternatives and unsupported DPA claims           |
| 2.0     | 2026-07-17 | Reconciled with code and configuration; added provider-selection and agreement gates |

## Usage

### Profile Tailors role by purpose

These roles are working classifications pending the legal entity, customer model, DPA, and
enabled-market review. A customer-content feature can place Profile Tailors in a processor role
for the content while Profile Tailors remains controller for security, billing, and its own
service operations.

| Processing purpose                                                                   | Working role                           | Status and rationale                                                                                             |
|--------------------------------------------------------------------------------------|----------------------------------------|------------------------------------------------------------------------------------------------------------------|
| Account registration, local authentication, email verification, and refresh sessions | Controller                             | Profile Tailors determines the account and security means; legal basis pending approval.                         |
| Customer-authored social content and scheduled publishing                            | Processor                              | Customer determines content and destination; a customer DPA and instructions are required.                       |
| Hosting and delivery metadata                                                        | Controller                             | Profile Tailors selects infrastructure and operational purposes; provider unknown.                               |
| Early-access waitlist                                                                | Controller                             | Profile Tailors determines the early-access purpose.                                                             |
| Optional waitlist marketing                                                          | Controller                             | Separate purpose and optional consent; withdrawal workflow missing.                                              |
| Workspace, membership, ownership, and access management                              | Controller                             | Profile Tailors defines the collaboration and security model.                                                    |
| LinkedIn account connection and publishing credentials                               | Processor for customer connection data | Customer chooses the account and instructs connection; service-security processing may be controller processing. |
| API key and service credential administration                                        | Controller                             | Profile Tailors determines service authentication and security controls.                                         |
| Customer media storage                                                               | Processor                              | Customer determines uploaded content and publishing purpose.                                                     |
| Unsplash search/import telemetry                                                     | Controller, subject to feature review  | Profile Tailors chooses the integration; disabled by default.                                                    |
| Publication job and delivery operations                                              | Processor                              | Performed to execute customer publishing instructions.                                                           |
| Security and authorization audit events                                              | Controller                             | Profile Tailors determines security/accountability purposes; lawful-basis assessment pending.                    |
| Application metrics and logs                                                         | Controller                             | Profile Tailors determines operational and security monitoring.                                                  |
| Marketing-site analytics                                                             | Controller                             | Optional Ahrefs integration; enabled-market consent/basis assessment pending.                                    |
| Authentication and UI device storage                                                 | Controller                             | Profile Tailors determines cookie and local-storage behaviour; necessity must be assessed item by item.          |

### Production provider register

| Function                  | Provider or technology                                  | State                             | Data or access                                                                    | Region                         | Agreement                                           | Publication rule                                                                          |
|---------------------------|---------------------------------------------------------|-----------------------------------|-----------------------------------------------------------------------------------|--------------------------------|-----------------------------------------------------|-------------------------------------------------------------------------------------------|
| PostgreSQL database       | Provider not selected                                   | **Not selected**                  | All database-backed activity data                                                 | Unknown                        | DPA and transfer terms not evidenced                | Do not name a database vendor or EEA location.                                            |
| Hosting/CDN/reverse proxy | Provider not selected                                   | **Not selected**                  | Network and request metadata; deployed artifacts                                  | Unknown                        | DPA and transfer terms not evidenced                | Do not name Vercel, Cloudflare, or Dokploy as current host.                               |
| Media object storage      | Local filesystem default; S3 and Cloudflare R2 adapters | **Not selected**                  | Media content, filenames, hashes, storage metadata                                | Unknown                        | DPA and transfer terms not evidenced                | Do not publish slash-separated storage alternatives.                                      |
| Transactional email       | Resend adapter                                          | **Conditional**                   | Email address, verification link, delivery metadata                               | Unknown                        | Contract, DPA, role, and region not evidenced       | Name only after activation and agreement verification.                                    |
| User identity             | Local JWT and configurable federated JWT validation     | **No external provider selected** | Account and authentication data                                                   | Application/database locations | Not applicable until an external issuer is selected | Do not name Auth0 or Clerk.                                                               |
| Social publishing         | LinkedIn                                                | **Configurable**                  | Account/profile identifiers, OAuth credentials, content, media, delivery metadata | Unknown                        | Platform/API terms approval not evidenced           | Name as a supported integration only when enabled; do not infer all processing locations. |
| External media search     | Unsplash                                                | **Conditional**                   | Search query, server request metadata, imported asset metadata                    | Unknown                        | API terms approval not evidenced                    | Name only if feature enabled.                                                             |
| Marketing analytics       | Ahrefs Web Analytics                                    | **Conditional**                   | Page, referrer, device/network and analytics request data                         | Unknown                        | Contract/DPA/role not evidenced                     | Verify activation and observed behaviour before notice.                                   |
| Metrics and logs          | Prometheus-format endpoint; provider not selected       | **Not selected**                  | Metrics, tags, identifiers and application logs                                   | Unknown                        | No managed-provider agreement evidenced             | Do not name Grafana or another managed provider.                                          |
| Error tracking            | None located                                            | **Not evidenced**                 | Not applicable until selected                                                     | Not applicable                 | None                                                | Remove Sentry from current-provider claims.                                               |
| Queue/cache               | No external managed provider located                    | **Not evidenced**                 | Application may use internal/runtime mechanisms; external flow unproved           | Unknown                        | None                                                | Remove Upstash, ElastiCache, CloudAMQP, and Confluent from current-provider claims.       |

### Claims invalidated by repository evidence

| Previous claim                                           | Revalidation result                                                                                                            |
|----------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| Vercel is the current host and its DPA/SCCs are in place | No Vercel deployment adapter or production configuration is present; release workflow says deployment is future work.          |
| Auth0/Clerk is the identity provider                     | Local JWT, refresh sessions, and configurable federated JWT validation are implemented; no Auth0/Clerk selection is evidenced. |
| Cloudflare R2/AWS S3 is the current media store          | Local filesystem is the default; R2 and S3 are available adapters, not a production selection.                                 |
| Sentry is a planned/current processor                    | No Sentry integration was located. Planned vendors do not belong in a current processor register.                              |
| Managed Grafana/Prometheus operates in the EEA           | Prometheus-format metrics exist; no managed recipient, region, or contract is evidenced.                                       |
| Resend/SendGrid is the email provider                    | Resend is conditional; SendGrid was not located. Neither is proved as selected production processing.                          |
| All social platforms receive content                     | Only LinkedIn has real backend connection and publishing adapters. Frontend types and mock data do not prove processing.       |

### Provider approval checklist

Before changing a row to **Selected**, record:

1. Exact provider legal entity, service, account owner, and production environment.
2. Data categories, purposes, controller/processor role, and all subprocessors.
3. Storage and access locations, support access, backup locations, and deletion behaviour.
4. Executed MSA/terms, DPA, security review, breach SLA, audit rights, and termination
   export/deletion.
5. Source-jurisdiction transfer mechanism and transfer assessment where required.
6. Configuration and runtime evidence proving the provider is actually used.
7. Public-notice wording, approval owner, counsel reference, and next review date.

### Immediate action register

| Priority | Action                                                                                  | Exit evidence                                                               |
|----------|-----------------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| P0       | Select the production legal entity, host/CDN, database, and object-storage architecture | Executed entity and provider records with regions and agreements            |
| P0       | Decide whether Resend and Ahrefs are enabled at launch                                  | Production configuration, contract review, data-flow and notice decision    |
| P0       | Remove unverified providers from public drafts                                          | EN/ES policies match selected provider register                             |
| P0       | Execute customer DPA and LinkedIn platform review before real customer publishing       | Approved DPA, platform terms assessment, transfer map                       |
| P1       | Select logs/metrics destination and retention                                           | Provider/security record plus implemented deletion settings                 |
| P1       | Establish quarterly provider and subprocessor review                                    | Named owner, evidence repository, review schedule and change-notice process |

## Troubleshooting

- **A vendor appears in an ADR or architecture diagram:** treat it as an option unless production
  configuration and contractual evidence prove selection.
- **An environment variable exists:** treat the integration as configurable or conditional, not
  active.
- **The provider publishes a DPA:** availability of a public DPA is not evidence that Profile
  Tailors accepted it, selected a region, completed a transfer assessment, or configured deletion.
- **A platform is present in frontend mocks:** do not add it to the processor or recipient register
  until a real backend flow or verified production integration exists.
- **The provider is an independent controller:** document the actual relationship and disclosures;
  the label does not remove Profile Tailors' responsibility for the disclosure or transfer.

## References

- [`data-inventory.yaml`](data-inventory.yaml)
- [`data-inventory.md`](data-inventory.md)
- [`ropa.md`](ropa.md)
- [`legal-publication-gate.md`](legal-publication-gate.md)
- [European Commission — controller and processor obligations](https://commission.europa.eu/law/law-topic/data-protection/rules-business-and-organisations/obligations/controller-processor/what-data-controller-or-data-processor_en)
