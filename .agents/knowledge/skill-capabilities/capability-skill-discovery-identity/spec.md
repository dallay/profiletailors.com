# Skill Discovery and Identity

## Requirements

- Each active skill lives in a top-level directory under `.agents/skills/`; references and assets
  remain inside their owning skill directory.
- The directory name matches the skill's frontmatter `name` for the current inventory.
- Each skill declares its discovery metadata in frontmatter. `.agents/skill-registry.md` is the
  hand-maintained index of current skill metadata.
- Authors update the registry during ordinary review when skills are added, renamed, removed, or
  their discovery metadata changes.

## Review practice

Check identity, required metadata, and relevant registry rows while reviewing a skill change. This
is a human review responsibility; there is no discovery-identity CI gate or registry generator.
The earlier deterministic implementation was removed by
[ADR-0026](../../../docs/architecture/adr/0026-remove-automated-skill-doctor.md).
