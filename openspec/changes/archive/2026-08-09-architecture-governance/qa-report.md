# Acceptance QA Report: architecture-governance

## Identity
- Change: `architecture-governance`
- Mode: openspec
- QA phase: `sdd-qa`
- Date: 2026-08-09

## Sources of Truth
- Proposal: `openspec/changes/architecture-governance/proposal.md`
- Specifications: `openspec/changes/architecture-governance/specs/architecture-governance/spec.md`
- Design: `openspec/changes/architecture-governance/design.md`
- Tasks: `openspec/changes/architecture-governance/tasks.md`
- Technical verification: `openspec/changes/architecture-governance/verify-report.md`
- Phase state: `openspec/changes/architecture-governance/state.yaml`
- OpenSpec configuration: `openspec/config.yaml`
- Relevant canonical guidance inspected:
  - `.agents/skills/architecture-governance/SKILL.md`
  - `.agents/skills/backend-platform/ddd-architecture/SKILL.md`
  - `.agents/skills/backend-platform/hexagonal-architecture/SKILL.md`
  - `.agents/skills/frontend-platform/frontend-architecture/SKILL.md`
  - `.agents/skills/frontend-platform/vue/SKILL.md`
  - `.agents/skills/frontend-platform/astrolicious-astro/SKILL.md`
  - `.agents/agentsync.toml`
  - `justfile`

The verification handoff reports `PASS WITH WARNINGS` for the implementation and records runtime
technical evidence. This QA phase independently exercised repository/operator acceptance paths and
did not rerun the technical test/build matrix owned by `sdd-verify`.

## Target and Environment
- Target: Canonical agent-guidance distribution and repository command-hub behavior for the
  `architecture-governance` change. This is not a browser product target.
- Environment: macOS, repository `/Users/acosta/Dev/dallay/worktrees/ddd`, branch `ddd`, HEAD
  `f4ebb134`.
- Credentials/permissions: No credentials or elevated permissions were required for the selected
  local filesystem, `just`, and AgentSync status checks.
- Limitations:
  - The change is documentation/agent-guidance scope and has no dedicated application-under-test
    or product acceptance surface.
  - No executable checker currently validates ARCH contract semantics, exception rationale, or
    scope traceability; the future `just architecture-check` is deliberately deferred.
  - The worktree contains unrelated pre-existing dirty and untracked paths documented by the
    proposal and verification report. QA did not modify or include those paths.
  - AgentSync apply was not repeated in QA because it can mutate generated tooling paths; the
    independently selected non-mutating status and link-resolution checks were run instead. The
    verification handoff records the prior apply as successful.

## Capability Inventory

| Capability | Availability | Selected? | Rationale / rejection reason |
|---|---|---:|---|
| Repository/manual operator inspection | available | Yes | Appropriate target is canonical guidance, distribution links, and command behavior. Manual semantic inspection remains evidence only and is not promoted to PASS under the static-inspection rule. |
| Filesystem and symlink resolution | available | Yes | Verifies that configured generated skill targets resolve to canonical `.agents/skills` and expose the new guidance. |
| `just` command hub | available | Yes | `just -l` and a negative invocation of `just architecture-check` produce observable operator evidence. |
| AgentSync status CLI | available | Yes | `pnpm dlx @dallay/agentsync status` is the non-mutating distribution check. |
| AgentSync apply CLI | available | Rejected | Already covered by the technical verification handoff; rerunning could mutate generated tooling paths, which QA must not change. |
| Backend/frontend technical runners | available | Rejected | Technical conformance is owned by `sdd-verify`; no product acceptance target would be exercised by duplicating those runs. |
| Browser / Playwright / Chrome | available as tooling | Rejected | No browser product target is in scope. |
| API/client requests | available as tooling | Rejected | No API target is in scope. |
| Data/persistence checks | available as tooling | Rejected | No persistence behavior is changed or exposed by this guidance-only change. |
| Accessibility/responsive checks | available as tooling | Rejected | No UI surface is changed. |
| Locale/internationalization checks | available as tooling | Rejected | No user-facing product copy or locale behavior is changed. |
| Unauthorized/security-flow checks | available as tooling | Rejected | No authentication, authorization, or protected product operation is in scope. |
| Dedicated architecture-governance acceptance analyzer | unavailable | No | No such analyzer or `just architecture-check` recipe exists; its absence is required by the current design. |

