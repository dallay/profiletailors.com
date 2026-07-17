# Controller–Processor Matrix

> **Classification:** Internal — Compliance
> **Status:** Active
> **Version:** 1.0

## Overview

This document maps the data-processing roles of Profile Tailors, its customers, and its third-party providers for every personal-data processing activity.

## Changes

| Version | Date | Description |
|---------|------|-------------|
| 1.0 | 2026-07-17 | Initial controller–processor matrix |

## Usage

This matrix serves as:

- A **role map** identifying when Profile Tailors acts as controller, processor, or both per activity.
- A **third-party register** documenting processor and independent-controller relationships.
- A **DPA tracker** identifying which provider agreements are in place, required, or planned.
- An **input for Privacy Policy and Terms** drafting (see DALLAY-488).

| Role | Definition |
|------|------------|
| **Controller** | Determines the purposes and means of processing (GDPR Art. 4(7)) |
| **Processor** | Processes data on behalf of the controller (GDPR Art. 4(8)) |
| **Independent controller** | Determines its own purposes and means, separately accountable |
| **Joint controller** | Jointly determines purposes and means with another party (Art. 26) |

---

## Profile Tailors Role by Activity

| Activity | Role | Rationale |
|----------|------|-----------|
| Account registration & management | **Controller** | We decide what data to collect and why |
| Social media publishing | **Processor** | We act on customer instructions for publication |
| Web hosting & delivery | **Controller** | We choose infrastructure and security measures |
| Lead capture & waitlist | **Controller** | We determine the purpose (first-party marketing) |
| Workspace & membership management | **Controller** | We provide the collaboration feature set |
| OAuth authentication & social connections (pa-006) | **Controller** (auth) / **Processor** (connections) | We control auth requirements; customer instructs which accounts to connect |
| API key management | **Controller** | We issue and manage access credentials |
| Media asset storage | **Processor** | Content belongs to the customer |
| Content publishing & delivery | **Processor** | Customer provides the content to publish |
| Audit & governance logging | **Controller** | Legal obligation — we determine retention and scope |
| Analytics & observability | **Controller** | We determine what to monitor for service improvement |

---

## Third-Party Processor Matrix

| Provider | Service | Role | Data Accessed | Location | DPA Status | Safeguard Mechanism |
|----------|---------|------|---------------|----------|------------|---------------------|
| **Vercel Inc.** | Hosting, CDN, serverless functions | Processor | IP addresses, request metadata, source code | US (global CDN) | ✅ In place | SCCs (Standard Contractual Clauses) |
| **Neon / AWS RDS / GCP Cloud SQL** | PostgreSQL database hosting | Processor | Personal data for activities that name database hosting as a processor arrangement (pa-001, pa-010); infrastructure-level metadata across all database-backed activities. See data-inventory.yaml for per-activity classification. | EEA or US | ⚠️ Required | SCCs; encryption at rest |
| **Cloudflare R2 / AWS S3** | Object storage for media assets | Processor | Media files, filenames, metadata | Bucket region (EEA/US) | ⚠️ Required | SCCs; server-side encryption |
| **Upstash / AWS ElastiCache** | Redis cache | Processor | Session data (transient), rate-limit counters | Region | ⚠️ Required | No persistent personal data; TTL-based expiry |
| **CloudAMQP / Confluent Cloud** | Message queue | Processor | Event payloads (may contain identifiers) | Region | ⚠️ Required | Encryption in transit; queue-level access control |
| **Resend / SendGrid** | Transactional email | Processor | Email address, email content | US | ⚠️ Required | SCCs; no marketing email processing |
| **Sentry (planned)** | Error tracking | Processor | Error stack traces, IP, request metadata | US | ⚠️ Planned | SCCs; data scrubbing rules |
| **Grafana / Prometheus (managed)** | Metrics & monitoring | Processor | Aggregated metrics, service logs | EEA | ⚠️ Required | Local hosting preferred |

---

## Independent Controller Matrix

| Provider | Service | Data Accessed | Location | Relationship Basis |
|----------|---------|---------------|----------|-------------------|
| **Auth0 / Clerk** | Identity provider (OAuth2/OIDC) | Authentication events, profile data | US | Terms of Service; independent controller for auth events |
| **LinkedIn** | Social media platform | Published content, engagement metrics | US | API Terms of Service; independent controller for published data |
| **Twitter/X** | Social media platform | Published content, engagement metrics | US | API Terms of Service |
| **Facebook** | Social media platform | Published content, engagement metrics | US | Platform Terms |
| **Instagram** | Social media platform | Published content, engagement metrics | US | Platform Terms |
| **TikTok** | Social media platform | Published content, engagement metrics | US | API Terms of Service |

---

## Action Items

| Priority | Item | Owner |
|----------|------|-------|
| 🔴 **P0** | Execute DPAs with database hosting provider | Compliance |
| 🔴 **P0** | Execute DPA with object storage provider | Compliance |
| 🟡 **P1** | Execute DPA with email service provider | Compliance |
| 🟡 **P1** | Execute DPA with message queue provider | Compliance |
| 🟡 **P1** | Execute DPA with observability providers | Compliance |
| 🔵 **P2** | Confirm Auth0/Clerk controller boundary | Legal |
| 🔵 **P2** | Review social platform ToS for controller obligations | Legal |

---

## Troubleshooting

- **Activity not listed:** If a data-processing activity is not represented in this matrix, update `data-inventory.yaml` first, then add the corresponding row.
- **Provider missing from matrix:** Add the provider to the appropriate table (Processor or Independent Controller) with its role, location, and DPA status.
- **DPA status out of date:** Update the `agreement_reference` or DPA Status column once an agreement is executed.

## References

- [`data-inventory.yaml`](data-inventory.yaml): Processing activity details
- [`ropa.md`](ropa.md): GDPR Art. 30 formal record
- [`data-inventory.md`](data-inventory.md): Human-readable processing activity list
- [Vercel DPA](https://vercel.com/legal/dpa)
- [Cloudflare DPA](https://www.cloudflare.com/cloudflare-customer-dpa/)
- [AWS DPA](https://aws.amazon.com/compliance/gdpr-center/)
