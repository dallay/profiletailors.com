## Exploration: DALLAY-562 — Administrative audit event infrastructure

### Current State
The repository already has three overlapping audit persistence models:

- `platformadmin` is the live Back Office path. `AdminAuditEvent`, `AdministrativeAuditPublisher`, and `R2dbcAdminAuditRepository` persist completed invitation, waitlist, and platform-role actions to `platform_admin_audit_events`; six handlers already depend on this seam, and PostgreSQL tests cover persistence/querying plus a successful invitation audit.
- `audit` provides an optional cross-cutting `AuditHook` backed by `audit_events`, mainly for authorization and workspace mutation facts. `R2dbcAuditHook` serializes details without an implemented redaction policy despite operational documentation claiming write-time review.
- A separate `administrative` context and `administrative_audit_events` table already exist from the current DALLAY-562 worktree state, but production capabilities do not call them. Only domain/publisher unit tests exist; the planned repository integration test is absent.

The existing DALLAY-562 `proposal.md`, root-level `spec.md`, `design.md`, and `tasks.md` predate this exploration and contain stale assumptions: they describe `administrative` as new, classify `AdminAuditEvent` as narrowly role-specific, disagree with migration 006 (`jsonb`, actual column widths and indexes), and mark `spec` complete although no delta exists under `specs/{domain}/spec.md`. The prior state claimed partial apply. This exploration resets the phase record so those downstream artifacts can be reconciled rather than treated as approved.

Security is incomplete in both live and duplicate paths. The new `administrative` model rejects sensitive key names, but its publisher relies on callers to redact and its repository uses a prohibited unchecked-cast suppression. The live `platformadmin` model accepts arbitrary metadata, the repository does not persist that metadata, and no redaction test guards its write boundary. Key-name denylisting alone also cannot detect a secret stored under a benign key such as `value` or `payload`.

### Affected Areas
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/domain/AdminAuditEvent.kt` — live event shape, action/result vocabulary, actor and safe-metadata contract.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/contracts/AdministrativeAuditPublisher.kt` — reusable seam already consumed by Back Office handlers.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/infrastructure/persistence/R2dbcAdminAuditRepository.kt` — live write/read adapter; metadata is currently dropped.
- `server/smp/src/main/kotlin/com/profiletailors/smp/platformadmin/application/handler/` — six mutation handlers emit audit events after successful persistence; invitation/waitlist/role tests form the main blast radius.
- `server/smp/src/main/kotlin/com/profiletailors/smp/administrative/` — duplicate, currently unused model/port/adapter/configuration introduced by the stale implementation direction.
- `server/smp/src/main/kotlin/com/profiletailors/smp/audit/` — existing generic hook and `audit_events` store; overlapping vocabulary and an unimplemented documented redaction guarantee.
- `server/smp/src/main/resources/db/changelog/platform-admin/003-create-platform-admin-audit-events.yaml` — canonical live Back Office audit table.
- `server/smp/src/main/resources/db/changelog/platform-admin/006-create-administrative-audit-events.yaml` — second audit table that creates split history and migration/rollback concerns.
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/integration/R2dbcAdminAuditRepositoryPostgresIntegrationTest.kt` — existing persistence/query evidence.
- `server/smp/src/test/kotlin/com/profiletailors/smp/platformadmin/integration/PlatformAdminInvitationTransactionPostgresIntegrationTest.kt` — proves successful invitation audit persistence, but not rollback/atomicity when audit persistence fails.
- `openspec/specs/admin-authorization/spec.md` and `openspec/specs/invitations/spec.md` — require restricted audit access and token-free invitation evidence.
- `docs/infrastructure/private-beta-correlation-matrix.md` — canonical secret/token exclusions and correlation pivots; currently describes `audit_events`, not the live `platform_admin_audit_events` store.

Dependency/blast-radius note: `AdministrativeAuditPublisher` has 14 callers/references and `AdminAuditAction` has 23. Replacing that contract affects platform-role, waitlist, invitation, controller/bootstrap, unit, and PostgreSQL integration paths. The duplicate `AuditEventPublisher` has no production consumer, so removing or adapting it is lower risk than migrating live handlers to a second context.

