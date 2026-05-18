# Branch Protection — `main`

## Purpose

Protect the `main` branch from accidental force pushes, deletions, and unchecked merges. Every code change that lands there must be reviewed, tested, and up-to-date with the rest of the codebase.

## Current Configuration

Set via GitHub REST API (branch protection, not rulesets):

```bash
gh api repos/dallay/profiletailors.com/branches/main/protection --method PUT
```

Resulting protection:

| Setting | Value | Why |
|---|---|---|
| **Require branch up-to-date** | ✅ `strict: true` | PR must be synced with latest `main` before merge |
| **Required status checks** | Loose (no specific checks) | Any status that reports must pass; skipped jobs don't block |
| **Required approving reviews** | 1 | At least one human approval |
| **Last push approval** | ✅ | The person who pushed cannot approve their own PR |
| **Dismiss stale reviews** | ✅ | Outdated approvals are discarded when new commits arrive |
| **Force pushes** | ❌ Blocked | No force pushes allowed |
| **Branch deletion** | ❌ Blocked | Cannot delete `main` |
| **Enforce on admins** | ✅ | Protection applies even to repository admins |

## Merge Flow

```
PR created
    ↓
Update branch with latest main (rebase or merge)
    ↓
All status checks report → pass
    ↓
At least 1 approval (not from pusher)
    ↓
All review threads resolved
    ↓
✅ MERGE ALLOWED
```

## Loose vs Strict Status Checks

**Current: Loose** — no specific checks are listed.

This means:
- If any workflow job reports a status to the commit, it must pass (success).
- If a workflow job never reports a status (skipped, not triggered, or not run), it does **not** block merge.
- Skipped jobs due to path conditions do not block merge.

**Trade-off:**
- Pro: Path-aware CI jobs that skip for unrelated changes don't block merge.
- Con: If a job fails but never reports to the commit, merge could still be allowed.

**If you want strict:** add specific check names (job names from your workflows) so only those checks matter.

## Adding Specific Required Checks

To add specific checks, run:

```bash
gh api repos/dallay/profiletailors.com/branches/main/protection/required_status_checks \
  --method PUT \
  -H "Content-Type: application/json" \
  --input - <<'EOF'
{
  "strict": true,
  "contexts": [
    "Detekt Analysis",
    "security / gitleaks-pr",
    "security / semgrep-backend",
    "security / semgrep-frontend",
    "security / codeql-backend",
    "security / trivy-backend",
    "security / frontend-eslint-security"
  ]
}
EOF
```

Or via the web UI:
1. Go to **Settings → Branches → main → Edit**
2. Check **Require status checks to pass before merging**
3. Search and select the checks from the list

## Viewing Current Protection

```bash
gh api repos/dallay/profiletailors.com/branches/main/protection --jq '.'
```

## Updating Protection

The current protection was set via:

```bash
gh api repos/dallay/profiletailors.com/branches/main/protection --method PUT \
  -H "Content-Type: application/json" \
  --input - <<'EOF'
{
  "required_status_checks": { "strict": true, "contexts": [] },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_last_push_approval": true
  },
  "restrictions": { "users": [], "teams": [], "apps": [] }
}
EOF
```