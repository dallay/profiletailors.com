# Skill Version Guidance

## Requirements

- Skills that describe an existing Profile Tailors dependency direct authors to its owning
  manifest, such as `gradle/libs.versions.toml` or the relevant `package.json`.
- Skills do not copy exact dependency versions from the repository into reusable instructions.
- A greenfield or upgrade task checks current compatibility from the dependency's authoritative
  release documentation and the target runtime instead of copying a stale example.
- Metadata fields that record when a local skill was reviewed use a real review date.

## Review practice

Check version claims during ordinary review of the skill content. There is no regex rule, literal
version scanner, script, or CI gate for skill versions. Framework and language identity statements
such as “Vue 3” remain valid when they describe the product stack rather than pinning a dependency.

The former automated-gate requirement is withdrawn by
[ADR-0026](../../../../docs/architecture/adr/0026-remove-automated-skill-doctor.md).
