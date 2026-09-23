# AI-Assisted Engineering — Change Evidence Index

> This document indexes the three completed changes that demonstrate the spec-driven workflow.
> Each change traces from Linear ticket through the full OpenSpec lifecycle.

---

## Change 1: Invitation Registration Flow (Archived)

**Status**: ✅ **ARCHIVED** — Full lifecycle complete

### Linear Issue

- **Issue**: `DALLAY-567`
- **Link**: Linear issue tracker (requires Linear MCP or web access)

### OpenSpec Change

- **Path**: `../../openspec/changes/archive/2026-09-20-dallay-567-invitation-registration-flow`
- **Type**: Delta spec for invitations capability

### Artifacts

| Phase | File | Status |
|-------|------|--------|
| Proposal | `proposal.md` | ✅ Complete |
| Design | `design.md` | ✅ Complete |
| Tasks | `tasks.md` | ✅ Complete |
| Apply | `apply-progress.md` | ✅ Complete |
| Verify | `verify-report.md` | ✅ PASS |
| QA | `qa-report.md` | ✅ PASS |
| Archive | `state.yaml` | ✅ `current_phase: archive` |

### Implementation Evidence

- **Branch**: `feat/dallay-567-invitation-evidence`
- **Commits**: Tracked via Linear issue
- **Tests**: 36/36 Playwright (Chromium, Firefox, Mobile Chrome), 1,729/1,729 Vitest, 24/24 BDD fast
- **Quality gates**: Detekt, Spotless, `git diff --check` — all PASS

### Key Verdict

- **Verification**: `PASS` — all 12 tasks complete
- **QA**: `PASS` — 5/5 scenarios (QA-06..QA-10), manual acceptance 4x PASS
- **Archive date**: 2026-09-20

### Workflow Trace

```
Linear DALLAY-567
    ↓
sdd-propose (proposal.md)
    ↓ Human Gate 1: scope, acceptance criteria ✓
sdd-spec (specs/invitations/)
    ↓
sdd-design (design.md)
    ↓
sdd-tasks (tasks.md)
    ↓
sdd-apply (TDD RED → GREEN → REFACTOR)
    ↓
sdd-verify (verify-report.md)
    ↓ Human Gate 2: review findings ✓
sdd-qa (qa-report.md)
    ↓
sdd-archive (state.yaml updated)
    ↓
Linear issue closed
```

---

## Change 2: Direct Invitation FK Fix

**Status**: 🔄 **NEARLY COMPLETE** — Ready for archive after QA

### Linear Issue

- **Issue**: `hotfix-direct-invitation`
- **Reference**: `openspec/changes/hotfix-direct-invitation-issued-by-fk/`

### OpenSpec Change

- **Path**: `openspec/changes/hotfix-direct-invitation-issued-by-fk/`

### Artifacts

| Phase | File | Status |
|-------|------|--------|
| Proposal | `proposal.md` | ✅ Complete |
| Tasks | `tasks.md` | ✅ Complete |
| Apply | `apply-progress.md` | ✅ Complete |
| Verify | `verify-report.md` | ✅ PASS |
| QA | — | Pending (not yet run) |

### Implementation Evidence

- **Focus**: Fix `issuedBy` foreign key constraint for direct invitations
- **Approach**: Writers via `PlatformPrincipalIds.fromUuid` with anti-double-prefix guard
- **Tests**: 37 unit tests, 247/247 BDD fast (incl. invitations-direct 11/11)
- **Quality gates**: Detekt + Spotless + `git diff --check` — all PASS
- **State**: `current_phase: verify` — next is `qa`

### Key Findings

The verify report confirms:

- Zero migrations required
- InvitationId untouched
- All writers properly gated through `PlatformPrincipalIds.fromUuid`
- No production changes until QA is complete

### Workflow Trace

```
Linear ticket (hotfix)
    ↓
sdd-propose (proposal.md)
    ↓ Human Gate 1 ✓
sdd-tasks (tasks.md)
    ↓
sdd-apply (implementation)
    ↓
sdd-verify (verify-report.md) ✓ COMPLETE
    ↓ Human Gate 2: pending QA
sdd-qa → (qa-report.md)
    ↓
sdd-archive → state.yaml updated
    ↓
Linear issue closed
```

