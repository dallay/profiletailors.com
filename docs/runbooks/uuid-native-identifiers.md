# UUID Identifier Migration Runbook

## Overview

This runbook supports the coordinated cutover required by [ADR-0029](../architecture/adr/0029-use-raw-uuid-identifiers.md). Internal entity IDs become native PostgreSQL UUID values and canonical unprefixed UUID strings at API/wire boundaries. The API media type remains `application/vnd.api.v1+json`. Provider IDs, tokens, storage keys, idempotency keys, and opaque worker identifiers such as `worker-<UUID>` retain their formats.

**Production execution is not authorized by this document.** No production backup, restore rehearsal, complete consumer inventory, named operational approver, or cutover approval is evidenced here. Missing evidence is a stop condition, not permission to proceed. The [backup/restore status](../infrastructure/private-beta-backup-restore-status.md) tracks separate operational evidence.

## Changes

The [UUID changelog](../../server/smp/src/main/resources/db/changelog/uuid-native-identifiers/001-uuid-native-identifiers.yaml) is included after historical and development-seed changelogs. Both changesets require the explicit Liquibase context `@uuid-native-identifiers`; an ordinary startup without that selected context does not perform conversion.

- `uuid-native-identifiers-001-prepare-mapping` runs [001-prepare.sql](../../server/smp/src/main/resources/db/changelog/uuid-native-identifiers/001-prepare.sql). It creates `uuid_identifier_migration_map` and inserts explicit mappings for code-shaped IDs.
- `uuid-native-identifiers-002-convert` runs [002-convert.sql](../../server/smp/src/main/resources/db/changelog/uuid-native-identifiers/002-convert.sql) in its own transaction. It requires `current_setting('profiletailors.uuid_cutover_approved', true)` to equal `on` on the executing JDBC connection. A setting in a separate administrative session or R2DBC connection does not approve the Liquibase connection.
- Convert sets a five-second lock timeout and a thirty-minute statement timeout. It takes `ACCESS EXCLUSIVE` locks on inventoried tables, builds mappings, checks references, converts listed columns, recreates captured foreign keys, and checks row counts.

Prepare and convert are **not one atomic operation**. Prepare can remain committed, including its Liquibase history entry, when convert fails. Table locks and the Liquibase changelog lock do **not** establish that writers are stopped: queued or reconnecting writers can resume after locks release.

## Usage

### Choose the installation path

1. **New installation:** Start from an empty database with the reviewed historical changelogs. Historical DDL still creates legacy column types; an empty database is not automatically a native UUID schema. Rehearse the full changelog with the explicit UUID context and connection approval before starting UUID-only services. Absence of customer rows does not eliminate seed or embedded-reference checks.
2. **Existing installation:** Use a representative restored copy of the actual database, including its Liquibase history. Run the preflight and mandatory backup/restore rehearsal below. Do not deploy UUID-only applications before conversion and postchecks pass.
3. **Development and seeds:** Select the `dev` context only for an intended development database. Development seed changelogs precede conversion and can populate rows rejected by current guards. Rehearse the combination of `dev` and `uuid-native-identifiers` explicitly; do not assume either context selects the other. For disposable data, use only an approved local rebuild procedure. Never reuse a development reset or seed procedure on an existing production database.

There is no dedicated UUID cutover recipe established here. The release operator must review the environment-specific Liquibase invocation and how its JDBC connection receives the approval setting. Do not enable the context or approval permanently in ordinary application startup configuration.

### Preflight and stop conditions

Before scheduling execution, the release operator must obtain and record:

