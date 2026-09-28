# Capability — Automated Skill Doctor (P1-D)

## Purpose

Establecer el gate de CI que detecta el drift del knowledge bundle
antes de que sea mergeado: un script determinístico en Node puro
que valida por cada `SKILL.md` la forma (frontmatter `name`,
coincidencia folder ↔ name, metadata `category` y `family`,
existencia de paths internos referenciados, ausencia de strings de
contamination conocidas), un sub-agente barato que use contexto LLM
para contradicciones cross-skill y semánticas más finas, un workflow
de GitHub Actions (`.github/workflows/skill-doctor.yml`) que corre
ambos en PR que toquen `.agents/`, y un ADR nuevo (ADR-NNN) que
codifique la taxonomía canónica + las reglas del doctor.

## Authority

- Governance decision **G-2** (ADR-NNN para taxonomía canónica + CI
  gates).
- Trazabilidad: bloque **P1-D** del `proposal.md`.
- Hard dependency declarado en REQ-KB-UMBRELLA-003:
  `capability-modern-best-practices` no puede cerrar sin este gate.

## Scope

- Archivos nuevos:
  - `.agents/scripts/skill-doctor.mjs` (Node puro, sin dependencias
    externas).
  - `.agents/agents/skill-doctor.md` (sub-agente).
  - `.github/workflows/skill-doctor.yml` (workflow CI).
  - `docs/architecture/adr/NNN-skill-taxonomy-and-doctor-gate.md`
    (nuevo ADR).
- El ADR-NNN se añade al `docs/architecture/adr/README.md` y referencia
  este change.
- El workflow corre:
  - Detección de PR: paths que toquen `.agents/**` o
    `.agents/AGENTS.md`, AGENTS.md, o cualquier `SKILL.md`.
  - Job 1: ejecutar `skill-doctor.mjs`.
  - Job 2: ejecutar `skill-comment-scan.mjs` (de
    `capability-comment-cleanup`).
  - Job 3: invocar el sub-agente `skill-doctor` para
    contradicciones context-dependent.
  - Cualquier fallo en cualquiera de los 3 jobs SHALL bloquear el
    merge del PR.

## Requirements

| REQ-ID                | Statement                                                                                                                                       |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-SD-001            | SHALL existir `.agents/scripts/skill-doctor.mjs` (Node puro) que valide por cada `SKILL.md`: frontmatter `name:` presente, `name` coincide con folder, `metadata.category` y `metadata.family` presentes, paths internos referenciados existen en el worktree, y 0 hits de los strings de contamination definidos en `capability-external-contamination`. |
| REQ-SD-002            | SHALL existir `.agents/agents/skill-doctor.md` con `model: haiku` (o similar bajo costo), SHALL consumir el JSON de salida del script determinístico y SHALL detectar contradicciones cross-skill (p. ej. una skill dice "use MobX" y otra dice "we use Pinia only") con salida estructurada. |
| REQ-SD-003            | SHALL existir `.github/workflows/skill-doctor.yml` que ejecute el script determinístico + `skill-comment-scan.mjs` + invoque el sub-agente; SHALL correr solo en PR que toquen `.agents/**` (path filter estándar); SHALL bloquear el merge si cualquier job falla. |
| REQ-SD-004            | SHALL existir `docs/architecture/adr/NNN-skill-taxonomy-and-doctor-gate.md` (ADR-NNN) que codifique la taxonomía canónica (folder == name == id; metadata.category + metadata.family; 2 excepciones documentadas) y las reglas del skill-doctor como gate de CI. |
| REQ-SD-005            | El ADR-NNN SHALL estar linkeado desde `docs/architecture/adr/README.md` y SHALL referenciar este change como evidencia.                            |
| REQ-SD-006            | El script determinístico SHALL tener una suite de tests bajo `.agents/scripts/__tests__/skill-doctor.test.mjs` o equivalente que cubra: name presente, name mismatch (incluyendo el caso de las 2 excepciones legítimas), metadata ausente, path interno inexistente, y contamination detectada. |
| REQ-SD-007            | El sub-agente `skill-doctor` SHALL tener un test de smoke (al menos un escenario fixture) que verifique el contrato de entrada/salida JSON.       |
| REQ-SD-008            | Este capability SHALL estar cerrado (workflow presente y bloqueante, ADR-NNN merged) antes de que `capability-modern-best-practices` pueda marcar como terminada su verificación transversal. |

## Scenarios

### Scenario: Validación determinística de un SKILL.md válido

**REQ-SD-001**

- GIVEN un `SKILL.md` con frontmatter completo, `name` == folder,
  `metadata.category` y `metadata.family` no vacíos, paths
  internos existentes, 0 contamination strings
- WHEN corre `node .agents/scripts/skill-doctor.mjs <dir>`
- THEN SHALL salir con exit code 0 y SHALL imprimir
  `OK: <count> skills validated`.

### Scenario: SKILL.md con drift reintroducido

**REQ-SD-001, REQ-SD-003**

- GIVEN un PR reintroduce `apps/portfolio` en una skill
- WHEN corre el workflow `.github/workflows/skill-doctor.yml`
- THEN SHALL fallar el job determinístico con un finding con el
  path del archivo, SHALL fallar el check de GitHub Actions, y SHALL
  bloquear el merge automáticamente.

### Scenario: Contradicción cross-skill detectada por sub-agente

**REQ-SD-002**

- GIVEN una skill afirma "use MobX as state manager" y otra skill
  declara "Vue 3 + Pinia"
- WHEN el sub-agente `skill-doctor` consume el output del
  determinístico + el corpus de skills
- THEN SHALL emitir un finding estructurado `{ kind:
  cross-skill-contradiction, severity: high, skills: [...],
  rationale: ... }` y SHALL propagarse como failure al job de GitHub
  Actions.

### Scenario: ADR-NNN presente y linkeado

**REQ-SD-004, REQ-SD-005**

- GIVEN el ADR se crea como
  `docs/architecture/adr/NNN-skill-taxonomy-and-doctor-gate.md`
- WHEN se verifica `docs/architecture/adr/README.md`
- THEN SHALL existir un índice/link al ADR-NNN y SHALL aparecer
  referenciado como source of truth para taxonomía de skills.

### Scenario: Tests del doctor pasan

**REQ-SD-006, REQ-SD-007**

- GIVEN las suites de tests cubren el determinístico y el
  smoke del sub-agente
- WHEN se ejecutan en CI
- THEN SHALL pasar las assertions definidas en cada test.

### Scenario: Hard dependency transversal

**REQ-SD-008, REQ-KB-UMBRELLA-003**

- GIVEN `capability-modern-best-practices` está lista para
  declararse cerrada
- WHEN `state.yaml` se mueve a la fase `apply` o `verify` de esa
  capability
- THEN SHALL haber un check explícito de que
  `.github/workflows/skill-doctor.yml` existe y bloquea merges;
  SHALL NO permitirse el cierre sin esa confirmación.
