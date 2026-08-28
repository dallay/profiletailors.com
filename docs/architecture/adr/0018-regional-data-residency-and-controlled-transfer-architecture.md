# ADR-0018: Regional Data Residency and Controlled Transfer Architecture

- Status: Accepted
- Date: 2026-08-26
- Decision owners: Principal Architect
- Scope: Infrastructure, Database, Storage, Observability, Workspace/Tenancy, External Integrations
- Supersedes: None
- Superseded by: None
- Related:
  - [ADR-0001: Use a Modular Monolith Backend](0001-use-a-modular-monolith-backend.md)
  - [ADR-0008: Application-Level Multi-tenancy](0008-application-level-multi-tenancy.md)
  - [docs/compliance/market-entry-asia.md](../../compliance/market-entry-asia.md)
  - [docs/compliance/data-inventory.yaml](../../compliance/data-inventory.yaml)

## Context

As Profile Tailors expands its enterprise and international customer base, clients and jurisdictional legal regimes (e.g., GDPR in EU, APPI in Japan, PDPA in Singapore, PIPA in South Korea, LGPD in Brazil) impose strict constraints regarding data residency, sovereignty, and cross-border transfer authorization.

While Profile Tailors utilizes application-level multi-tenancy (`workspace_id` filtering as per ADR-0008) within a modular monolith architecture (ADR-0001), customer primary storage, backups, telemetry, and background job processing must guarantee that personal data (PII) and tenant content remain strictly within agreed geographical boundaries. Additionally, external third-party integrations (such as social media platform APIs, transactional messaging providers, and LLMs) introduce unavoidable outbound data flows that must be strictly audited, minimized, and governed.

This ADR defines the architectural pattern, component-level residency rules, tenant binding mechanisms, disaster recovery policies, encryption isolation boundaries, cross-border transfer controls, cost/operational trade-off quantifications, and regulatory feasibility assessments for constrained markets (e.g., China and Vietnam).

## Decision Drivers

- **Regulatory Compliance & Data Sovereignty**: Compliance with GDPR, LGPD, APPI, PDPA, and sectoral data residency laws requiring data persistence, processing, and backup isolation within approved geographic borders.
- **Tenant Isolation & Anti-Drift Guarantee**: Guaranteeing that a tenant bound to a specific region cannot accidentally leak, migrate, or process data across regional boundaries due to routing, configuration, or background processing drift.
- **Security & Key Separation**: Physical and logical cryptographic separation of tenant data encryption keys (KMS) across regions to prevent cross-jurisdictional key exposure.
- **Operational Simplicity & Cost Efficiency**: Avoiding premature complexity (such as active-active multi-region databases) while establishing a scalable regional silo footprint.
- **Unavoidable Outbound Data Flow Governance**: Explicit identification, minimization, and contractual gating for all external API egress (social media networks, LLMs, email/SMS gateways).
- **Disaster Recovery Integrity**: Ensuring backup retention and failover mechanisms strictly adhere to regional residency boundaries without silent cross-border failover.

## Comparative Architecture Models Analysis

We evaluated three candidate models for global multi-region deployment:

| Criterion | Model A: Single-Region Baseline | Model B: Regional Silos with Immutable Tenant Binding (Chosen) | Model C: Globally Replicated Database / Distributed Mesh |
|---|---|---|---|
| **Data Isolation & Sovereignty** | Low. All tenant data hosted in primary region (EU). Fails strict local storage mandates. | **High**. Each region is an independent execution & storage unit. Tenant data never leaves designated region. | Medium-Low. Cross-region replication introduces risk of PII replication across jurisdictions. |
| **Tenant Routing & Drift Risk** | Trivial. Single API ingress target. Zero drift risk. | **Zero-Drift Enforced**. Strict Gateway/Token tenant-to-region binding; misrouted traffic rejected with `421`. | High. Complex dynamic routing & distributed transaction lock risks. |
| **Database Complexity & Ops** | Low. Single PostgreSQL cluster (R2DBC). | **Medium**. Identical infrastructure stacks per region; zero cross-region DB clustering needed. | Extreme. Spanner/CockroachDB/Postgres multi-region replication, conflict resolution, latency penalties. |
| **Object Storage & Backups** | Simple single-region bucket setup. | **Region-locked S3 buckets** & regional KMS keys. CRR strictly disabled. | Complex multi-region S3 replication rules and cross-border lifecycle risks. |
| **Cross-Border Outbound Governance** | Managed per provider integration. | **Strictly governed**. Egress proxies & local outbox logging per region. | High risk of hidden egress from distributed workers. |
| **Disaster Recovery (DR)** | Single-region failover across local AZs. | **Regional Failover within local jurisdiction**. Prohibits out-of-jurisdiction failover. | Automated multi-region failover, risking residency breach during outage. |
| **Cost Scaling (Infrastructure)** | Baseline ($1x). | **Linear per region ($1.3x to $2.5x total depending on region count)**. | High exponential baseline ($3x-5x) due to distributed consensus & traffic costs. |