1. The exact release revision, target database/schema, Liquibase history, selected contexts, migration files, database version, and compatible backend/dashboard/admin/consumer versions.
2. A complete owner-based inventory of internal ID columns, relations, polymorphic references, JSON/encrypted payloads, caches, queues, events, callbacks, persisted URLs, and client state. Compare it with `uuid_columns` and `uuid_roots` in the actual SQL. Do not treat those lists as proof of inventory completeness.
3. A review of unmapped values, collisions, duplicate UUID suffixes, orphan references, code-shaped IDs, nullability, constraints, indexes, defaults, and table row counts. Preserve semantic role/permission keys separately from entity IDs.
4. A successful migration and restore rehearsal on an isolated representative copy, with timings, row counts, relationship checks, application/client tests, and a measured recovery time. Record the backup location, integrity result, retention, access controls, restore procedure, and evidence. A backup file without a tested restore is insufficient.
5. A named human decision-maker, maintenance window, stop/drain plan for every writer, recovery decision criteria, and acceptable treatment of writes after the backup. These assignments and approvals remain pending until recorded by the operator.

The current SQL supports these reviewed cases:

- Idea-board `columns_json` must be a JSON array. Its semantic column keys and idea `column_id` values remain unchanged.
- Consent subjects of kind `USER` and `WORKSPACE` use the corresponding entity mapping. `ANONYMOUS` subjects remain opaque. Unknown kinds and unmapped entity subjects stop conversion.
- Consent scopes follow [ADR-0030](../architecture/adr/0030-separate-public-waitlist-consent-scope.md). `consent_records` and `consent_record_events` have nullable UUID `workspace_id` and `waitlist_id` columns with exactly one present. Under the cutover locks, historical `workspace_id = 'waitlist:<key>'` values resolve to the existing waitlist whose semantic `key` matches exactly once, including keys containing colons. Both projections and events move to that list reference before workspace mapping/orphan checks. Missing or ambiguous keys, missing projections and event/projection scope mismatches abort conversion. No workspace or waitlist is fabricated; semantic keys, purposes, policy versions, statuses, record versions and legal evidence remain unchanged.
- Populated `waitlist_invitations` use the mapped entry and principal references. `invitations` support `DIRECT` with no source reference and `WAITLIST` with a mapped entry reference. Other source/reference combinations stop conversion. A legacy `wl-<UUID>` entry uses the table-namespaced deterministic mapping, not suffix stripping; invitation and audit references use that same mapping.
- Historical credential-deletion tombstones retain a mapped connection ID even when the live connection has been deleted. Only the tombstone `connection_id` is exempt from the live-parent check; its workspace and all mapping checks remain mandatory. Provider references, credential references and activity IDs remain unchanged.
- Platform-admin audit targets support `Principal`, `USER`, `WaitlistEntry`, `WaitlistInvitation`, `Invitation`, `TAKEDOWN_REPORT` and `NOTIFICATION` through their entity mappings. `CONFIGURATION` with target `registration.mode` remains semantic text. Metadata references `reportId`, `workspaceId`, `assetId`, `notificationId` and `retryNotificationId` use their entity mappings. Original and successor retry notifications must both have mappings; missing notifications are not exempt historical references. Known scalar semantic fields remain unchanged. Unknown target types, unknown metadata fields, non-string metadata values, invalid metadata shapes and unmapped references stop conversion.
- `notifications` is created outside Liquibase by `NotificationsSchemaInitializer` on `ApplicationReadyEvent`. When the table exists before cutover, SQL registers its `id` column and `ntf` root before locks and mapping. Legacy `ntf-<UUID>` IDs map to their UUID suffix; canonical UUID IDs retain their value. When the table is absent, SQL neither creates it nor invents mappings for audit references. New installations receive UUID columns from the initializer; startup does not convert an existing legacy table. This is separate from `notification_events`.
- Persisted notification payloads use template-specific contracts: `waitlist.welcome.waitlistEntryId` maps to waitlist entries; `platform.invitation.invitationId` maps to invitations; `governance.takedown.reported`, `.approved` and `.rejected` map `reportId` and `assetId` to reports and media assets. Other known string fields retain their exact values, including URLs, URL tokens and free text. The migration does not rewrite template IDs, recipients, idempotency keys or operational state. Unknown templates/fields, non-object payloads, non-string values, missing required reference fields and unmapped references stop conversion rather than applying a global prefix replacement.
- Sequence-generated and identity-generated identifier columns receive `gen_random_uuid()` defaults after conversion. Explicit-ID columns do not acquire automatic generation.

