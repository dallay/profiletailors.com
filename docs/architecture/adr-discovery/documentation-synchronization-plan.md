# Documentation Synchronization Plan

Following the approval of the ADRs, the following existing documents must be updated to align with the canonical architectural state.

## Required Updates

| Document | Target Section | Change Required | Related ADR |
|----------|----------------|-----------------|-------------|
| `docs/architecture/README.md` | Current Status | Change "Web application (React)" to "Web application (Vue 3)". | ADR-0007 |
| `docs/architecture/c4/02-container.md` | Diagram / Text | Update Web App container description to Vue 3 / Pinia. | ADR-0007 |
| `docs/architecture/c4/04-code.md` | Identifier Strategy | Update description to mention prefixed-UUIDs (`varchar(64)`). | ADR-0005 |
| `docs/architecture/README.md` | Architecture Principles | Link to ADR-0001 (Modular Monolith) and ADR-0002 (Hexagonal). | ADR-0001, ADR-0002 |
| `AGENTS.md` | Backend Architecture | Explicitly mention the custom `@Service` marker rule. | ADR-0002 |

## Process
1. Approval: Obtain confirmation for this synchronization plan.
2. Update: Apply changes to documentation files.
3. Verification: Ensure all cross-links between ADRs and C4 diagrams are functional.
