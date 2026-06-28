# Delta for Backend PostgreSQL Testcontainers

## ADDED Requirements

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
