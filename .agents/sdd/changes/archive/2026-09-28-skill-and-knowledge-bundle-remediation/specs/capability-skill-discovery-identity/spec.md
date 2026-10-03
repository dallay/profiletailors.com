# Capability — Skill Discovery & Identity (P0-A)

## Purpose

Establece la taxonomía canónica del knowledge bundle: una skill por
folder top-level, identidad estable entre folder name, frontmatter
`name` y skill id, y metadatos uniformes `category` y `family` en cada
`SKILL.md`. El resultado es un bundle donde un agente puede resolver
"qué skill cubre X" sin ambigüedad, y donde el gate de CI puede
validar la forma del bundle sin tener que entender su contenido.

## Authority

- Governance decision **G-2** (taxonomía canónica + CI gates).
- Trazabilidad: bloque **P0-A** del `proposal.md`.
- ADR a actualizar: ADR-NNN (nuevo, creado por
  `capability-automated-skill-doctor`); reglas de esta capability
  quedan codificadas en ese ADR.

## Scope

- Archivos modificados: 67 `SKILL.md` (uno por skill), carpetas
  existentes reubicadas a layout plano, 3 subcarpetas categoriales
  vacías en `design-pattern/` (`behavioral/`, `creational/`,
  `structural/`) eliminadas.
- Excluidos del flatten: `impeccable/` (conserva `reference/`,
  `scripts/`, `agents/` porque son artefactos ejecutables invocados
  por la propia skill) y `impeccable/`, `astrolicious-astro` (folder
  name ≠ frontmatter `name`; documentado como identidad intencional).
- `.agents/skill-registry.md` se regenera vía `sdd-init` al final del
  apply; no se edita a mano.

## Requirements

| REQ-ID                | Statement                                                                                                                          |
|-----------------------|------------------------------------------------------------------------------------------------------------------------------------|
| REQ-SDI-001           | Cada skill SHALL vivir en su propia carpeta top-level bajo `.agents/skills/`, una carpeta por skill, sin jerarquía anidada de skills. |
| REQ-SDI-002           | El nombre de la carpeta SHALL coincidir con el campo `name:` del frontmatter del `SKILL.md`, salvo las dos excepciones documentadas en REQ-SDI-003. |
| REQ-SDI-003           | Las únicas dos excepciones permitidas a la regla REQ-SDI-002 son `impeccable/` (folder) → `impeccable` (frontmatter, intencional) y `astrolicious-astro/` (folder) → `astrolicious-astro` (frontmatter, intencional). |
| REQ-SDI-004           | Cada `SKILL.md` SHALL declarar en su frontmatter `metadata.category` y `metadata.family` con valores no vacíos.                       |
| REQ-SDI-005           | La skill `impeccable/` SHALL conservar sus subcarpetas ejecutables (`reference/`, `scripts/`, `agents/`); no se aplana.             |
| REQ-SDI-006           | Las subcarpetas categoriales vacías `design-pattern/behavioral/`, `design-pattern/creational/` y `design-pattern/structural/` SHALL eliminarse. |
| REQ-SDI-007           | Tras el apply SHALL ejecutarse `sdd-init` para regenerar `.agents/skill-registry.md`; ninguna edición manual del registry SHALL formar parte del diff. |
| REQ-SDI-008           | Cada `SKILL.md` SHALL tener un frontmatter mínimo válido (paréntesis YAML correctos, delimitadores `---` abiertos y cerrados, UTF-8). |

## Scenarios

### Scenario: Flatten aplicado

**REQ-SDI-001, REQ-SDI-006**

- GIVEN el estado actual tiene 47 skills anidadas bajo
  `backend-platform/`, `frontend-platform/`, `testing/`,
  `design-pattern/{behavioral,creational,structural}/`
- WHEN el apply reorganiza las carpetas
- THEN SHALL existir 67 carpetas top-level, una por skill, y SHALL
  eliminarse las 3 subcarpetas vacías; `.agents/skills/` SHALL
  contener un nivel de carpetas para skills más las carpetas
  ejecutables internas permitidas por REQ-SDI-005.

### Scenario: Identidad canónica

**REQ-SDI-002, REQ-SDI-003**

- GIVEN el apply completa el flatten
- WHEN el agente CI corre `scripts/find-skills-with-name-mismatch.mjs`
  (implementado en `capability-automated-skill-doctor`)
- THEN SHALL reportar exactamente dos skills en la lista de
  excepciones: `impeccable` y `astrolicious-astro`; SHALL fallar si
  cualquier otra skill tiene folder name ≠ frontmatter `name`.

### Scenario: Metadata uniforme

**REQ-SDI-004, REQ-SDI-008**

- GIVEN los 67 `SKILL.md` reorganizados
- WHEN el agente parsea cada frontmatter
- THEN SHALL encontrar en cada uno los campos `metadata.category`
  y `metadata.family` no vacíos, y SHALL fallar en cualquier skill
  con metadata ausente, vacía o mal formada.

### Scenario: Skill-registry regenerado

**REQ-SDI-007**

- GIVEN el apply modificó al menos un `SKILL.md`
- WHEN finaliza la implementación
- THEN SHALL existir un commit/change con la salida de `sdd-init`
  reescribiendo `.agents/skill-registry.md`; SHALL NO existir un
  commit que edite ese archivo a mano.

### Scenario: La skill impecable conserva artefactos internos

**REQ-SDI-005**

- GIVEN el apply reorganiza `.agents/skills/`
- WHEN se valida `impeccable/`
- THEN SHALL permanecer `impeccable/reference/`, `impeccable/scripts/`
  y `impeccable/agents/` con sus contenidos originales referenciados
  desde el `SKILL.md`.
