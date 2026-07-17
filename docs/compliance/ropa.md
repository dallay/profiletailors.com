# Record of Processing Activities (ROPA)

> **Classification:** Internal — Compliance
> **Status:** Active
> **Version:** 1.0
> **Authority:** GDPR Article 30
> **Processing entity:** Dallay (Profile Tailors)

## Controller Information

| Field | Value |
|-------|-------|
| **Name** | Dallay (Profile Tailors) |
| **DPO** | Not appointed |
| **Representative** | Not appointed (EEA establishment assumed) |
| **Contact** | TBD |

## Processing Activities

### 1. User Account Registration and Management

| Category | Detail |
|----------|--------|
| **Purpose** | Create and manage user accounts, authentication, and session management |
| **Controller** | Dallay (Profile Tailors) |
| **Processor(s)** | Database hosting provider |
| **Legal basis** | Contract (Art. 6(1)(b)) |
| **Data categories** | Email, username, password hash (bcrypt), principal identifier, display identity |
| **Data subjects** | Registered users |
| **Recipients** | Internal systems; database hosting provider (processor) |
| **International transfer** | EEA (internal); depends on database hosting region |
| **Retention schedule** | Delete 30 days after account deletion (statutory exceptions apply) |
| **Security measures** | bcrypt hashing, TLS 1.3, encryption at rest, RBAC, audit logging |

### 2. Social Media Publishing and Scheduling

| Category | Detail |
|----------|--------|
| **Purpose** | Schedule, compose, and publish content to connected social media accounts |
| **Controller** | Customer (workspace owner) — Profile Tailors acts as processor |
| **Processor(s)** | LinkedIn, Twitter/X, Facebook, Instagram, TikTok (independent controllers) |
| **Legal basis** | Controller instruction |
| **Data categories** | Social media account identifiers, published content, delivery records |
| **Data subjects** | End recipients; social media account owners |
| **Recipients** | LinkedIn (US), Twitter/X (US), Facebook (US), Instagram (US), TikTok (US) |
| **International transfer** | US (social media platforms) |
| **Retention schedule** | 90 days after account deletion; published content persists per platform policy |
| **Security measures** | OAuth 2.0, token encryption (AES-256-GCM), workspace isolation |

### 3. Web Application Hosting and Delivery

| Category | Detail |
|----------|--------|
| **Purpose** | Host and deliver the Profile Tailors web application, API, and marketing site |
| **Controller** | Dallay (Profile Tailors) |
| **Processor(s)** | Vercel Inc. (US) |
| **Legal basis** | Contract (Art. 6(1)(b)) |
| **Data categories** | IP address, browser user agent, usage analytics, request metadata |
| **Data subjects** | Website visitors, application users, API consumers |
| **Recipients** | Vercel Inc. (US — processor) |
| **International transfer** | US (SCCs in place via Vercel DPA) |
| **Retention schedule** | 90 days (access logs), 26 months (aggregated analytics) |
| **Security measures** | TLS 1.3, WAF, DDoS protection, CDN edge security |

### 4. Lead Capture and Waitlist Management

| Category | Detail |
|----------|--------|
| **Purpose** | Manage early-access waitlist signups and marketing communications |
| **Controller** | Dallay (Profile Tailors) |
| **Processor(s)** | None (internal processing only) |
| **Legal basis** | Consent (Art. 6(1)(a)) |
| **Data categories** | Email, marketing preferences, consent status & version, locale, metadata |
| **Data subjects** | Waitlist signups, prospective users |
| **Recipients** | Internal systems only (EEA) |
| **International transfer** | None (EEA only) |
| **Retention schedule** | 30 days after consent withdrawal (anonymize); converted users move to account retention |
| **Security measures** | Email verification, consent versioning, RBAC |

### 5. Workspace and Membership Management

| Category | Detail |
|----------|--------|
| **Purpose** | Manage team workspaces, memberships, roles, and ownership |
| **Controller** | Dallay (Profile Tailors) |
| **Processor(s)** | Database hosting provider |
| **Legal basis** | Contract (Art. 6(1)(b)) |
| **Data categories** | Membership records, ownership records, role assignments, workspace metadata |
| **Data subjects** | Workspace members and owners |
| **Recipients** | Internal systems (EEA) |
| **International transfer** | EEA (internal) |
| **Retention schedule** | 30 days after removal (audit trail exceptions) |
| **Security measures** | RBAC per workspace, membership audit trail, cross-workspace isolation |

### 6. OAuth Authentication and Social Account Connections

| Category | Detail |
|----------|--------|
| **Purpose** | Authenticate users and connect social media accounts |
| **Controller** | Dallay (auth) / Customer (social connections) |
| **Processor(s)** | Auth0/Clerk (independent controller for auth events) |
| **Legal basis** | Contract (Art. 6(1)(b)) |
| **Data categories** | OAuth subject identifier, provider connection ref, encrypted tokens |
| **Data subjects** | Registered users connecting social accounts |
| **Recipients** | Auth0/Clerk (US); social platforms (US) |
| **International transfer** | US (SCCs required) |
| **Retention schedule** | Encrypted tokens: deleted on revocation; metadata: 30 days |
| **Security measures** | AES-256-GCM token encryption, no plaintext exposure, server-side refresh |

