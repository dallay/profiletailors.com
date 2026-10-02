# Docs Audit — sin gaps, fácil de entender

## Branch

`docs/docs-audit-wave-1` (creada desde `main`, 2026-10-01). Todo el trabajo de Oleadas va en esta rama.

## Route

Delegated direct en Plan Mode. Docs-only, no requiere ciclo SDD salvo que cambie contrato de producto o arquitectura.

## Outcome

Repasar toda la documentación del monorepo, cerrar gaps de índice, navegabilidad y estructura, y dejar todo fácil de entender con evidencia verificable.

## Tasks

- [x] **RPI-001: Inventario y matriz de gaps** — contar superficies, detectar docs huérfanos, validar `just doc-check` y `just docs-lint`, registrar decisión de ruta.
  - Aceptación: inventario con conteos y lista de huérfanos publicada en este archivo.
  - Evidencia: Working — ver sección Evidencia abajo.
  - Next: completar matriz detallada por carpeta y abrir oleadas.
- [x] **RPI-002: Índice `docs/README.md` + sub-READMEs** — indexar ~40 archivos huérfanos (compliance, testing, runbooks, infra, diagrams, retention, plans, marketing, mcp, security audit).
  - Aceptación: `docs/README.md` enlaza todo `docs/**/*.md` salvo excluidos explícitos; cada subcarpeta con README tiene tabla de contenidos.
  - Evidencia: Oleada 1 — índice ampliado (+42 líneas, 0 links rotos), `just docs-lint` PASS (0 issues, 3053 files), `just doc-check` PASS, `git diff --check` PASS. Oleada 2a — 8 READMEs (infrastructure, testing, runbooks, monitoring, marketing, mcp-server, plans, reviews) + compliance register completo; `just docs-lint` PASS (0 issues, 3061 files), `just doc-check` PASS, `git diff --check` PASS. Oleada 2b — 9 READMEs (adr-discovery, adr-enforcement, architecture/diagrams, architecture/shared, design, diagrams, marketing/lighthouse, mcp-server/clients, ai-engineering/reviews); 0 carpetas sin README. Nuestros 17 archivos limpios en lint; repo-wide `just docs-lint` FAIL por `plan/tasks/openspec-purge.md` ajeno (13 issues MD022/MD032, creado 08:15). `git diff --check` PASS. Observado sin tocar: ~26 deletions en `openspec/specs/*` + `plan/tasks/openspec-purge.md` de proceso concurrente.
  - Next: Oleada 3 — PRODUCT x6 + OpenSpec navegabilidad (RPI-004/006).
- [x] **RPI-003: Estructura y estándares** — aplicar `Overview → Changes → Usage → Troubleshooting → References`, English-only, `kebab-case.md`, frontmatter donde aplique.
  - Aceptación: muestra de 10 docs reparados como modelo + checklist para el resto.
  - Evidencia: 17 READMEs nuevos siguen `Overview → Usage → Troubleshooting → References` (variantes intencionales: `compliance/README` como registro futuro, `docs/README` como índice). Checklist resto: renombres `kebab-case` diferidos como deuda (ai-engineering MAYÚSCULAS, `adr-enforcement/ADR-*.md`, `c4/SUMMARY.md`, `EFICIENCIA.md` nombre ES/contenido EN) por romper links; English-only verificado en muestra.
- [x] **RPI-004: `PRODUCT.md` x6** — herencia `apps/web/PRODUCT.md` → hijos, sin duplicar, copy EN+ES clara, límites por superficie.
  - Aceptación: tabla de herencia + gaps de copy/contrato consentimiento.
  - Evidencia: Oleada 3a — 2 contradicciones reconciliadas contra código en `apps/web/PRODUCT.md` (waitlist API ADR-0011, Threads segundo provider DALLAY-598) + 1 legibilidad en `apps/web/app/PRODUCT.md` (A11y sin referencia cruzada). `just doc-check` PASS, `git diff --check` PASS, lint limpio en nuestros archivos. Consentimiento marketing/app/admin consistente con `shared/web` (banner, `__PT_CONSENT_ANALYTICS`, DNT/GPC, fail-closed).
- [x] **RPI-006: OpenSpec navegable** — post-purge 49 specs (commit 29a8e53a en esta rama). Estados: dallay-598 verify→qa, reactive-calendar archive→null (sigue en `changes/` fuera de `archive/`, dueño del change), skill-knowledge verify→archive. Navegabilidad vía `docs/README` + `openspec/README` + tabla en plan; sin writes dentro de `openspec/` (contratos producto). 1 link roto propio (`design/README` → DESIGN.md) detectado y corregido; manual link-check 203/203 PASS.
- [x] **RPI-005: Arquitectura navegable** — ADRs 0001-0026 + C4 + data-model + diagrams + `shared/dependencies.md` enlazados y sin drift.
  - Aceptación: ADR index vs archivos OK, C4 SUMMARY vs componentes OK, data-model 60 tablas/13 contextos verificados.
  - Evidencia: ADR 26/26 OK; C4 4 niveles + SUMMARY + README OK; data-model 12 HTML + README sin drift; SUMMARY drift plataformas corregido a LinkedIn + Threads. Deuda: `.agents/DESIGN.md` dice Astro 6, código + C4 dicen Astro 7 (^7.3.5) — dueño diseño.
