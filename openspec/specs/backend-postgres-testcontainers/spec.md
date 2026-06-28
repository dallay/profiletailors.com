# Backend PostgreSQL Testcontainers Specification

## Purpose

Define backend test policy for production-faithful PostgreSQL verification while preserving fast pure/domain feedback loops.

## Requirements

### Requirement: Selective PostgreSQL-backed test classification

Backend tests that validate SQL semantics, constraints, indexes, Liquibase migrations, R2DBC PostgreSQL behavior, locking, concurrency, or PostgreSQL-specific repository behavior MUST run against PostgreSQL Testcontainers and MUST be tagged `postgres`. Pure domain/application tests MUST remain DB-free and MUST NOT require Docker.

#### Scenario: Production-semantics test uses PostgreSQL

- GIVEN a backend test verifies SQL, migration, constraint, index, lock, or concurrency behavior
- WHEN the test is introduced or migrated
- THEN it MUST use PostgreSQL Testcontainers
- AND it MUST be tagged `postgres`

#### Scenario: Pure test remains fast

- GIVEN a domain or application test has no database dependency
- WHEN the fast backend suite runs
- THEN the test MUST run without Docker
- AND it MUST NOT depend on a PostgreSQL container

#### Scenario: H2 is not authoritative for PostgreSQL behavior

- GIVEN H2 compatibility conflicts with production PostgreSQL semantics
- WHEN deciding how to verify the behavior
- THEN the PostgreSQL Testcontainers test MUST be the authority
- AND production schema or SQL MUST NOT be weakened solely for H2

### Requirement: Shared PostgreSQL integration support

The backend test infrastructure SHALL provide reusable PostgreSQL Testcontainers support for repository, Liquibase, and Spring integration tests. Shared support MUST configure R2DBC and Liquibase consistently and MUST prevent data leakage between tests.

#### Scenario: Repository test uses shared support

- GIVEN a repository test needs PostgreSQL behavior
- WHEN it starts
- THEN it SHALL obtain PostgreSQL R2DBC and Liquibase configuration from shared support
- AND it SHALL not duplicate container bootstrap unnecessarily

#### Scenario: Test data cleanup covers dependent media tables

- GIVEN PostgreSQL-backed tests create media assets and workspace file blobs
- WHEN a test finishes or the next test starts
- THEN cleanup MUST include dependent rows in a safe order
- AND later tests MUST not observe prior test data

### Requirement: Fast and Docker-backed command separation

Build tooling MUST preserve fast commands that exclude `postgres` tests and SHOULD expose explicit Gradle/Just commands for PostgreSQL integration tests. Full CI MUST include PostgreSQL integration coverage; local fast CI MUST remain Docker-free.

#### Scenario: Fast command excludes PostgreSQL tests

- GIVEN Docker is unavailable locally
- WHEN `backend-test-fast` or `ci-local` runs
- THEN tests tagged `postgres` MUST be excluded
- AND the command MUST complete without starting Testcontainers

#### Scenario: Full CI includes PostgreSQL integration

- GIVEN Docker is available in CI or local infrastructure
- WHEN `ci-full` runs
- THEN PostgreSQL integration tests MUST run
- AND existing PostgreSQL BDD coverage SHOULD remain included

### Requirement: Media asset dedup schema hardening verification

PostgreSQL-backed tests MUST verify the media-asset-dedup schema hardening for `workspace_file_blobs` and `media_assets`, without changing media-library product behavior. Verification MUST cover composite foreign keys, partial garbage-collection index behavior or definition, status/hash constraints, compare-and-swap persistence, `FOR UPDATE`, `SKIP LOCKED`, and `ON CONFLICT` paths where applicable.

#### Scenario: PostgreSQL constraints reject invalid media rows

- GIVEN the media dedup schema is migrated on PostgreSQL
- WHEN invalid workspace/blob relationships, statuses, or hash states are inserted
- THEN PostgreSQL MUST reject the invalid rows
- AND valid rows MUST remain accepted

#### Scenario: PostgreSQL concurrency SQL is verified

- GIVEN concurrent media blob operations run through repository APIs
- WHEN CAS, row-locking, skip-locked, or conflict-handling paths execute
- THEN PostgreSQL MUST enforce the expected persistence outcome
- AND no H2-only assertion may replace this coverage

### Requirement: PostgreSQL Evidence for Publishing Transactions

The change MUST include `postgres`-tagged integration evidence using real PostgreSQL, real R2DBC publishing repositories, and the real transaction runner. Evidence MUST cover rollback for Create, Edit, Cancel, Retry, and Reschedule and successful paired commit; mocks, pass-through runners, or H2 MUST NOT substitute for this evidence.

#### Scenario: Every workflow rolls back on second-write failure

- GIVEN each workflow runs against PostgreSQL and its job mutation is forced to fail after the publication mutation
- WHEN the transaction exits with failure
- THEN direct persisted-state queries MUST prove the prior publication state and asset links are preserved
- AND pre-existing jobs MUST survive, while failed Create leaves no publication, links, or job

#### Scenario: Every workflow commits both sides

- GIVEN each workflow runs against PostgreSQL without an injected failure
- WHEN the transaction completes
- THEN direct persisted-state queries MUST prove its publication and asset-link mutation committed
- AND the matching job mutation MUST also be committed
