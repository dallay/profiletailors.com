# Capability Umbrella — Knowledge Bundle Remediation

## Purpose

Agrupa las nueve capabilities hijas (`skill-discovery-identity`,
`backend-semantics`, `external-contamination`, `playwright-rebuild`,
`ui-governance-precedence`, `version-policy`, `modern-best-practices`,
`comment-cleanup`, `automated-skill-doctor`) en un sistema coherente que
reconcilia `.agents/AGENTS.md`, `.agents/skills/**`, los ADRs,
`.agents/DESIGN.md`, PRODUCT.md y los gates de CI con el contrato vivo del
monorepo `profiletailors.com`. Esta umbrella declara las autoridades y
restricciones transversales que aplican a todas las capabilities hijas.

## Authority

- ADR-0002 (`docs/architecture/adr/0002-adhere-to-hexagonal-architecture.md`)
  enmendado por la decisión G-1 de este change.
- ADR-NNN (nuevo, creado por `capability-automated-skill-doctor`)
  para taxonomía canónica de skills + gates CI, decisión G-2.
- Governance decisions G-1..G-4 declaradas en `state.yaml` y cerradas en
  la fase `sdd-propose`.
- Orden de cierre declarado en REQ-KB-UMBRELLA-003 (declarado por
  `capability-automated-skill-doctor` debe cerrar antes de que
  `capability-modern-best-practices` pueda implementar verificación
  transversal).

## Requirements

| REQ-ID             | Statement                                                                                                                 |
|--------------------|---------------------------------------------------------------------------------------------------------------------------|
| REQ-KB-UMBRELLA-001| Las governance decisions G-1..G-4 son fuente de verdad autoritativa para esta remediación; no se renegocean en apply.   |
| REQ-KB-UMBRELLA-002| Cada capability hija declara explícitamente a qué bloque P0-A/P0-B/P0-C/P0-D/P0-E/P1-A/P1-B/P1-C/P1-D pertenece.        |
| REQ-KB-UMBRELLA-003| `capability-automated-skill-doctor` es dependencia dura de `capability-modern-best-practices`; esta última no cierra sin el gate del doctor activo. |
| REQ-KB-UMBRELLA-004| Ningún cambio de esta umbrella puede tocar las secciones "Operating Contract" (1-6) ni "Static Analysis and Linter Compliance" de `.agents/AGENTS.md`; se preservan tal cual. |
| REQ-KB-UMBRELLA-005| Cada capability hija modifica exclusivamente los archivos listados en su propia sección "Scope" y los registros en `.agents/skill-registry.md` se regeneran vía `sdd-init` al final. |
| REQ-KB-UMBRELLA-006| Las capabilities que tocan código fuente compartido (Spring scrub, Playwright rebuild) terminan su sección con un escenario "WHEN a CI gate fails THEN the change SHALL block the merge". |

## Traceability

| Block  | Capability                                            | Governance | ADR                                       |
|--------|-------------------------------------------------------|------------|-------------------------------------------|
| P0-A   | capability-skill-discovery-identity                    | G-2        | ADR-NNN (nuevo)                           |
| P0-B   | capability-backend-semantics                          | G-1        | ADR-0002 (enmendado)                      |
| P0-C   | capability-external-contamination                     | —          | —                                         |
| P0-D   | capability-playwright-rebuild                         | —          | —                                         |
| P0-E   | capability-ui-governance-precedence                   | G-4        | —                                         |
| P1-A   | capability-version-policy                             | —          | —                                         |
| P1-B   | capability-modern-best-practices                      | G-2 (gate) | ADR-NNN (nuevo)                           |
| P1-C   | capability-comment-cleanup                            | G-3        | —                                         |
| P1-D   | capability-automated-skill-doctor                     | G-2        | ADR-NNN (nuevo)                           |

## Scenarios

### Scenario: Governance decisions aplican transversalmente

**REQ-KB-UMBRELLA-001, REQ-KB-UMBRELLA-002**

- GIVEN un agente revisa cualquier capability hija de esta umbrella
- WHEN intenta renegociar G-1..G-4 o mover un bloque entre P0 y P1
- THEN la implementación SHALL rechazar la renegociación y SHALL consultar
  `state.yaml` como autoridad cerrada.

### Scenario: Hard dependency doctor → modern-best-practices

**REQ-KB-UMBRELLA-003**

- GIVEN `capability-automated-skill-doctor` no ha cerrado (workflow
  `.github/workflows/skill-doctor.yml` no existe o falla)
- WHEN `capability-modern-best-practices` intenta marcar como terminada
  su verificación transversal de skills
- THEN SHALL bloquear el cierre y reportar la dependencia insatisfecha,
  sin posibilidad de bypass.

### Scenario: Operating Contract preservado

**REQ-KB-UMBRELLA-004**

- GIVEN `state.yaml` lista `completed: [init, explore, propose, spec]`
- WHEN se aplica el change y se modifican `.agents/AGENTS.md`,
  `.agents/skills/**` o ADRs
- THEN las líneas que comprenden "Operating Contract" SHALL quedar
  textualmente idénticas al snapshot verificado en `explore-notes.md`
  sección 0.

### Scenario: Skill-registry regenerado

**REQ-KB-UMBRELLA-005**

- GIVEN el apply modificó `.agents/skills/**/SKILL.md` (frontmatter,
  layout, contenido)
- WHEN finaliza la implementación
- THEN SHALL ejecutar `sdd-init` y SHALL regenerar
  `.agents/skill-registry.md` desde el filesystem, sin edición manual.