### Selected Architecture: Model B — Regional Silos with Immutable Tenant-to-Region Binding

Profile Tailors adopts **Model B: Regional Silo Architecture** anchored by an **EU Primary Baseline Region (`eu-central-1` Frankfurt)**.

Under this model:
1. Each region runs an independent, complete instance of the Profile Tailors infrastructure stack (API gateway, modular monolith application servers, PostgreSQL database, object storage, background workers, and telemetry collectors).
2. Workspaces (tenants) are immutably bound to a single region upon creation (`region_id`).
3. There is **zero direct cross-region database replication** or shared data stores. Cross-region state sharing is strictly prohibited for tenant data.

## Detailed Architectural Design

### 1. EU Baseline Region & Deployment Footprint

- **Primary Region**: AWS `eu-central-1` (Frankfurt, Germany) / EU Data Center Baseline.
- **Scope**: All EU, UK, and default global tenants reside in `eu-central-1` unless an enterprise tenant explicitly provisions in a designated secondary region (e.g., `us-east-1` N. Virginia, `ap-northeast-1` Tokyo).

### 2. Tenant-to-Region Binding & Anti-Drift Enforcements

To guarantee zero accidental tenant data drift across regions:

- **Immutable Region Identifier**: Every workspace record contains a `region_id` (e.g., `eu-central-1`, `us-east-1`) that is immutable for the lifetime of a session and normal request routing. This attribute is set during workspace creation and embedded in the workspace JWT claims (`rg` claim) and workspace metadata. Region migration is an explicit, exceptional administrative operation authorized only through the Cold Migration Pipeline (see below), which reissues the `region_id` binding and rotates all tokens after the target region import completes and source-region purge is verified.
- **Ingress Gateway Validation**:
  - The regional Ingress Gateway / API Router inspects incoming request JWT tokens and the `X-Workspace-Id` header.
  - If a request targeting Region A carries a tenant token bound to Region B, the gateway **MUST** immediately reject the request with `HTTP 421 Misdirected Request` or `HTTP 403 Forbidden` and log a security audit alert.
  - Gateway dynamic proxy-redirecting of requests containing tenant PII across regions is **PROHIBITED** to prevent unauthorized cross-border transit.
- **Application & Database Level Enforcements**:
  - The backend `WorkspaceContextWebFilter` verifies that `ResourceContext.regionId` matches the local deployment environment (`SPRING_CLOUD_REGION` / `APP_REGION`).
  - Database queries automatically append `workspace_id = :workspaceId` (as per ADR-0008). Database instances in Region A do not contain tables or records for tenants bound to Region B.

### 3. Component Data Residency Architecture

#### PostgreSQL Database
- **Regional Deployment**: Independent PostgreSQL instances per region accessed via R2DBC.
- **Schema Management**: Liquibase migrations run independently per regional cluster during deployment pipelines.
- **Data Isolation**: Database connection strings are region-bound. No database federation, cross-region links, or read-replicas across geographical boundaries are permitted.

#### Object Storage (S3 / CAS Media Library)
- **Region-Locked Buckets**: Object storage buckets (e.g., `profiletailors-media-eu-central-1`) are provisioned exclusively within the local region.
- **Replication Restrictions**: Cross-Region Replication (CRR) for tenant media and uploaded assets **MUST BE DISABLED**.
- **Content-Addressable Storage (CAS)**: Content deduplication (ADR-media-library-storage) operates strictly *per region*. Hash lookups are scoped to the regional bucket catalog; identical files uploaded in different regions are stored independently in each region's bucket to maintain strict sovereignty.

