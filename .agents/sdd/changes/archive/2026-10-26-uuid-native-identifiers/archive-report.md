# Archive Report: Native UUID Identifiers

## Overview

Archived local candidate on 2026-10-26. Independent [verification](verify-report.md) and
[acceptance review](qa-report.md) both report **PASS WITH WARNINGS**. No unresolved CRITICAL/P0/P1
finding or acceptance-relevant local blocker was identified. This disposition does not authorize
production SQL, deployment, commit, push or publication.

## Specification Synchronization

The five approved deltas were merged into current main specifications in English:

- [Publishing](../../../specs/publishing/spec.md): two added requirements, nine scenarios.
- [Publishing shortlinks](../../../specs/publishing-shortlinks/spec.md): two added requirements,
  four scenarios.
- [Media library](../../../specs/media-library/spec.md): two added requirements, six scenarios.
- [Consent scopes](../../../specs/consent-scopes/spec.md): new specification with five requirements,
  eight scenarios from the approved contract.
- [Governance consent API](../../../specs/governance-consent-api/spec.md): one added identity/scope
  requirement with three scenarios; existing nominal principal/workspace examples now use UUIDs.
  Endpoints, permissions, tenant response shape, statuses and idempotency remain unchanged.

All pre-existing requirement sections are preserved except the explicitly superseded nominal
identifier examples. Provider/opaque exclusions and ADR-0028 product deferrals remain unchanged.
No unrelated main specification or historical archive was rewritten.

Rollout wording is resolved only to the selected conditional coordinated stopped-writer plan:
raw UUIDs within v1 after conversion, no legacy writers on the converted schema, and release
blocked pending a separate plan if coordination cannot be approved. No maintenance window,
rolling compatibility, fallback implementation or production approval is inferred. Recovery must
account for new writes; automatic lossless reversal is not promised.

## Closure and Evidence

All **21/21** local tasks are reconciled. The phase DAG closes through
`apply → verify → qa → archive`, with `next: none`. The full active folder transfers to
`.agents/sdd/changes/archive/2026-10-26-uuid-native-identifiers/` without merging or overwriting
an existing archive. State is prepared before transfer and frozen afterward.

Both reports, all five original deltas, proposal, design, inventory, apply progress and retained
`evidence/local-acceptance.json` remain intact. Historical Spanish planning artifacts are preserved
as audit evidence, not rewritten as new canonical prose. Earlier pending/failure wording remains
historical and does not override the final reports or reconciled state. External temporary evidence
references remain external; this transfer does not claim to copy raw external logs or traces.

The technical report retains **25/30 compliant, 5/30 partial, zero failing and zero wholly
untested scenarios**. Four partial rows concern operational rehearsal outside local scope; one
concerns dedicated malformed-shortlinks-header HTTP rejection/no-access coverage. QA accepts the
scoped candidate using actual browser, packaged-backend and disposable-PostgreSQL evidence and
independent evidence review. No runtime tests or builds were rerun for archival work.

## Remaining Release Conditions and Warnings

- Historical strict-TDD chronology is incomplete and is not certified retroactively.
- Existing scheduler/media omissions, two documentation-test skips and toolchain warnings remain.
- The dedicated malformed-shortlinks HTTP negative-path coverage gap remains a follow-up warning.
- Temporary external evidence retention is limited; the retained sanitized summary is not a raw trace.
- Legal/operational approval, representative production backup/restore, measured timing,
  deployed-consumer coordination and stopped-writer verification remain separate release conditions.
- Remote CI/security/Sonar, live Unsplash, production SQL, production smoke and deployment are not
  established by local acceptance. Production SQL, deployment, commit and push remain unperformed.

## Documentation Impact

Owned ADR-0029/0030, runbook and RPI references follow the archive location. C4 and documentation
index references continue to resolve through their existing ADR/runbook links. Product files,
API implementation, SQL, dependencies, CI configuration and runtime source require no further
change for this metadata-only archive phase and are not modified here.

## Archival Validation

Before transfer, projected archive-relative validation checked **27 Markdown files and 167 local
links**, with zero missing targets. Requirement-preservation comparison passed for all four existing
main specs; only the approved governance example substitutions changed existing prose. The merge
adds **12 requirements and 30 scenarios** across five capabilities. Checklist validation confirms
21 checked tasks and zero unchecked tasks. `git diff --check` passed.

Scoped Markdown lint checked 11 files using the repository configuration via standard input:
10 passed without findings; media-library retains its identical pre-existing MD028 finding at
line 7. No new lint finding or configuration exception was introduced. Runtime tests/builds,
remote checks and production actions were not run by the archival writer.

Transfer validation compares every prepared file's bytes and every original artifact retained
without edits, including both reports, the five deltas and local acceptance evidence. The archive
target must be absent before transfer. The source folder must be absent afterward. Validation
after transfer is read-only; archived history is not rewritten.