## Scenario Matrix

| ID | Capability | Acceptance scenario | Result | Evidence or reason |
|---|---|---|---|---|
| QA-01 | Filesystem and symlink resolution | An operator can reach the canonical `architecture-governance`, backend DDD, and frontend architecture skills through each configured generated skill target. | PASS | A runtime link-resolution check confirmed `.claude/skills`, `.cursor/skills`, `.codex/skills`, `.gemini/skills`, and `.opencode/skills` are symlinks resolving to `.agents/skills`; each target exposes all three relevant governance skill files. |
| QA-02 | AgentSync status CLI | After distribution is present, an operator can verify AgentSync is synchronized without generated-target drift. | PASS | `pnpm dlx @dallay/agentsync status` returned `Status: All good` and reported the configured instructions, commands, agents, and skill links as `OK`. Repeated status invocations produced the same healthy result. |
| QA-03 | `just` command hub | An operator can discover the existing verification recipes used by the governance guidance. | PASS | `just -l` listed `backend-test-fast`, `backend-check`, `frontend-lint`, `frontend-check`, `frontend-test`, `admin-test`, and `admin-check`, along with the repository command hub recipes. |
| QA-04 | `just` command hub | The deferred `just architecture-check` boundary rejects an attempt to run an unverified gate rather than silently running or requiring it. | PASS | `just architecture-check` returned `error: justfile does not contain recipe 'architecture-check'`. The `justfile` has no `architecture-check` recipe, matching the explicit deferred/unverified policy. |
| QA-05 | Repository/manual inspection | An operator can rely on an executable acceptance check to validate all five ARCH contract rows, their owner/scope/ADR/severity metadata, exceptions, and failure semantics. | NOT TESTED | The canonical skill visibly documents the five rows and traceability convention, but no executable governance analyzer exists. Under QA policy, static/manual inspection cannot be recorded as PASS. A future contract-lint or verified opt-in gate is required. |
| QA-06 | Repository/manual inspection | A repeated distribution/status check remains stable after the initial observation. | PASS | AgentSync status was run independently multiple times during QA and continued to report `Status: All good`; canonical generated skill links continued to resolve to `.agents/skills`. |
| QA-07 | Repository/manual inspection | An interrupted AgentSync apply can be resumed or rolled back without generated-target damage. | NOT TESTED | QA did not interrupt or repeat the mutating apply flow. The verification handoff records a successful prior apply and cleanup, but interruption/rollback behavior requires a disposable isolated worktree and an explicitly authorized mutation test. |
| QA-08 | Browser / Playwright / Chrome | A browser user can complete an acceptance flow for this change. | NOT TESTED | No browser application-under-test or user-facing product behavior is part of this documentation/agent-guidance change. |
| QA-09 | Accessibility/responsive | The changed product surface remains accessible and responsive. | NOT TESTED | No product UI surface is changed; there is no applicable browser target. |
| QA-10 | Locale/internationalization | The changed product surface behaves correctly across supported locales. | NOT TESTED | No product locale or user-facing copy behavior is changed. |
| QA-11 | Data/persistence | Guidance distribution or operator state persists correctly across a restart or new session. | NOT TESTED | No application persistence target exists. Local symlink state was inspected, but that is not product persistence acceptance. |
| QA-12 | Unauthorized/security flow | An unauthorized actor is prevented from performing a changed product operation. | NOT TESTED | No authentication or authorization operation is changed or exposed by the target. Repository file permissions were not treated as product security acceptance. |
| QA-13 | Exploratory/manual | An exploratory operator session finds no unanticipated behavior outside the deterministic checks. | NOT TESTED | QA performed focused deterministic checks only; there is no product target or interactive operator workflow to explore without inventing acceptance scope. |