#### Backups & Disaster Recovery (DR)
- **Snapshot Storage**: Automated PostgreSQL volume snapshots and S3 backup buckets MUST be stored within the primary region or a designated secondary Availability Zone / region *within the same legal jurisdiction* (e.g., `eu-west-1` Ireland for `eu-central-1` Frankfurt under EU GDPR).
- **Prohibited Failover**: Failover of data-sovereign tenants to out-of-jurisdiction regions (e.g., failing over an EU tenant DB to a US region) is **STRICTLY PROHIBITED**. In the event of an unrecoverable single-region outage, RTO/RPO objectives must be satisfied using intra-jurisdictional backups.

#### Observability, Telemetry & Audit Logs
- **Local Telemetry Collectors**: Application logs, OpenTelemetry traces, and Prometheus metrics are ingested by regional collector nodes (e.g., OpenTelemetry Collector, Grafana Loki, Tempo).
- **PII Scrubbing & Anonymization Boundary**: Raw logs and trace spans containing PII (e.g., IP addresses, user emails, workspace names) **MUST NOT** cross regional borders.
- **Centralized Operational Dashboards**: Global operations teams may only access aggregated, de-identified metrics (e.g., HTTP request rates, JVM error counts, CPU utilization). Any centralized telemetry aggregator MUST strip all tenant identifiers and PII before egress.

### 4. Cross-Region Support, Admin Access & Encryption Separation

#### Key Management Service (KMS) Separation
- **Regional Customer Managed Keys (CMK)**: Each region utilizes dedicated, non-exportable KMS keys (e.g., AWS KMS regional keys) for envelope encryption of data at rest (PostgreSQL storage, S3 bucket encryption, secret storage).
- **Key Isolation**: IAM policies enforce that application instances in Region A have no permission to call `kms:Decrypt` using KMS keys from Region B.

#### Admin & Support Access Protocol
- **Zero-Trust Just-In-Time (JIT) Access**: Global support engineers and system administrators do not hold standing access to regional tenant databases or object stores.
- **Break-Glass Proxy**: Administrative access to a regional instance requires explicit Break-Glass authorization, Multi-Factor Authentication (MFA), and a logged ticket identifier.
- **Consent & Audit Ledger**: Where mandated by DPA/contract, customer consent is verified before support sessions. All administrative database queries or file accesses trigger immutable outbox audit events (`AdminAccessLogged`) persisted in the local regional audit table.

### 5. Unavoidable Cross-Border Transfers & External Provider Integrations

Profile Tailors integrates with external third-party services to deliver core capabilities (publishing to social media networks, sending transactional communications, and processing generative AI tasks). These integrations represent **unavoidable outbound data flows**.

#### Data Flow Mapping Matrix

| External Integration | Purpose | Data Items Transferred | Egress Boundary & Destination | Transfer Mechanism & Safeguards |
|---|---|---|---|---|
| **LinkedIn API** | Social Publishing & Analytics | Post text, media URLs/assets, OAuth tokens, author profile IDs | US (Microsoft / LinkedIn servers) | User-directed outbound API call; OAuth consent; HTTPS; Minimization of payload |
| **X (Twitter) API** | Social Publishing & Analytics | Post text, image/video binaries, handle IDs | US (X Corp servers) | User-directed outbound API call; explicit customer publishing action |
| **Meta Graph API (Facebook / Instagram)** | Social Publishing & Analytics | Media assets, captions, account IDs, page tokens | US (Meta Platforms servers) | User-directed outbound API call; Meta standard contractual clauses (SCCs) |
| **Threads API** | Social Publishing | Post text, images, container IDs | US (Meta Platforms servers) | User-directed outbound API call; OAuth authorization |
| **Transactional Email** | System Notifications, Account Verifications | Recipient email address, name, transactional message body | TBD — no approved production subprocessor selected | Feature disabled/not activated in production until a provider is approved and a valid transfer mechanism (DPA/SCC) is executed |
| **Transactional SMS** | Multi-Factor Authentication | Phone number, verification code | TBD — no approved production subprocessor selected | Feature disabled/not activated in production until a provider is approved and a valid transfer mechanism (DPA/SCC) is executed |
| **LLM / AI Providers** | Content Generation & Assistance | Prompt text, post draft snippets | TBD — no approved production subprocessor selected | Feature disabled/not activated in production until a provider is approved and a valid transfer mechanism (DPA/SCC) is executed |

