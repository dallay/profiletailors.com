# Code scanning configuration

## Goal

Restore a valid default-branch CodeQL reporting path without weakening security checks or suppressing findings.

## Route

Direct inline Plan Mode change. The evidence points to a bounded GitHub Actions configuration issue plus a separate dependency finding; no product contract or OpenSpec cycle is required.

## Scope

1. Add a `push` trigger for `main` to the custom CodeQL-containing security workflow.
2. Keep scheduled deep scans and existing pinned actions intact.
3. Preserve the existing Security Deep job set; the new main push hook intentionally refreshes all deep security reports together.
4. Validate YAML, inspect the diff, and report the separate OWASP/Kotlin vulnerability honestly.

## Acceptance evidence

- CodeQL workflow declares a `push` hook for the default branch.
- No action pin, permission, scan scope, or vulnerability suppression is weakened.
- Workflow YAML parses and `git diff --check` passes.
- Existing worktree changes remain untouched.
