# Controller–Processor Matrix

> **Classification:** Internal — Compliance
> **Status:** Active
> **Version:** 1.0

## Overview

This document maps the data-processing roles of Profile Tailors, its customers, and its third-party providers for every personal-data processing activity.

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
| OAuth authentication | **Controller** | We decide auth requirements and data storage |
| Social account connections | **Processor** | Customer instructs which accounts to connect |
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
| **Neon / AWS RDS / GCP Cloud SQL** | PostgreSQL database hosting | Processor | All personal data stored in database | EEA or US | ⚠️ Required | SCCs; encryption at rest |
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

## References

- [`data-inventory.yaml`](data-inventory.yaml): Processing activity details
- [`ropa.md`](ropa.md): GDPR Art. 30 formal record
- [`data-inventory.md`](data-inventory.md): Human-readable processing activity list
- [Vercel DPA](https://vercel.com/legal/dpa)
- [Cloudflare DPA](https://www.cloudflare.com/cloudflare-customer-dpa/)
- [AWS DPA](https://aws.amazon.com/compliance/gdpr-center/)