### 7. API Key and Service Credential Management

| Category | Detail |
|----------|--------|
| **Purpose** | Issue and manage API keys for programmatic access |
| **Controller** | Dallay (Profile Tailors) |
| **Processor(s)** | Database hosting provider |
| **Legal basis** | Contract (Art. 6(1)(b)) |
| **Data categories** | API key lookup hash, secret verifier, principal association |
| **Data subjects** | API key holders (technical users) |
| **Recipients** | Internal systems (EEA) |
| **International transfer** | None (EEA only) |
| **Retention schedule** | 90 days after revocation (rotation audit trail) |
| **Security measures** | Cryptographic hashing, rotation support, irrevocable revocation |

### 8. Media Asset Storage and Management

| Category | Detail |
|----------|--------|
| **Purpose** | Upload, store, and serve media assets for social media content |
| **Controller** | Customer (workspace owner) — Profile Tailors acts as processor |
| **Processor(s)** | Cloudflare R2 / AWS S3 (object storage) |
| **Legal basis** | Controller instruction |
| **Data categories** | Original filenames, media content (may contain personal data), upload metadata |
| **Data subjects** | End users; workspace members |
| **Recipients** | Cloudflare R2 / AWS S3; social media platforms (upon publication) |
| **International transfer** | US or EEA (bucket region); US (social platforms) |
| **Retention schedule** | 7 days after deletion (GC grace), then permanent erasure |
| **Security measures** | Signed URLs (time-limited), workspace access control, server-side encryption, SHA-256 integrity |

### 9. Content Publishing and Delivery Operations

| Category | Detail |
|----------|--------|
| **Purpose** | Execute scheduled content publishing and manage delivery lifecycle |
| **Controller** | Customer (workspace owner) — Profile Tailors acts as processor |
| **Processor(s)** | Social media platforms (independent controllers) |
| **Legal basis** | Controller instruction |
| **Data categories** | Publication content, author identifier, job metadata, delivery error logs |
| **Data subjects** | Workspace members; social media audience |
| **Recipients** | LinkedIn, Twitter/X, Facebook, Instagram, TikTok |
| **International transfer** | US (social media platforms) |
| **Retention schedule** | 90 days (delivery logs), 7 days (jobs) |
| **Security measures** | Author attribution, delivery logging, workspace isolation |

### 10. Audit and Governance Logging

| Category | Detail |
|----------|--------|
| **Purpose** | Record security-relevant events for compliance and incident response |
| **Controller** | Dallay (Profile Tailors) |
| **Processor(s)** | Database hosting provider |
| **Legal basis** | Legal obligation (Art. 5(2), Art. 24) |
| **Data categories** | Actor principal ID, workspace ID, request details, role info, event details |
| **Data subjects** | All users generating audit events |
| **Recipients** | Internal systems (EEA) |
| **International transfer** | None (EEA only) |
| **Retention schedule** | 1 year (standard), 5 years (security incidents) |
| **Security measures** | Append-only store, tamper-evident (planned), RBAC, cross-workspace isolation |

### 11. Analytics, Observability, and Error Monitoring

| Category | Detail |
|----------|--------|
| **Purpose** | Monitor application performance, track errors, analyze usage |
| **Controller** | Dallay (Profile Tailors) |
| **Processor(s)** | Vercel Analytics, Sentry (planned), Grafana/Prometheus |
| **Legal basis** | Legitimate interest (Art. 6(1)(f)) |
| **Data categories** | IP address, request metadata, error stack traces (minimized) |
| **Data subjects** | Application users, API consumers |
| **Recipients** | Vercel Analytics (US), Sentry (US, planned), Grafana/Prometheus (EEA) |
| **International transfer** | US (SCCs required) |
| **Retention schedule** | 30 days (detailed logs), 13 months (aggregated metrics), 90 days (errors) |
| **Security measures** | Log scrubbing, data minimization by design, operations team access only |

---

## Review and Change Management

| Item | Detail |
|------|--------|
| **Review cadence** | Quarterly or when new processing activities are introduced |
| **Change trigger** | New feature, new third-party provider, new data category, regulatory change |
| **Owner** | Compliance team |
| **Approval** | CTO / DPO (when appointed) |

## References

- GDPR Art. 30(1)–(5): Legal requirements for records of processing activities
- [`data-inventory.yaml`](data-inventory.yaml): Machine-readable source of truth
- [`controller-processor-matrix.md`](controller-processor-matrix.md): Third-party processor map
- [`data-inventory.md`](data-inventory.md): Human-readable processing activity list
