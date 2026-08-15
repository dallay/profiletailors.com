# Documentation Synchronization Plan

Following the approval of the ADRs, the following existing documents must be updated to align with
the canonical architectural state.

## Required Updates

| Document                               | Target Section          | Change Required                                                | Related ADR        | Status |
|----------------------------------------|-------------------------|----------------------------------------------------------------|--------------------|--------|
| `docs/architecture/README.md`          | Current Status          | Change "Web application (React)" to "Web application (Vue 3)". | ADR-0007           | ✅ Applied (2026-08-14) |
| `docs/architecture/c4/02-container.md` | Diagram / Text          | Update Web App container description to Vue 3 / Pinia.         | ADR-0007           | ✅ Applied (2026-08-14) |
| `docs/architecture/c4/04-code.md`      | Identifier Strategy     | Update description to mention prefixed-UUIDs (`varchar(64)`).  | ADR-0005           | ✅ Applied (2026-08-14) |
| `docs/architecture/README.md`          | Architecture Principles | Link to ADR-0001 (Modular Monolith) and ADR-0002 (Hexagonal).  | ADR-0001, ADR-0002 | ✅ Applied (2026-08-14) |
| `docs/architecture/c4/02-container.md` | Deployment & Messaging  | Remove Kubernetes/Cloud Run & RabbitMQ/Kafka in favor of Swarm & Reactor channels. | CANDIDATE-011, CANDIDATE-012 | ✅ Applied (2026-08-14) |
| `docs/architecture/c4/03-component.md` | Bounded Contexts        | Document all 17 bounded contexts in `server/smp/`.             | CANDIDATE-013      | ✅ Applied (2026-08-14) |
| `docs/architecture/shared/dependencies.md`| Shared Kernel Modules| Document all 11 shared modules under `shared/`.               | ADR-0010           | ✅ Applied (2026-08-14) |
| `AGENTS.md`                            | Backend Architecture    | Explicitly mention the custom `@Service` marker rule.          | ADR-0002           | ✅ Applied (2026-08-14) |

## Process

1. Approval: Obtain confirmation for this synchronization plan.
2. Update: Apply changes to documentation files.
3. Verification: Ensure all cross-links between ADRs and C4 diagrams are functional.
