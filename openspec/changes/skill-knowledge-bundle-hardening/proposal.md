# Proposal: Skill Knowledge Bundle Hardening

## Intent

Make the Profile Tailors agent knowledge bundle internally coherent, aligned with repository
architecture and manifests, and mechanically verifiable as complete bundles rather than isolated
`SKILL.md` files.

This is a technical governance initiative. It does not introduce or modify a product capability, so
it intentionally has no capability delta under `openspec/specs/`.

## Scope

### In Scope

- Restore referential integrity after flattening `.agents/skills/`.
- Align architecture guidance around domain-owned repository and gateway ports.
- Make active Spring guidance consistently WebFlux, coroutines, R2DBC, and MockK based.
- Align Vue, general web, and design guidance with repository packages and precedence.
- Validate complete skill bundles, family semantics, local paths, and manifest-owned versions.
- Add deterministic drift-injection tests and a small semantic scenario corpus.

### Out of Scope

- Rewriting archived OpenSpec history solely to update old paths.
- Changing product architecture or production dependencies.
- Refactoring unrelated application source.
- Replacing deterministic checks with language-model review.
- Making generic skills authoritative over repository policy or specialized local skills.

## Delivery Strategy

The initiative is delivered as five independently reviewable pull requests:

1. Knowledge graph integrity: flattened paths, ADR-0002, Spring metadata, and registry.
2. Spring semantic hardening: recursive content cleanup and the flattened-layout Spring validator.
3. Frontend and UI truth: Vue, best practices, modern web, and design activation contracts.
4. Skill Doctor v2: bundle-recursive, path-aware, manifest-aware, family-specific validation.
5. Semantic acceptance: scenario corpus, contextual audit, final registry, and independent report.

Each pull request must preserve the boundaries of its slice. The change remains active until all five
slices pass their deterministic gates and an independent final audit reaches the same conclusion.

## Affected Areas

| Area | Impact |
|---|---|
| `.agents/` | Skill instructions, references, examples, registry, and governance tools |
| `docs/architecture/adr/` | Canonical architecture wording and links |
| `.gitleaks.toml` and CI governance | Bundle-consumer paths and deterministic gates |
| `openspec/changes/skill-knowledge-bundle-hardening/**` | Cross-PR initiative traceability |

## Risks

| Risk | Mitigation |
|---|---|
| A broad rewrite becomes unreviewable | Keep the five planned pull requests isolated by concern |
| Validators encode the current incorrect state | Correct normative guidance before adding enforcement |
| Root files pass while references remain contradictory | Treat every skill directory as one validation unit |
| Generic guidance overrides repository policy | Encode and test the documented source-of-truth order |

## Success Criteria

- [ ] All active references resolve against the flattened skill inventory.
- [ ] Domain owns repository and gateway ports in every normative architecture source.
- [ ] Active Spring bundles match the repository's reactive stack outside marked migration material.
- [ ] Frontend and design skills use real packages, feature boundaries, and design precedence.
- [ ] Skill Doctor validates complete bundles and detects deliberate drift fixtures.
- [ ] Semantic scenarios consistently produce the repository's canonical decisions.
- [ ] Independent final verification passes before this change is archived.
