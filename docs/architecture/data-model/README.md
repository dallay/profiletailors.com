# Data Model · ER Diagrams

Visual catalog of every PostgreSQL table in `server/smp` and what it stores. 12 self-contained HTML diagrams. Use them to navigate the schema, find tables by bounded context, and understand cross-context foreign keys before touching code.

> **Start here:** [`erd-overview.html`](./erd-overview.html) — the zone map. Read it first; it tells you which diagram to open next.

---

## Map of bounded contexts

| Bounded context | Tables | Anchor aggregate | Diagram |
|---|---|---|---|
| **identity** | 5 | `principals` | [`erd-identity.html`](./erd-identity.html) |
| **credentials** | 3 | `api_key_credentials` | [`erd-credentials.html`](./erd-credentials.html) |
| **tenancy** | 3 | `workspaces` | [`erd-tenancy.html`](./erd-tenancy.html) |
| **authorization** | 7 | `roles` (RBAC core + 3 workspace-scoped extensions) | [`erd-authorization.html`](./erd-authorization.html) |
| **publishing (core)** | 9 | `publications` | [`erd-publishing-core.html`](./erd-publishing-core.html) |
| **publishing (engagement)** | 7 | `social_content_posts` | [`erd-publishing-engagement.html`](./erd-publishing-engagement.html) |
| **governance (compliance)** | 7 | `compliance_controls` | [`erd-governance-compliance.html`](./erd-governance-compliance.html) |
| **governance (consent + audit)** | 4 | `audit_events` | [`erd-governance-consent.html`](./erd-governance-consent.html) |
| **media** | 4 | `media_assets` | [`erd-media.html`](./erd-media.html) |
| **platform-admin + privacy** | 5 | `platform_role_assignments` + `data_subject_requests` | [`erd-platform-privacy.html`](./erd-platform-privacy.html) |
| **MCP + lead-capture + ideas + hashtags** | 6 | `idempotency_records`, `waitlists`, `ideas`, `hashtag_saved_sets` | [`erd-misc.html`](./erd-misc.html) |

**Total: 60 tables across 13 bounded contexts.** Two contexts (publishing, governance) split into multiple diagrams because each has more than 8 tables.

---

## How to read these diagrams

Every diagram follows the same conventions:

- **Paper:** warm off-white (`#F5F5F0`). **Ink:** jet-black (`#1A1A1A`). **Accent:** saturated red (`#D71921`) — used only on the focal aggregate root of each bounded context (1 per diagram).
- **Header strip** on every entity box carries a `mono` type tag (`ENTITY`, `ENTITY · FOCAL · AGGREGATE ROOT`, `JOIN`, `EXTERNAL · TENANCY`, …) and the entity name in `Space Grotesk`.
- **Field rows** are mono. Columns use `#` for primary key and `→` for foreign key. Right-aligned column holds the SQL type (`varchar(64)`, `timestamptz`, `boolean`, …).
- **Italic one-liners** in `Doto` call out non-obvious invariants, uniqueness constraints, or back-link semantics.
- **FK arrows** carry cardinality badges (`1`, `N`, `0..1`). Dashed connectors mean logical references without a hard FK (e.g., denormalized snapshots, pointer-by-ref).
- **External context** boxes (dashed border) reference tables in other bounded contexts. Open the relevant diagram for full column detail.

The style is brand-matched to the Profile Tailors design system (`.agents/DESIGN.md`). See the skill's `style-guide.md` for the full token set.

---

## Cross-context relationships at a glance

The overview diagram captures the most important FKs. The full list:

| Source → Target | Via |
|---|---|
| `principals` → `workspace_memberships` | `principal_id` (bridge between identity and tenancy) |
| `principals` → `workspace_ownerships` | `owner_principal_id` |
| `principals` → `api_key_credentials` / `refresh_sessions` / `service_account_credentials` | `principal_id` |
| `principals` → `publications` | `author_principal_id` |
| `principals` → `publication_assets` | `created_by_principal_id` |
| `principals` → `audit_events` | `actor_principal_id` (denormalized) |
| `workspaces` → `workspace_memberships` / `workspace_ownerships` | `workspace_id` |
| `workspaces` → `publications` / `publication_assets` / `publication_jobs` | `workspace_id` |
| `workspaces` → `social_accounts` / `social_connections` | `workspace_id` |
| `workspaces` → `social_content_*` (all 7 tables) | `workspace_id` |
| `workspaces` → `media_assets` / `workspace_file_blobs` | `workspace_id` |
| `workspaces` → `ideas` / `hashtag_saved_sets` | `workspace_id` |
| `workspaces` → `invitations` | `workspace_id` |
| `workspaces` → `workspace_direct_grants` / `workspace_target_scopes` / `workspace_entitlements` | `workspace_id` |
| `workspaces` → `data_subject_requests` | `workspace_id` (optional) |
| `workspaces` → `consent_records` / `consent_record_events` | `workspace_id` |
| `workspaces` → `audit_events` | `workspace_id` |
| `workspaces` → `idempotency_records` | `workspace_id` |
| `social_accounts` → `publications` | `social_account_id` |
| `publications` → `publication_jobs` | unique `publication_id` |
| `publication_jobs` → `delivery_attempts` | `publication_job_id` |
| `publication_assets` � `publications` | `publication_asset_links` (ordered join) |
| `recurring_schedules` → `publications` | `template_post_id` |
| `waitlists` → `waitlist_entries` | `waitlist_id` |
| `waitlist_entries` → `waitlist_invitations` | `waitlist_entry_id` |
| `principals` ↔ `invitations` | `issued_by` / `accepted_principal_id` |
| `principals` ↔ `platform_admin_audit_events` | `operator_principal_id` |
| `principals` ↔ `platform_role_assignments` | `principal_id` |
| `roles` ↔ `permissions` | `role_permissions` (many-to-many) |
| `workspace_memberships` ↔ `roles` | `membership_roles` (many-to-many) |
| `principals` ↔ `workspace_direct_grants` / `workspace_target_scopes` | `principal_id` |
| `media_assets` → `workspace_file_blobs` | composite FK `(workspace_id, file_hash)` |
| `ideas` → `publications` | `converted_to_publication_id` (nullable back-link) |
| `social_content_posts` → `publications` | `local_publication_id` (nullable back-link) |
| `social_content_webhook_events` → `social_content_payload_cache` | `payload_cache_key` |

Every cross-context FK is by identity only — no context imports another context's internal entity. This is the architectural rule the diagrams enforce (see ADR-0016: *Aggregates communicate by identity only*).

---

## When to update these diagrams

Update the affected diagram when any of the following change:

- A new table is added or an existing one is renamed
- A foreign key is added, removed, or its cardinality changes
- A new cross-context FK appears
- The focal aggregate of a bounded context shifts

The diagrams are an executable artifact: they are generated from the Liquibase changelogs in `server/smp/src/main/resources/db/changelog/`. If the source-of-truth XML drifts from these diagrams, regenerate from the migrations — do not hand-edit the HTML to match code.