#### Transfer Safeguards & Egress Governance
1. **User Direction**: Social media post publishing occurs exclusively under explicit user direction when scheduling or publishing content to third-party networks.
2. **Payload Minimization**: Egress adapters MUST strip non-essential internal metadata (such as internal database IDs, user global IP addresses, and workspace internal flags) before sending payloads to external APIs.
3. **Egress Proxying**: All outbound third-party API traffic MUST pass through regional egress proxies configured with domain allowlists, audit logging, and TLS 1.3 inspection.
4. **Contractual Protections**: Transfers to third-party processors are governed by standard Data Processing Addendums (DPAs), EU Standard Contractual Clauses (SCCs), or UK International Data Transfer Addendums (IDTA).

### 6. Migration and Disaster Recovery (DR) Implications

#### Tenant Regional Migration Strategy
When an enterprise tenant requests a region migration (e.g., migrating workspace from `us-east-1` to `eu-central-1`):
1. **Cold Migration Pipeline**: Hot/live cross-region database replication is **PROHIBITED**. Migration must be executed as a scheduled maintenance window ("Cold Migration").
2. **Execution Steps**:
   - Workspace placed in read-only maintenance mode.
   - Export tenant database slice (using `workspace_id` filter) and object storage CAS assets to an encrypted export archive.
   - Cryptographically re-encrypt the archive using the target region's KMS key.
   - Import archive into target region database and S3 bucket.
   - Purge tenant source data from origin region following the Retention & Erasure Control Plan.
   - Update workspace global routing map `region_id` to target region and reissue fresh tokens with the new region binding (old tokens carrying the prior region are invalidated).

#### Disaster Recovery (DR) Protocols
- **RPO / RTO Targets**: RPO ≤ 1 hour, RTO ≤ 4 hours for regional restore.
- **Intra-Jurisdictional DR**: Automated backups and failover targets MUST remain within the same legal jurisdiction.
- **Regional Isolation during Disaster**: An outage in Region A must not degrade service or leak traffic into Region B.

### 7. Feasibility Assessment for High-Risk Jurisdictions (China & Vietnam)

Due to severe regulatory requirements, local hosting mandates, and state access laws, **China and Vietnam cannot be served by generic global or regional SaaS infrastructure**.

#### China (PIPL, Data Security Law, Cybersecurity Law)
- **Legal Mandates**: PIPL and DSL mandate strict local data storage within mainland China for critical data/personal information above statutory thresholds (e.g., processing data of over 1 million individuals or exporting sensitive/important data). Outbound data export requires Cyberspace Administration of China (CAC) security assessments, CAC Standard Contracts, or formal certification.
- **Infrastructure Mandates**: When applicable thresholds are met or when actively targeting the Chinese market, local hosting inside mainland China (e.g., AWS China in Beijing/Ningxia), a licensed local entity / Joint Venture, and an Internet Content Provider (ICP) license / app filing are required.
- **Architectural Conclusion**: **UNSUPPORTED** without a dedicated architecture. Profile Tailors standard SaaS instances MUST NOT accept registrations or route traffic for mainland China data residency compliance without a dedicated, isolated China air-gapped deployment architecture.

#### Vietnam (Decree 13/2023/ND-CP & Cybersecurity Decree 53)
- **Legal Mandates**: Decree 13 Article 43 requires a detailed Outbound Transfer Impact Assessment Dossier submitted to MPS A05 within 60 days of processing for transfers of Vietnamese citizens' data abroad. Decree 53's local storage and local representative obligations apply to foreign enterprises providing telecommunications, internet, or digital services in Vietnam when they receive a security warning or order from the Ministry of Public Security (not unconditionally to all foreign SaaS providers).
- **Architectural Conclusion**: **UNSUPPORTED** pending dedicated architecture/legal package. Profile Tailors standard global infrastructure MUST NOT process localized Vietnamese tenant data without an approved, dedicated local cloud footprint and legal entity structure.

