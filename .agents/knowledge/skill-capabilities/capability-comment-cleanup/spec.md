# Comment Policy

## Requirement

Repository code and agent-authored guidance follow the zero-comment policy in `.agents/AGENTS.md`.
Allowed comments are limited to SPDX license headers in the named license files, interpreter
shebangs, and markers emitted by approved generators. Express intent through names, types,
structure, and tests.

## Review practice

Apply the policy during ordinary review of changed files. Do not add a comment scanner, allowlist,
test suite, generated report, or dedicated CI workflow for skill comments. The retired scanner
requirements are superseded by [ADR-0026](../../../../docs/architecture/adr/0026-remove-automated-skill-doctor.md).
