# Apply Progress

## Commands Run

| Command | Result |
|---|---|
| `git status --short --branch` | Exit 0; clean worktree on `feature/keycloak-auth-migration`, one authorized baseline commit ahead of `origin/main`. |
| `just -l` | Exit 0; command hub listed, including `backend-test-fast`, `backend-check`, and `backend-test-postgres`. |
| Route/source inventory `rg` review | Exit 0; inspected identity, credentials, MCP, and HTTP controller route mappings. |
| Identity model/port/changelog inspection | Exit 0; confirmed existing `principals` uniqueness is `(principal_type, subject, provider)` and `user_identities.email` is unique; `PrincipalIdentityLookup` exposes lookup only, with no verified external identity-link or email synchronization operation. |
| TDD test command availability | Confirmed by `.agents/sdd/config.yaml` and `just -l`: `just backend-test-fast` is configured. Not run because the identity-link persistence contract is unresolved. |

No source behavior, tests, or persistence were changed. No tests were run.

## Task evidence

| Task | State | Evidence |
|---|---|---|
| 1.1 | Complete | `.agents/sdd/changes/keycloak-human-authentication/identity-and-consumer-inventory.md` records current source-visible consumer/route families, methods, credential, bounded-context owner, and disposition. It distinguishes source-visible evidence from external consumer confirmation. |
| 1.2 | Blocked before RED | Existing storage places one `subject`/`provider` identity directly on `principals`; its unique constraint includes `principal_type`. No explicit contract says whether Keycloak issuer/subject should replace those fields or use a separate link relation. The application port has no external-link lookup contract. |
| 1.3 | Blocked | No behavior implementation. Email synchronization has no application port; conflict and concurrency semantics are not defined by the existing lookup-only port. |
| 1.4 | Blocked | No unit/PostgreSQL tests or continuity review were run because no persistence design was selected. |

## TDD evidence

Task 1.1 is inventory documentation and did not require TDD. Tasks 1.2–1.4 did not begin. No RED/GREEN/REFACTOR evidence is claimed. The configured runner exists, but test authoring was stopped because the existing data model and ports do not specify how to retain existing principal identity fields while representing an independently approved Keycloak link, or how to handle email-sync conflicts atomically.

## Review workload

- Forecast: High.
- Strategy: `feature-branch-chain`.
- Boundary: `trunk=main`; `parent_branch=main`; `base=main`; `branch=feature/keycloak-auth-migration`; `position=1`; `issue=none`.
- Implemented slice: source-visible non-MCP credential consumer/route inventory only (task 1.1).
- Remaining review budget concern: identity-link persistence and conflict-safe email synchronization need an explicit contract before tasks 1.2–1.4 can proceed.

## Blocker

The current schema can enforce uniqueness for a principal's existing `(principal_type, subject, provider)` fields, but it has no distinct external identity-link record. Reusing `provider` as the Keycloak issuer and replacing `subject` would change existing local identity data; adding a separate association changes the approved migration shape. The existing application lookup port also cannot distinguish an approved external link from the current principal subject/provider projection or atomically synchronize verified email while preserving local identity/account/audit continuity. Stop before behavior implementation and request clarification on whether Keycloak links are a separate table or a replacement for `principals.subject/provider`, plus whether email conflicts skip synchronization or reject authentication. No task checkbox or state phase was advanced.