#### Operational Mandate
> **China and Vietnam are explicitly marked as UNSUPPORTED in standard Profile Tailors regional deployments.** Enabling support for China or Vietnam requires a dedicated, individually approved architectural and legal design package.

### 8. Quantified Cost & Operational Trade-offs

| Cost & Operational Vector | Single-Region Model | Regional Silo Model (Selected) | Globally Replicated Model |
|---|---|---|---|
| **Base Compute & DB Fixed Cost** | $1,200 / month | $1,200 / month (1st region) + $900 / month per additional region | $4,500+ / month (minimum cluster nodes across 3 regions) |
| **Network Egress & Cross-Region Data Transfer** | $50 / month | $100 / month (isolated per region egress) | $1,500+ / month (continuous cross-region DB sync & consensus) |
| **KMS & Storage Costs** | $30 / month | $30 / month per region | $150 / month |
| **Operational Overhead (DevOps)** | 1x Baseline | 1.3x Baseline (Infrastructure-as-Code modular replication via Terraform/OpenTofu) | 3.5x Baseline (Complex multi-region cluster management & network partitions) |
| **Compliance & Audit Overhead** | High risk of non-compliance for non-EU enterprise customers | **Low risk**. Clear, verifiable regional boundaries per customer | Very high audit burden (proving cross-region sync doesn't breach residency) |

## Consequences

### Positive

- **Uncompromising Compliance**: Designed to satisfy GDPR, LGPD, APPI, PDPA, and enterprise customer data residency mandates.
- **Zero Accidental Tenant Drift**: Ingress token validation (`421 Misdirected Request`) and database isolation prevent misrouting and accidental cross-border data leakage.
- **Cryptographic Isolation**: Per-region KMS keys ensure tenant data cannot be decrypted outside its designated region.
- **Clean Disaster Recovery Boundaries**: Architecturally enforces backups remaining strictly within local legal jurisdictions, preventing accidental compliance breaches during disaster recovery.
- **Architectural Clarity**: Clear, explicit identification of all third-party outbound data flows (LinkedIn, X, Meta, Threads, LLMs, email/SMS gateways).

### Negative

- **Increased Operational Footprint**: Managing multiple independent regional infrastructure stacks via Infrastructure-as-Code.
- **Higher Infrastructure Fixed Cost**: Running base compute and database instances per active deployment region.
- **Cold Tenant Migration Overhead**: Migrating a tenant across regions requires scheduled downtime and manual/automated export-import pipelines rather than live database replication.

### Risks

- **Misconfigured Gateway Routing**: If an ingress proxy is misconfigured, misrouted requests could reach the wrong regional backend. *Mitigated by backend application-level region validation (`ResourceContext.regionId` check).*
- **Third-Party API Egress Policy Drift**: External social APIs changing data centers or endpoints. *Mitigated by regional egress proxies and contractual DPA/SCC oversight.*

### Accepted Trade-offs

- **No Global Deduplication of Assets**: Media files uploaded across different regions are deduplicated *within* each regional S3 bucket only. Cross-region deduplication is sacrificed to guarantee data sovereignty.
- **Cold Migration Downtime**: Tenant regional migrations incur scheduled maintenance downtime to ensure safe, verifiable data transfer and re-encryption.

## Compliance and Enforcement

- **Ingress Routing Enforcer**: Gateway rules and backend `WorkspaceContextWebFilter` verify `region_id` on every request.
- **ArchTest & Code Reviews**: ArchTest rules verify that cross-region storage adapters or database replication modules are not introduced into the codebase.
- **Infrastructure-as-Code (IaC) Validation**: Terraform / OpenTofu linting enforces `cross_region_replication = false` on all tenant S3 buckets.

## Verification

- [ ] `read_file` verification of created ADR file and structure.
- [ ] Integration test verification for `WorkspaceContextWebFilter` handling misdirected region headers.
- [ ] Documentation index update verification in `docs/architecture/adr/README.md`.

## Revisit Conditions

- Introduction of mandatory real-time global collaboration capabilities across workspaces in different regions (requiring cross-region sync protocols).
- Formal regulatory changes in target markets requiring physical on-premise local data hosting.
- Formal approval of dedicated infrastructure design packages for China or Vietnam market entry.