### Approaches
1. **Consolidate on the existing `platformadmin` audit seam** — evolve `AdminAuditEvent`/`AdministrativeAuditPublisher` and `platform_admin_audit_events`, remove or supersede the unused duplicate direction, and enforce safe metadata at the publisher/persistence boundary.
   - Pros: Reuses the active capability seam and tested query store; smallest blast radius; avoids split audit history; aligns ownership with Back Office authorization and UI.
   - Cons: Requires a migration strategy for metadata and possibly actor type; must reconcile migration 006 if already applied anywhere; action-specific safe metadata needs a deliberate contract.
   - Effort: Medium

2. **Make `administrative` the canonical audit bounded context** — adapt all platform-admin handlers and future capabilities to the new port/table, then migrate or bridge existing `platform_admin_audit_events` data and queries.
   - Pros: Creates an explicitly reusable cross-capability boundary; separates audit ownership from platform-admin workflows.
   - Cons: Duplicates current capability before consolidation, has the largest call-site and data-migration blast radius, and risks cross-context coupling plus two query models.
   - Effort: High

3. **Extend the generic `AuditHook`/`audit_events` ledger** — represent administrative completion facts as `MutationAuditFact` and use the governance reader/store.
   - Pros: One broad audit ledger and existing workspace-audit query primitives.
   - Cons: The hook is optional, workspace-oriented, lacks the required correlation field and stable admin action/result model, and currently persists unredacted details; changing it would broaden DALLAY-562 beyond Back Office.
   - Effort: High

### Recommendation
Use approach 1. The repository already has a production-consumed, permission-gated Back Office audit seam and PostgreSQL persistence tests. The proposal should treat DALLAY-562 as consolidation and hardening, not creation of a parallel bounded context.

The revised contract should centralize enforcement at or before the persistence boundary so a caller cannot bypass redaction. Prefer action-specific allowlisted/typed metadata over arbitrary caller-supplied maps; reject unsupported keys and bound key/value lengths. Persist only completed actions unless the proposal explicitly expands scope to rejected/failed attempts. Correlation should come from the existing request context rather than random generation. For same-database mutations, define and test whether business mutation plus audit insertion are atomic; do not silently choose best-effort behavior.

The proposal must also decide how to handle migration 006 in environments where Liquibase may already have recorded it. Do not edit an applied changeset in place. A follow-up migration or explicit pre-release rollback is required based on deployment evidence.

### Risks
- Two active audit tables and three event abstractions can split evidence and make the admin audit UI incomplete.
- Caller-side denylisting is bypassable and key-only filtering cannot guarantee that values contain no raw token or secret.
- The live repository silently drops `AdminAuditEvent.metadata`, so current role metadata is not auditable.
- Existing handler tests cover successful publication but not transaction rollback or audit-store failure semantics.
- Migration 006 may already be registered outside this worktree; deleting or rewriting it without deployment evidence can break Liquibase checksums.
- The operational correlation matrix names `audit_events` as the admin pivot while current Back Office handlers write `platform_admin_audit_events`.
- Existing `administrative` persistence code contains an unchecked-cast suppression forbidden by repository policy.

Unresolved product/architecture questions for proposal:

- Which store is canonical for the future `platform.audit.read` UI: `platform_admin_audit_events` or `audit_events`?
- Are only successful completed actions in scope, or must rejected/failed attempts also be retained?
- Must audit persistence be in the same transaction as the administrative mutation, and should an audit write failure roll back the action?
- Is actor type always a platform operator for this capability, or must service/system actors be first-class now?
- Is correlation ID mandatory, what existing request-context source owns it, and what happens for background jobs?
- What action-specific metadata keys are approved, what size/cardinality limits apply, and are email/reason fields prohibited or transformed?
- Has migration 006 run in any shared or production-like environment?
- What retention, immutability/tamper resistance, and access controls apply to stored administrative audit data?

### Ready for Proposal
Yes — with mandatory revision of the existing proposal. The evidence is sufficient to propose consolidation on the live `platformadmin` seam, but the proposal must resolve canonical-store ownership, atomicity/failure semantics, metadata allowlisting, correlation sourcing, and migration-006 disposition before spec/design work resumes. Existing downstream artifacts are not implementation-ready as written.