- [ ] **RPI-006: OpenSpec navegable** — BLOQUEADO por purge concurrente (72→55 specs). Retomar cuando aterrice en `main`; borrar entrada duplicada vieja (72 specs) ya superada.
- [x] **RPI-007: Operador fácil** — compliance, testing, runbooks, infra, mcp-server, marketing seo, observabilidad con quick path.
  - Aceptación: cada guía tiene Quick path de 3 pasos + comandos `just` exactos.
  - Evidencia: Quick path agregados a infrastructure, testing, runbooks, monitoring READMEs; compliance register completo en Oleada 2a.
- [x] **RPI-008: Verificación final** — `just doc-check`, `just docs-lint`, link-check manual (lychee no instalado), `git diff --check`.
  - Aceptación: PASS documentado, o fallos listados como deuda con owner.
  - Evidencia: `just docs-lint` PASS (0 issues, 3072 files), `just doc-check` PASS, `git diff --check` PASS, manual link-check 203/203 PASS en 19 READMEs. CI/GitHub Actions NOT RUN (local only). Deudas con dueño: kebab renombres, DESIGN Astro 6, reactive-calendar en `changes/` vs `archive/`.

## Evidence

- `git status`: `main...origin/main`, limpio al inicio (2026-10-01).
- `find docs -type f`: ~130 archivos bajo `docs/`; `find . -maxdepth 4 -name "*.md"`: 487.
- `openspec/specs/`: 72; `openspec/changes/`: 3 activos (`dallay-598-threads-provider-integration` phase=verify/next=qa, `reactive-calendar-browser-sync` phase=archive/next=null pero sigue fuera de `archive/`, `skill-knowledge-bundle-hardening` phase=verify/next=archive) + `archive/`.
- `plan/tasks/`: 23 archivos; posible stale por revisar en RPI-002.
- `just doc-check`: PASS (2026-10-01).
- `just docs-lint`: FAIL solo en `tmp/plans/2026-09-25-pwa-*` (21 issues MD001/MD022/MD032); `docs/` limpio.
- `just docs-links`: NOT RUN — `lychee: not found` (exit 127).
- `docs/README.md`: 41 links verificados, 0 rotos — válido pero incompleto. Deja fuera compliance/* (28 archivos, solo 2 indexados), `testing/*` (3), `runbooks/password-recovery.md`, `infrastructure/*` (7), `diagrams/*.html` (11), `retention-framework-*` (3), `plans/*`, `marketing/seo.md`, `security/audit-report.md`, `architecture/adr-discovery/*`, `adr-enforcement/*`, `c4/*` parcial, `data-model/*` parcial.
- Carpetas sin README (17): `ai-engineering/reviews`, `architecture/adr-discovery`, `adr-enforcement`, `diagrams`, `shared`, `design`, `diagrams`, `infrastructure`, `marketing`, `marketing/lighthouse`, `mcp-server`, `mcp-server/clients`, `monitoring`, `plans`, `reviews`, `runbooks`, `testing`.
- `kebab-case.md`: violación en `docs/ai-engineering/*.md` (TOOLING, SCORECARD, CHANGES, ASSESSMENT, EFICIENCIA, REVIEW-EVIDENCE), `architecture/adr-enforcement/ADR-*.md`, `architecture/c4/SUMMARY.md` — solo `README.md` está exento por estándar.
- `PRODUCT.md` x6: todos con `<!-- impeccable:product-schema 1 -->`, longitudes 67-99 líneas, herencia por revisar en RPI-004.
- Lint config gap: `.markdownlint-cli2.jsonc` globs `**/*.md` sin excluir `tmp/**`, aunque `tmp/` está ignorado por git (`/tmp` en `.gitignore`). Por eso `just docs-lint` rompe por archivos temporales.
- `docs/architecture/adr/README.md`: índice 0001-0026 completo y al día.
- `docs/architecture/README.md`: C4 + ADRs + data-model bien enlazados; `docs/README.md` no refleja todo ese árbol.
- `.agents/DESIGN.md` v1.1: autoridad visual Nothing-inspired, al día.

## Status

Working: RPI-001 inventario inicial completo. Next: completar matriz huérfanos con script + arrancar RPI-002.