---

## Change 3: Private Beta Launch Readiness

**Status**: ⚠️ **QA BLOCKED** — Technical verification complete, acceptance testing pending

### Linear Issues

- **Primary**: `DALLAY-555` (publishing controls)
- **Secondary**: `DALLAY-557`

### OpenSpec Change

- **Path**: `openspec/changes/private-beta-launch-readiness/`

### Artifacts

| Phase | File | Status |
|-------|------|--------|
| Explore | Implicit in phases | ✅ |
| Proposal | `proposal.md` | ✅ Complete |
| Spec | `specs/` | ✅ Complete |
| Design | `design.md` | ✅ Complete |
| Tasks | `tasks.md` | ✅ Complete |
| Apply | `apply-progress.md` | ✅ Unit 1 & 2 complete |
| Verify | `verify-report.md` | ✅ PASS WITH WARNINGS |
| QA | `qa-report.md` | ⚠️ BLOCKED |

### Implementation Evidence

- **Units completed**: 2 (activation/invitation core + publishing controls)
- **Quality gates**: BDD fast, backend suite, Detekt, Spotless, `git diff --check` — all PASS
- **Critical fixes verified**: Provider payload redaction, stale-claim operation identity, typed unknown-exception classification, list-publications diagnostic exposure

### Blockers

The QA report documents:

1. **BLOCKED**: Managed-beta acceptance evidence not available
2. **WARNING**: Production is reachable but no approved mutation permissions
3. **WARNING**: Deployed v0.4.1 image is pre-change/mismatched release

### Workflow Trace

```
Linear DALLAY-555/557
    ↓
sdd-propose (proposal.md)
    ↓ Human Gate 1 ✓
sdd-spec (specs/)
    ↓
sdd-design (design.md)
    ↓
sdd-tasks (tasks.md)
    ↓
sdd-apply (Unit 1: activation/invitation core)
    ↓
sdd-apply (Unit 2: publishing controls)
    ↓
sdd-verify (verify-report.md) ✓ PASS WITH WARNINGS
    ↓ Human Gate 2: blocked by acceptance QA
sdd-qa → BLOCKED (awaiting managed-beta evidence)
    ↓
sdd-archive → (pending)
    ↓
Linear issue → (pending)
```

---

## Summary: Evidence Completeness

| Criterion | Change 1 | Change 2 | Change 3 |
|-----------|:--------:|:--------:|:--------:|
| Linear issue traceable | ✅ | ✅ | ✅ |
| Proposal | ✅ | ✅ | ✅ |
| Design | ✅ | N/A (hotfix) | ✅ |
| Tasks | ✅ | ✅ | ✅ |
| Implementation | ✅ | ✅ | ✅ |
| Verification | ✅ PASS | ✅ PASS | ✅ PASS W/ WARNINGS |
| QA | ✅ PASS | 🔄 Pending | ⚠️ BLOCKED |
| Archive | ✅ | 🔄 Pending | 🔄 Pending |

### Evidence Suitability for Exercise

For the purpose of demonstrating the **workflow** (not the product), these changes provide:

1. **Change 1** — Full demonstration of complete lifecycle
2. **Change 2** — Demonstration of hotfix/rapid response workflow
3. **Change 3** — Demonstration of human gate (QA blocked) and honest documentation of blockers

The exercise requires evidence of "Ticket → spec → implementation → review → archive" which Change 1 fully satisfies.

---

## How to Navigate These Changes

For evaluators:

1. **Start with Change 1** — It has the cleanest complete lifecycle
2. **Read `state.yaml`** in each change to understand phase status
3. **Check `verify-report.md`** for technical verification evidence
4. **Check `qa-report.md`** for acceptance evidence
5. **Follow the phase DAG** documented in `openspec/config.yaml`

---

## Linear Integration

All changes are linked to Linear issues via the `linear_issue` field in `state.yaml`:

```yaml
linear_issue: DALLAY-567
```

The Linear MCP configuration in `.mcp.json` enables agents to:

- Read issue details
- Update issue status
- Add comments
- Create attachments

See `docs/ai-engineering/SETUP.md` for MCP authentication.