The SQL still rejects nonempty target-scope ID arrays, populated direct-grant conditions, generic audit-event targets or nonempty details, populated bulk-import errors, encrypted social-content payloads, cached idempotency responses, and stored shortlink idempotency responses. Unresolved or ambiguous historical `waitlist:` consent keys, populated polymorphic compliance risk scopes/actors and workspace applicability dimensions also require review. Shortlink owners must map to existing workspaces; a publication `group_id` remains unchanged and does not supply an owner mapping.

These are unresolved data-migration blockers when such data exists, not instructions to delete records. Emptying tables, discarding audit data, or bypassing guards is not an approved workaround. Re-read the SQL at the chosen release revision: this list describes current guards, not a promise of future conversion support. Stop for a reviewed mapping and tested implementation if any guard applies.

### Consent scope database contract

`ck_consent_records_scope` and `ck_consent_record_events_scope` enforce the exclusive choice of scope. Projection scope references have foreign keys to real `workspaces` or `waitlists`. Events use `(consent_id, workspace_id)` and `(consent_id, waitlist_id)` foreign keys to the projection's corresponding unique pairs. With the exclusive-scope checks, every event must reference an existing projection in exactly the same scope; a nullable column cannot bypass both foreign keys.

The existing active index is renamed to `uq_consent_active_workspace`, retaining `(workspace_id, subject_kind, subject_value, purpose, policy_version) WHERE status = 'ACTIVE'`. `uq_consent_active_waitlist` applies the same identity and predicate using `waitlist_id`. The repository must select the matching `ON CONFLICT` column list and retain the exact active predicate. A different policy version or scope remains an independent identity; withdrawal does not remove historical rows or events.

Workspace reads, withdrawal and history use `workspace_id = :workspaceId AND waitlist_id IS NULL`. Waitlist operations use `workspace_id IS NULL AND waitlist_id = :waitlistId`. A null workspace alone is not a waitlist selector. Tenant endpoints must never fall back to public scope. These rules also apply when a workspace and a waitlist happen to have the same UUID value.

The historical changelogs remain unchanged. The explicitly approved UUID cutover adds and converts the scope columns in its transaction, then installs the scope checks, foreign keys and indexes. An ordinary application startup does not perform this conversion. Runtime Kotlin, authorization and BDD verification remain separate from the SQL harness. Engineering approval does not replace the pending legal review or operational approval.

### Disposable SQL regression harness

Run `bash server/smp/src/main/resources/db/changelog/uuid-native-identifiers/tests/run.sh` only against its newly created disposable Docker container. Set `LIQUIBASE_TEST_CLASSPATH` to existing Liquibase CLI and PostgreSQL JDBC dependencies, and `UUID_SQL_TEST_LOGS` to an external evidence directory. The harness applies historical changelogs, exercises rejection and rollback cases, and validates populated conversion with both identity and sequence defaults. It also covers the presence and absence of `notifications`, all five known payload templates, original/successor retry audit mappings, missing roots, collisions and opaque-field preservation. Failed notification conversions must preserve both notification and audit snapshots.

Consent regressions cover valid historical waitlist projections/events, missing and ambiguous keys, mismatched event scopes, unchanged workspace consent, legal-evidence snapshots, scope XOR/foreign keys, both active conflict targets, policy-version identity, scoped withdrawal/history and concurrent duplicate requests in both scope types. The ambiguity fixture simulates historical uniqueness drift only in its disposable copy; the production migration does not remove waitlist-key uniqueness. Failed consent conversions must preserve records, events, waitlists, nullability, columns and mapping state. The harness does not use Gradle or connect to an existing database. These fixtures are not a representative production restore or application-level verification.

### Cutover procedure