## Untested Scope

- Scope: ARCH contract semantic traceability, approved-exception behavior, deliberate failure
  reporting, interrupted AgentSync apply/rollback, browser/UI behavior, API behavior, persistence,
  locale, accessibility, responsive behavior, and unauthorized product flows.
- Reason: This change contains canonical documentation and agent guidance only; no product target or
  executable architecture-governance analyzer is available. Static inspection cannot be promoted to
  an acceptance PASS, and the future `just architecture-check` is intentionally unverified and
  absent.
- Re-run prerequisite: Provide a clean isolated worktree and either (a) an executable contract
  linter/verified opt-in architecture-check that reports each ARCH contract and owner, or (b) a
  concrete operator acceptance harness for the affected distribution surface. For interrupted
  AgentSync testing, explicitly authorize a disposable-worktree mutation and rollback exercise.

## Findings

| ID | Severity | Scenario / location | Evidence | Status |
|---|---|---|---|---|
| QA-F-001 | P2 | QA-05; ARCH metadata and scope/contract traceability | The canonical skills document the five contracts and metadata, but no executable checker validates the semantic contract inventory or exception/failure mapping. | Open / deferred by design; a future gate or contract-lint proposal must provide executable evidence before claiming this acceptance scenario. |
| QA-F-002 | P3 | QA-07; AgentSync interruption and rollback | The mutating apply flow was not interrupted in QA. Existing verification evidence covers a successful apply and cleanup only. | Accepted limitation; rerun only in an isolated disposable worktree if rollback behavior becomes acceptance-relevant. |
| QA-F-003 | P3 | QA-08 through QA-12; product acceptance categories | There is no browser, API, persistence, locale, accessibility, responsive, or authorization product target for this guidance-only change. | Accepted documentation-scope limitation; no product acceptance is claimed. |
| QA-F-004 | P3 | Technical handoff warnings | `verify-report.md` carries pre-existing app lint warnings, an unavailable strict-TDD verifier module, and unrelated dirty-worktree isolation notes. These were not caused or changed by QA. | Acknowledged / non-blocking handoff warning; implementation verification remains `PASS WITH WARNINGS`. |

No `CRITICAL`, `P0`, or `P1` finding was observed. QA did not modify source code, product files,
DDD sweep files, `justfile`, CI, generated tooling paths, or unrelated files.

## Verdict

`NOT TESTED`

### Rationale

The independently exercised repository/operator paths passed: canonical AgentSync skill targets
resolve correctly, AgentSync status is healthy, the command hub exposes the expected baseline
recipes, and the unverified `just architecture-check` command is correctly absent. However, this
repository change has no application-under-test or product acceptance surface, and semantic ARCH
traceability is only documented rather than executable. Per the QA contract, those observations
cannot be converted into a product acceptance PASS, and the final QA verdict is therefore
`NOT TESTED` rather than fabricated acceptance.

Because this is explicitly documentation/agent-guidance scope with no production-code, dependency,
CI, or `justfile` change, the archive phase may evaluate the documentation-only exception with this
visible warning. That exception does not change the QA verdict and must not be interpreted as
product acceptance.

## Limitations and Handoff

- QA does not fix code or guidance findings; no source changes were made.
- Product acceptance is not claimed without a product target and observable product evidence.
- The `sdd-verify` handoff remains the source for technical tests/builds and reports
  `PASS WITH WARNINGS`; QA intentionally did not duplicate that matrix.
- Do not add or require `just architecture-check` as part of this phase. Its absence is an observed
  and expected boundary, not a failure.
- Implementation handoff: keep the canonical skills under `.agents/skills`, preserve AgentSync
  symlink distribution, and require a separate approved proposal before introducing an executable
  architecture gate or frontend analyzer.
- Archive handoff: `sdd-archive` may proceed only if its documentation/config-only exception policy
  explicitly accepts the visible `NOT TESTED` warning and no unresolved blocking findings remain.
