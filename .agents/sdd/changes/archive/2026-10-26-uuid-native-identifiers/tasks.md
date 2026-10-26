# Tasks: Native UUID Identifiers

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | 1,000+ |
| 400-line budget risk | High |
| Chained PRs recommended | Yes; partition after implementation audit |
| Suggested split | Reviewable commits on a dedicated branch; PR strategy to follow diff review |
| Delivery strategy | Dedicated branch; commit, push, and PR only after all required tests and builds pass |
| Chain strategy | size-exception; user authorized publication after tests and builds pass |

Decision needed before apply: No
Chained PRs recommended: Yes
Chain strategy: size-exception — user authorized branch, commit, push, and PR after all required tests and builds pass; production SQL and deployment remain unauthorized
400-line budget risk: High

### Suggested Work Units

| Unit | Goal | Verification |
|---|---|---|
| 1 | Inventory, ADR, SQL plan | Schema/consumer evidence; migration, backup, rollback review |
| 2 | Backend IDs and persistence | Backend, BDD, PostgreSQL relation tests |
| 3 | Clients and docs | Frontend gates, E2E, docs and diff review |

PR partition is a later decision, not an apply prerequisite. Work in this branch/worktree. The user executes reviewed SQL and validates production before UUID-only deployment.

## Phase 1: Inventory

- [x] 1.1 Reconcile the executable column/root manifest and owner/consumer inventory: 174 unique entries across 74 tables, including optional notifications and both WAITLIST references. Preserve semantic keys, provider IDs, tokens, storage/idempotency keys, group IDs and lease tokens. See `implementation-inventory.md` and the final SQL harness evidence.
- [x] 1.2 Trace included identity/reference producers and consumers through the context adapters, API/client fixtures and typed historical payloads. Independent residual-producer review classified transient validation markers and the synthetic rule marker without inventing persisted identities. Unknown historical contracts fail closed rather than receive speculative mappings.
- [x] 1.3 User decision: retain `application/vnd.api.v1+json`; use raw internal UUIDs in that version.

## Phase 2: Data model and migration plan

- [x] 2.1 Supersede ADR `docs/architecture/adr/0005-use-prefixed-string-identifiers.md` with ADR-0029; update index/architecture README and preserve historical decision text.
- [x] 2.2 Prepare and independently review SQL for approval, collisions, references, conversion, row counts, constraints and indexes. Execute only on disposable databases: 34 rejection/rollback cases, 93 validated foreign keys and 405 valid indexes. Production SQL remains unexecuted.
- [x] 2.3 Document backup/restore requirements, stopped-writer order, validation and rollback/forward-fix limitations. Do not claim a representative production restore rehearsal has occurred.
- [x] 2.4 Prepare a conditional coordinated cutover with explicit stopped-writer approval. No downtime approval or rolling compatibility is assumed; if operators cannot satisfy these conditions, deployment remains blocked and needs a separately approved compatibility plan.

## Phase 3: Backend

- [x] 3.1 Migrate inventoried generators, parsers and adapter bindings/reads while preserving nominal context boundaries and excluded formats. Final backend-check and native PostgreSQL suites pass.
- [x] 3.2 Convert inventoried internal references to native PostgreSQL UUID through the explicitly gated migration, including legacy notifications and default-generated UUID keys.
- [x] 3.3 Accept canonical UUID workspace headers; reject invalid values without mutation. Resolver, shortlinks, authorization and isolation regressions pass in the full backend suites.
- [x] 3.4 Use structured context/field names rather than entity prefixes for diagnostics; preserve excluded operational/opaque formats.
- [x] 3.5 Implement approved WORKSPACE/WAITLIST scopes with real entity UUIDs, exclusive references, scoped uniqueness, events, queries, withdrawal and historical migration. Same-UUID isolation is verified in PostgreSQL, BDD and live HTTP acceptance.

## Phase 4: Clients and regression

- [x] 4.1 Update app/admin consumers and fixtures, restore semantic translations/template keys, align session IDs and validate cleanup workspace inheritance. Browser consent receipt/version remains unchanged; shared/web tests pass 74/74.
- [x] 4.2 Verify canonical shortlinks ownership, authentication, invalid workspace rejection and workspace isolation in native PostgreSQL/HTTP tests.
- [x] 4.3 Verify publishing/media/shortlinks relationships and migration constraints, indexes, collisions and orphan rejection. The full PostgreSQL lane passes 506/506.

## Phase 5: Documentation and gates

- [x] 5.1 Reconcile active API/spec overlays, product scope, ADR-0029/0030, C4, indexes, data inventory and operational runbook. Tenant HTTP shape and v1 media type remain unchanged.
- [x] 5.2 Execute applicable backend/web gates, SQL harness and observable acceptance without weakening assertions, rules or coverage gates. See `apply-progress.md` and the final verification/QA reports for exact commands, skips and evidence.
- [x] 5.3 Keep deployment blocked on separate legal/operational approval, representative backup/restore and user-executed production pre/postconditions. No production, commit, push or deployment action is claimed.

## Final coordination

- [x] Independent technical verification and persisted `verify-report.md`.
- [x] Independent acceptance evidence review and persisted `qa-report.md`.
- [x] Reconcile final state and close the local task without implying remote CI or production approval.

Operational approval and execution are outside this local implementation checklist. Existing scheduler/media skips, remote CI and live third-party Unsplash are reported explicitly, not represented as passing runs.