1. Complete preflight and rehearse both success and failure recovery before the maintenance window. Stop if required evidence or human approval is missing.
2. Block new mutating ingress and stop/drain **all writers**: every backend replica, publishing/scheduler worker, queue consumer, callback/webhook processor, retry process, import job, administrative script, migration runner, and external database client. Disable automatic restarts/reconnects for legacy writers. Worker safe-off alone is insufficient. Verify drained in-flight transactions and no remaining writer sessions or pending work that can replay legacy IDs.
3. With writers still stopped, capture the final consistent pre-conversion backup and baseline counts/history. Ensure the rehearsed restore procedure applies to this backup. Keep writers stopped through validation and the release decision.
4. Ask the designated human decision-maker to approve this exact database, revision, context selection, backup evidence, blockers assessment, and recovery plan. Only after that decision may the operator arrange `profiletailors.uuid_cutover_approved=on` on the **Liquibase JDBC connection** and explicitly select the `uuid-native-identifiers` context. The `@` in the changelog marks an explicitly required context; it is not a general startup authorization.
5. Execute the reviewed migration invocation once through the approved operator procedure. Record changeset outcomes and errors. If conversion fails, keep writers stopped and follow recovery below; do not repeatedly retry without inspection.
6. Verify both changeset history entries, expected mapping rows, native UUID types for every inventoried column, unchanged row counts, uniqueness, non-null requirements, zero unmapped IDs/orphans, and valid foreign keys/indexes. Independently check embedded/polymorphic references, owner references and representative joins; SQL row-count checks alone do not establish semantic correctness. Verify excluded provider/opaque values are unchanged.
7. Validate the compatible release in isolation: canonical UUID API responses and inputs, authentication/session references, workspace isolation, publication/shortlink ownership, invitations, seed consumers, and affected dashboard/admin flows. Resolve or invalidate replayable queues, caches, URLs and stale client state through reviewed consumer-specific procedures; do not blindly strip prefixes.
8. Activate only verified compatible backend, clients, workers and consumers. Prevent legacy application images and cached legacy clients from mutating the converted database/API. Resume writers in a controlled order after the human release decision; monitor parse rejections, FK errors, mapping anomalies and publishing behavior. Keep structured telemetry fields; preserve opaque `worker-<UUID>` values.
9. Remove the temporary JDBC approval and explicit migration-context selection from routine startup configuration. Retain mapping, history, backup and validation evidence under the approved retention/access policy. Do not drop the mapping table as incidental cleanup.

## Troubleshooting and Recovery

### Prepare committed but convert failed

Inspect `DATABASECHANGELOG`, the mapping table and actual column types before deciding what ran. Convert is transactional, but prepare is a separate committed changeset. Verify rollback of convert rather than assuming the whole changelog reverted. Do not drop the mapping table, delete history entries, edit applied changesets, or bypass validation guards to force a rerun.

Keep all writers stopped. Record the failed guard, timeout or constraint error. A reviewed retry is possible only after confirming the database state and resolving the cause without weakening checks. If returning to the pre-migration state is required, use the rehearsed coordinated restore procedure and matching application/client versions.

### Conversion committed or application validation failed

Do not roll back only the application to a legacy image. The converted schema and UUID-only wire contract are incompatible with assumed legacy prefix handling. Keep writers stopped and obtain a human decision between a tested compatible application, a reviewed forward fix, or coordinated database restore with matching application/client/consumer state.

There is no reverse conversion supplied by these changesets. After new writes, restoration can lose data or conflict with external effects such as published posts. Record and approve reconciliation and any data-loss decision explicitly. Do not promise lossless recovery or regenerate prefixed IDs from UUIDs. See the [production rollback runbook](./production-rollback.md) before using generic service rollback commands.

## References

- [ADR-0029: Raw UUID identifiers](../architecture/adr/0029-use-raw-uuid-identifiers.md)
- [Migration design](../../.agents/sdd/changes/archive/2026-10-26-uuid-native-identifiers/design.md)
- [Implementation inventory and known gaps](../../.agents/sdd/changes/archive/2026-10-26-uuid-native-identifiers/implementation-inventory.md)
- [Local archive disposition and release limitations](../../.agents/sdd/changes/archive/2026-10-26-uuid-native-identifiers/archive-report.md)
- [Observability contracts](../observability-contracts.md)
- [Production rollback](./production-rollback.md)
- [Backup and restore evidence status](../infrastructure/private-beta-backup-restore-status.md)
