# Verification Report — mcp-server

> Authored 2026-08-27 as part of Linear DALLAY-434 close-out housekeeping.
> This is not a live `sdd-verify` agent run. It summarises the per-PR CI gate
> evidence captured at merge time so the change can be archived cleanly.

## Scope of this verification

The `mcp-server` change delivered the read-only MCP path: four tools, embedded
Spring AI 2.0 server with STATELESS Streamable HTTP, OAuth 2.1 + Keycloak DCR +
RFC 8707 + workspace isolation, BDD scenarios, audit, rate limiting.

Out of scope and explicitly deferred to DALLAY-590:

- Write tools (`create_publication`, `edit_publication`, `delete_publication`,
  `cancel_publication`, `retry_publication`).
- Connected Applications registry.
- Stateful streaming / SSE notifications for agents.

## PR-by-PR CI gate

Every PR passed the `CI Gate` workflow
(`.github/workflows/ci.yml`) on its base branch before merge. The relevant
gates for this change are backend-only.

| PR | Title | Merge commit | PR # | Backend gates run by CI |
|---|---|---|---|---|
| PR 1 | server module foundation + compatibility spike | `28959924` | [#539](https://github.com/dallay/profiletailors.com/pull/539) | backend-check, backend-test, backend-bdd-fast |
| PR 2 | MCP server security foundation | `821e1968` | [#560](https://github.com/dallay/profiletailors.com/pull/560) | backend-check, backend-test, backend-bdd-fast |
| PR 3 | 4 read tools, error mapping, audit, and rate limiting | `83ddcab2` | [#573](https://github.com/dallay/profiletailors.com/pull/573) | backend-check, backend-test, backend-bdd-fast |
| PR 4 | BDD, workspace isolation, contract tests, and docs | `b8609a3d` | [#574](https://github.com/dallay/profiletailors.com/pull/574) | backend-check, backend-test, backend-bdd-fast |

Housekeeping follow-up commit `eb14a310` reconciled OpenSpec implementation
evidence after merge.

The CI `Gate` job is the canonical source; it requires every applicable check
to be `success` or `skipped`. No PR in the chain was merged with a failing
gate. PR title verification is enforced by the release workflow.

## Functional checks observed at merge time

- `just backend-check` — green at each PR merge (Detekt + unit tests + modularity).
- `just backend-test` — green at each PR merge.
- `just backend-bdd-fast` — green at each PR merge.

PostgreSQL-specific suites (`backend-bdd-postgres`, `backend-test-postgres`)
were not exercised by this change; the change did not introduce R2DBC
migrations or persistence-bound behaviour beyond reusing existing tables.

## Spike verification

`openspec/changes/archive/2026-08-27-mcp-server/spikes/SPIKE_OUTCOME.md` documents the
compatibility decisions inherited by PR 2 onward:

- Spring AI 2.0 `@McpTool` annotation API verified against the GA starter
  (`spring-ai-starter-mcp-server-webflux:2.0.0`).
- Keycloak DCR (RFC 7591) confirmed supported.
- Keycloak CIMD confirmed not supported in the tested Keycloak 26; the
  fallback (pre-registered clients) is the production path.
- RFC 8707 `resource` indicator verified for audience separation.
- Workspace injection option A (signed context, Keycloak protocol mapper)
  selected over custom authenticator and intermediate Authorization Server.
- MCP Inspector compatibility verified.

## Out of band risks

These were called out in the original proposal and remain open in DALLAY-590:

- Tenant / scope bypass is mitigated by signed workspace claim plus
  audience and invocation checks. The audit and rate-limit filters are in
  place but not load-tested.
- CIMD is a SHOULD in the November 2025 MCP spec; the fallback to DCR plus
  pre-registered clients is acceptable per the spike outcome but should be
  re-tested against real Keycloak 26 in staging before any external agent
  traffic is enabled.

## Verdict

**PASS** for archival purposes. The change is merged into `main`, all four PRs
cleared the CI gate, all read-path tools and security wiring are exercised by
unit, contract and BDD tests within `just backend-bdd-fast`. Caveats above
are tracked in DALLAY-590 and the open issues on CIMD re-test in staging.
