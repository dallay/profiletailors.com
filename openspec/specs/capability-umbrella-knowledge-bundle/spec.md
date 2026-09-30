# Knowledge Bundle Maintenance

## Authority

- `.agents/AGENTS.md`, `.agents/DESIGN.md`, applicable `PRODUCT.md` files, current repository
  configuration, and accepted ADRs own the claims agent guidance makes.
- Skills are living documentation. Keep their content and references accurate through normal
  authoring and review.
- `.agents/skill-registry.md` is hand-maintained and is updated when skill identity or discovery
  metadata changes.

## Requirements

- Skill examples must not contradict current source, package manifests, architecture, product
  contracts, or the applicable design system.
- Historical OpenSpec changes and archived reviews remain records of prior decisions; they do not
  override a later accepted ADR.
- Skill-format scripts, deterministic contamination scans, generated registries, and dedicated CI
  gates are not required. ADR-0026 supersedes the prior automated-governance design.
- A skill's scope and activation rules must defer to a more specific local skill when one owns the
  task.

## Review

Review changed skills and directly affected references together. Use `.agents/agents/skill-doctor.md`
only when an optional semantic review would help. Do not require an automated validator or a
five-pull-request delivery sequence for content maintenance.
