# Documentation Drift Report

| Document | Current claim | Repository reality | Related Candidate | Required action |
|----------|---------------|--------------------|-------------------|-----------------|
| `docs/architecture/README.md` | Web application (React) | Web application (Vue 3) | CANDIDATE-007 | Update document to reflect Vue. |
| `docs/architecture/c4/02-container.md` | Web App: React | Web App: Vue 3 | CANDIDATE-007 | Update diagram/text to reflect Vue. |
| `docs/architecture/README.md` (Verification example) | Creation endpoints use `PUT /{resource}/{id}` | Creation endpoints use `POST /resource` | CANDIDATE-006 | Clarify if PUT-based creation is a target or if POST is the accepted standard. |
| `docs/architecture/c4/04-code.md` | UUID v4 asset identifier... MUST NOT be sequential | Asset IDs use prefixes like `user-` and `ws-` followed by UUID. | CANDIDATE-005 | Update convention to match prefixed-UUID reality. |
| `server/smp/src/test/.../ModularityVerificationTest.kt` | Modulith enforces boundaries | Test is disabled due to violation: `authorization -> audit :: application` | CANDIDATE-001 | Resolve violation or update ADR to acknowledge permitted exceptions. |
