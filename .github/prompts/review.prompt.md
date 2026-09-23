# Spec-Aware Review Command

> Non-mutating review that reads specifications and reports findings without modifying code.

## When to Use

Use this prompt when reviewing a change against its OpenSpec specification:

```
@review.prompt.md
```

Or when asking Copilot to review code:

```
Review the changes in this diff against the specification at [path to spec].
Report findings without modifying any code.
```

---

## Review Workflow

### 1. Locate Specification

Find the relevant OpenSpec specification for the change:

```bash
# For archived changes
openspec/archive/<date>-<name>/specs/

# For active changes
openspec/changes/<name>/specs/

# For main specs
openspec/specs/
```

### 2. Read Specification

Read the specification completely, noting:

- **Scope**: What is in scope, what is out
- **Acceptance criteria**: How success is measured
- **Key decisions**: Architecture choices, trade-offs

### 3. Review Implementation

Compare the implementation against the specification:

| Check | Question |
|-------|----------|
| Completeness | Does the code implement all spec requirements? |
| Correctness | Does the code match spec behavior exactly? |
| Boundaries | Are scope boundaries respected? |
| Tests | Do tests verify spec criteria? |
| Quality | Does code follow repository patterns? |

### 4. Report Findings

Structure findings using the 4R model:

---

## Verdict Format

```
## Verdict

**OVERALL**: [PASS | WARNINGS | FAIL]

## 4R Findings

### Risk
- [Finding] — [File:Line] — [Spec violation]
- ...

### Readability
- [Finding] — [File:Line] — [Pattern violation]
- ...

### Reliability
- [Finding] — [File:Line] — [Edge case]
- ...

### Resilience
- [Finding] — [File:Line] — [Concurrency/transaction concern]
- ...

## Summary

| Category | Count | Severity |
|----------|-------|----------|
| Risk | N | [HIGH/MEDIUM/LOW] |
| Readability | N | [HIGH/MEDIUM/LOW] |
| Reliability | N | [HIGH/MEDIUM/LOW] |
| Resilience | N | [HIGH/MEDIUM/LOW] |

## Recommendations

1. [Action for human to decide]
2. [Action for human to decide]
3. ...

## Evidence

- Specification: [path]
- Implementation: [files reviewed]
- Tests: [test files reviewed]
```

---

## Verdict Definitions

### PASS

- No findings, OR
- Only LOW severity findings, OR
- All findings are suggestions (not required changes)

### WARNINGS

- At least one MEDIUM severity finding, OR
- Multiple LOW severity findings that together indicate a pattern issue

### FAIL

- At least one HIGH severity finding, OR
- A finding that violates a spec requirement

---

## Severity Definitions

| Severity | Definition | Action |
|----------|------------|--------|
| **HIGH** | Security vulnerability, data corruption risk, spec violation | Must fix before merge |
| **MEDIUM** | Correctness issue, missing edge case, pattern violation | Should fix before merge |
| **LOW** | Style inconsistency, minor improvement opportunity | Consider fixing, not blocking |

---

## Rules for This Review

1. **DO NOT modify any code** — This is a read-only review
2. **DO report exact file and line** — Use `[File:Line]` format
3. **DO reference the spec** — Quote relevant spec text
4. **DO leave decisions to human** — Never say "I fixed this"
5. **DO be specific** — Avoid generic statements like "improve error handling"
6. **DO verify tests** — Check that tests cover spec criteria

---

## Example Finding

**Finding**: Missing null check for invitation ID

- **File**: `server/smp/src/main/kotlin/com/profiletailors/smp/identity/application/AcceptInvitationHandler.kt:47`
- **Spec violation**: "The handler MUST return 404 when invitation does not exist" (from `openspec/specs/invitations/spec.md`)
- **Risk**: HIGH — Could throw NullPointerException if invitation ID is malformed
- **Recommendation**: Add null check and return 404 with `INVITATION_NOT_FOUND` code

---

## Human Gate

After receiving this review:

1. **Read all findings** carefully
2. **Classify by severity**: Is this HIGH/MEDIUM/LOW?
3. **Decide on corrections**: Do you agree with each finding?
4. **Implement corrections**: Make changes yourself or delegate
5. **Re-review if needed**: Run this prompt again after corrections

**This review does NOT approve or reject the change. Only a human can make that decision.**

---

## Integration with OpenSpec

Use after `sdd-verify` to get an additional layer of review:

```bash
# After verification report
# Human reviews the report
# Then runs this review command on the implementation
```

The review findings can be attached to the OpenSpec change as evidence.

---

## Related Commands

- `@sdd-verify` — Technical verification against specs
- `@4r-review` — Full 4R quality review
- `@just backend-check` — Run automated quality gates
