# Capability — Comment Cleanup (P1-C)

## Purpose

Establecer un pipeline de enforcement post-procesado para la política
zero-comments: reframear el wording en AGENTS.md, crear un scanner
determinístico en Node puro (sin dependencias externas), crear un
sub-agente barato (`comment-cleanup`, `model: haiku`) para casos
context-dependent, y añadir un test que verifique que el
determinístico funciona y no tiene falsos positivos en los archivos
permitidos (shebangs, headers de licencia, generated markers).

## Authority

- Governance decision **G-3** (zero-comments preservado, reframeado
  a "prefer self-documenting; do not generate explanatory comments
  by default; cleanup enforced in post-processing").
- Trazabilidad: bloque **P1-C** del `proposal.md`.

## Scope

- Archivos modificados:
  - `.agents/AGENTS.md` (sección "Fix Simplicity and Zero-Comment
    Policy", reframe del wording).
  - `.agents/scripts/skill-comment-scan.mjs` (nuevo, Node puro).
  - `.agents/agents/comment-cleanup.md` (nuevo sub-agente, `model:
    haiku`).
  - Test del scanner (Node puro o Vitest mínimo) bajo
    `.agents/scripts/__tests__/skill-comment-scan.test.mjs` o
    equivalente.
- Permitidos: shebang ejecutable (`#!/...`), headers de licencia
  en cabecera de archivo (`SPDX-License-Identifier: ...`), markers
  de generated (`@generated`, `// AUTO-GENERATED`).

## Requirements

| REQ-ID                | Statement                                                                                                                                       |
|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| REQ-CC-001            | `.agents/AGENTS.md` sección "Fix Simplicity and Zero-Comment Policy" SHALL usar el wording "prefer self-documenting code; do not generate explanatory comments by default; cleanup enforced in post-processing"; SHALL preservar la prohibición de comments, lint suppression directives, TODO/FIXME/HACK, y commented-out code. |
| REQ-CC-002            | El scanner determinístico `.agents/scripts/skill-comment-scan.mjs` SHALL estar escrito en Node puro sin dependencias externas (npm packages) y SHALL fallar con exit code no-cero cuando encuentre `^\\s*//`, `^\\s*\\*`, `^\\s*#` (en archivos de código), o `<!--` (en archivos `.md`/`.astro/.vue`). |
| REQ-CC-003            | El scanner SHALL permitir shebangs ejecutables como `#!/usr/bin/env bash`, headers de licencia, y generated markers; SHALL permitir líneas vacías y líneas de instrucciones shebang sin tratarlas como comments. |
| REQ-CC-004            | El sub-agente `comment-cleanup` SHALL existir en `.agents/agents/comment-cleanup.md` con `model: haiku` y SHALL consumir el output del scanner para distinguir comentarios contextuales (comentarios dentro de strings, ejemplos de skill que requieren un comment para ser ilustrativos, etc.) de comentarios a eliminar. |
| REQ-CC-005            | SHALL existir al menos un test que verifique el determinístico: dado un fixture con comment prohibido, SHALL fallar; dado un fixture con shebang/licencia/generated marker permitidos, SHALL pasar. |
| REQ-CC-006            | El pipeline SHALL ejecutarse en CI como parte del gate `.github/workflows/skill-doctor.yml` (mismo workflow, fase previa al sub-agente de contradicciones). |

## Scenarios

### Scenario: AGENTS.md reframeado

**REQ-CC-001**

- GIVEN el wording actual de AGENTS.md dice "Never leave comments in
  the repo. The standard is zero comments: no explanatory comments
  or docblocks, TODO/FIXME notes, lint/type suppression directives,
  or commented-out code."
- WHEN se aplica esta capability
- THEN SHALL reemplazarse por un párrafo que contenga literal la frase
  "prefer self-documenting code; do not generate explanatory comments
  by default; cleanup enforced in post-processing" y SHALL preservar
  las prohibiciones concretas (explanatory comments, docblocks,
  TODO/FIXME, lint suppressions, commented-out code).
- AND ningún test preexistente SHALL romperse por el reframe (las
  prohibiciones materiales no cambian).

### Scenario: Scanner determinístico falla en comments prohibidos

**REQ-CC-002**

- GIVEN un fixture `fixtures/comments-bad.kt` que contiene
  `// this is a comment`
- WHEN corre `node .agents/scripts/skill-comment-scan.mjs <path>`
- THEN SHALL salir con exit code ≠ 0 y SHALL imprimir el path y la
  línea del hallazgo.

### Scenario: Scanner respeta permitidos

**REQ-CC-003**

- GIVEN un fixture `fixtures/comments-allowed.sh` que contiene
  `#!/usr/bin/env bash`, `SPDX-License-Identifier: Apache-2.0`, y
  `# AUTO-GENERATED; do not edit`
- WHEN corre el scanner sobre ese archivo
- THEN SHALL salir con exit code 0.

### Scenario: Sub-agente barata para context-dependent

**REQ-CC-004**

- GIVEN el scanner emite una lista de hallazgos en un PR
- WHEN el sub-agente `comment-cleanup` consume esa lista
- THEN SHALL clasificar cada hallazgo como `delete` / `keep-as-license`
  / `keep-as-shebang` / `keep-as-generated` / `keep-as-illustrative-in-skill-example`
  y SHALL proponer acciones reversibles; SHALL NO borrar sin
  clasificación explícita.

### Scenario: Test del pipeline determinístico

**REQ-CC-005**

- GIVEN el test cubre el fixture "bad" y el fixture "allowed"
- WHEN se ejecuta `node --test .agents/scripts/__tests__/skill-comment-scan.test.mjs`
- THEN SHALL pasar (2 assertions: bad → exit ≠ 0; allowed → exit 0).

### Scenario: Gate CI ejecuta el pipeline

**REQ-CC-006**

- GIVEN `.github/workflows/skill-doctor.yml` corre el scanner antes
  de invocar al sub-agente de contradicciones
- WHEN un PR introduce un `// FIXME` en `apps/web/marketing/src/`
- THEN SHALL fallar la fase "comment-scan" y SHALL bloquear el merge.
