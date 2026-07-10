# Candidate Decision Catalog

| ID            | Candidate decision                  | Current state | Evidence strength | Rationale known          | Contradictions                        | Proposed action |
|---------------|-------------------------------------|---------------|-------------------|--------------------------|---------------------------------------|-----------------|
| CANDIDATE-001 | **Modular Monolith Backend**        | Implemented   | High              | Yes                      | Minor (violation test disabled)       | Create ADR      |
| CANDIDATE-002 | **Hexagonal Architecture**          | Implemented   | High              | Yes                      | None                                  | Create ADR      |
| CANDIDATE-003 | **Reactive Stack (WebFlux/R2DBC)**  | Implemented   | High              | Yes                      | None                                  | Create ADR      |
| CANDIDATE-004 | **CQRS and Mediator Pattern**       | Implemented   | High              | Yes                      | CommandWithResult usage               | Create ADR      |
| CANDIDATE-005 | **Prefixed Backend-Generated IDs**  | Implemented   | High              | Likely (observability)   | Documentation drift (target UUID/PUT) | Create ADR      |
| CANDIDATE-006 | **Resource Creation via POST**      | Implemented   | High              | Likely (REST convention) | Documentation drift (target PUT)      | Create ADR      |
| CANDIDATE-007 | **Astro & Vue Frontend Split**      | Implemented   | High              | Yes                      | Documentation drift (mentions React)  | Create ADR      |
| CANDIDATE-008 | **Application-Level Multi-tenancy** | Implemented   | High              | Yes                      | None (RLS not used)                   | Create ADR      |
| CANDIDATE-009 | **JWT + HttpOnly Cookie Auth**      | Implemented   | High              | Yes                      | None                                  | Create ADR      |
| CANDIDATE-010 | **Shared Kernel Strategy**          | Implemented   | High              | Yes                      | None                                  | Create ADR      |

## Decision Groups

### 1. System Structure

- CANDIDATE-001 (Modular Monolith)
- CANDIDATE-007 (Frontend Split)

### 2. Backend Architecture

- CANDIDATE-002 (Hexagonal Architecture)
- CANDIDATE-003 (Reactive Stack)
- CANDIDATE-004 (CQRS/Mediator)
- CANDIDATE-010 (Shared Kernel)

### 3. API & Creation Design

- CANDIDATE-005 (Prefixed IDs)
- CANDIDATE-006 (POST Creation)

### 4. Security & Tenancy

- CANDIDATE-008 (Multi-tenancy)
- CANDIDATE-009 (Auth Flow)
