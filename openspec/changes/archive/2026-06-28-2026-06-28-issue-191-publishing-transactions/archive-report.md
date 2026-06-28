# Archive Report: Publishing Mutation Transactions

- Change: `2026-06-28-issue-191-publishing-transactions`
- Archived at: 2026-06-28
- Artifact store: OpenSpec filesystem
- Verification verdict: **PASS WITH WARNINGS**
- Critical issues: None

## Specs Synced

| Domain | Action | Details |
|---|---|---|
| `publishing` | Updated | Added 4 requirements and 7 scenarios covering atomic publication/job mutations, framework-neutral orchestration, persisted-result job derivation, and unchanged Delete behavior. |
| `backend-postgres-testcontainers` | Updated | Added 1 requirement and 2 scenarios requiring real PostgreSQL commit/rollback evidence for all five publishing workflows. |

## Archive Verification

- Main specs were updated before archival.
- Proposal, delta specs, design, tasks, verification report, exploration, and state are retained in the archive.
- Verification reported no critical issues.
- State is finalized with `current_phase: archive` and `next: done`.

## Preserved Warnings

1. Edit/Retry/Reschedule rollback tests use a failing repository decorator before replacement delegation rather than exercising a delete-then-failed-insert path.
2. Existing-job rollback comparisons cover selected persisted columns rather than a complete row snapshot.

## Post-Archive Validation

- `just ci-full` passed after exporting `SMP_POSTGRES_TEST_PASSWORD` from `SMP_POSTGRES_PASSWORD`, matching the CI workflow contract.
- The BDD database reset now restores the Liquibase-required `role-owner` baseline after cleanup; this prevents PostgreSQL transactions from remaining aborted after a swallowed foreign-key violation.
- Infrastructure was stopped with `just infra-down` after validation.
