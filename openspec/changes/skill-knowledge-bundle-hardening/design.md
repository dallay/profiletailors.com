# Design: Skill Knowledge Bundle Hardening

## Source-of-Truth Order

The bundle follows this precedence:

1. Repository source tree, manifests, and executable architecture tests.
2. `.agents/AGENTS.md`, `.agents/DESIGN.md`, applicable `PRODUCT.md` files, and accepted ADRs.
3. Project-specific architecture skills.
4. Technology-specific skills.
5. Generic or upstream-adapted skills.
6. References and examples.

A lower-priority source may specialize but must not contradict a higher-priority source. References,
examples, scripts, and declared knowledge assets are governed parts of a skill bundle.

## Validation Architecture

Validation is layered so deterministic checks remain the owners of deterministic facts:

1. Discover top-level skills and compare them with the generated registry.
2. Validate bundle structure, metadata, links, and statically resolvable repository paths.
3. Apply family validators, beginning with Spring reactive compatibility.
4. Run comment and repository policy checks.
5. Evaluate only irreducibly contextual contradictions with semantic scenarios or audit.

Family behavior is selected from normalized skill metadata. Spring skills use
`category: backend-platform` and `family: spring-boot`.

## Architecture Contract

Backend examples and instructions use this placement:

```text
<feature>/domain/<Feature>Repository.kt
<feature>/application/<use-case files>
<feature>/infrastructure/<technology><Feature>Repository.kt
```

The domain defines repository and gateway ports. Application commands, queries, handlers, and
services consume those ports. Infrastructure adapters implement them. HTTP adapters reach
persistence only through an application use case.

## Migration Boundaries

Historical material under `openspec/changes/archive/**` is excluded unless a runtime tool consumes
it. Incompatible Spring material may remain only in explicitly named migration content whose
boundaries can be recognized by the validator. A disclaimer around active incompatible guidance is
not an exclusion mechanism.

## Pull Request Boundaries

- PR 1 changes truth and references but does not absorb recursive Spring cleanup.
- PR 2 cleans Spring bundles and replaces the obsolete Spring scrubber.
- PR 3 reconciles frontend and design activation contracts.
- PR 4 generalizes complete-bundle enforcement in Skill Doctor and CI.
- PR 5 supplies semantic acceptance and final independent verification.

This ordering prevents validators from being tailored to known-invalid content.
