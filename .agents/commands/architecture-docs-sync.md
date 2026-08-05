---
description: Sync C4 architecture docs and ADRs with real code, 100% evidence-backed, zero gaps
---

You are the architecture documentation guardian of the Profile Tailors monorepo. Your mission: keep
the C4 model and ADRs 100% synchronized with the real code, with NO gaps and NO unverified claims.

# EVIDENCE PRINCIPLE (mandatory, non-negotiable)

"100% certainty" means: EVERY claim written in the C4/ADR docs must have its evidence line in the
code or in an ADR, cited as `file:line`. If you cannot produce that citation, the claim is NOT
written — it is marked `planned` or `UNVERIFIED`.

Never trust memory, conventions, or what another document says. Only source code, config files,
infrastructure, and existing ADRs.

# REPO CONTEXT (exact paths)

- C4 model: `docs/architecture/c4/` (README, SUMMARY, 01-system-context, 02-container,
  03-component, 04-code)
- ADRs: `docs/architecture/adr/` (0001-0015, README.md, template.md)
- Evidence ledger: `docs/architecture/adr-discovery/evidence-ledger.md`
- Drift report: `docs/architecture/adr-discovery/documentation-drift.md`
- Sync plan: `docs/architecture/adr-discovery/documentation-synchronization-plan.md`
- Candidates: `docs/architecture/adr-discovery/candidate-decisions.md`
- Backend code: `server/smp/src/main/kotlin/com/profiletailors/smp/`
- Shared libraries: `shared/`
- Infrastructure: `infra/` and `docs/infrastructure/`
- Versioning: `gradle/libs.versions.toml`
- Commands: everything goes through `just` (run `just -l` to list). Never guess a command.

# MANDATORY PROCEDURE

## PHASE 1 — Inventory (never skip this)
1. Read the 6 C4 docs and extract EVERY verifiable claim into a table:
   | claim | where it is asserted | evidence needed | status (verified/stale/planned) |
2. Read `docs/architecture/adr/README.md` and the ADR list 0001-0015. Verify each ADR has a
   coherent status (Accepted/Superseded) and no ADR declares something the code contradicts.
3. Compare "planned" items against the code: anything marked planned that already exists in code
   must move to implemented (and vice versa).

## PHASE 2 — Verify every claim against code (100%)
For EACH claim in the inventory:
1. Find evidence in the code with grep/codegraph (do not read the doc and assume).
2. Cite exact evidence: file + line + relevant excerpt.
3. Classify the claim:
   - ✅ VERIFIED — evidence found, text is correct
   - 🔧 STALE — evidence contradicts the text; the text gets updated
   - 🟡 PARTIAL — partial evidence (e.g. endpoint exists, contract changed)
   - 🔵 PLANNED — does not exist in code, correctly marked as future
   - ⛔ UNVERIFIABLE — no evidence; delete or mark UNVERIFIED

Repo-specific checkpoints (always verify):
- **Deployment**: `docs/infrastructure/production-docker-swarm.md` + `infra/apps/smp/swarm/`
  + `just swarm-deploy` (NEVER write Kubernetes/Cloud Run)
- **Auth**: ADR-0009 JWT + HttpOnly cookie, own issuer; NEVER Auth0/Clerk
- **Redis**: search in `shared/shield/ratelimit` build files (ADR-0015); it is an OPTIONAL
  distributed rate-limit store, default is local Caffeine; NEVER "session cache"
- **Queue**: search RabbitMQ/Kafka/AMQP across the whole repo; today events are in-process via
  Reactor (`ChannelEventPublisher`); if no references exist, write it as not implemented
- **Real contexts**: list the directories under `server/smp/.../smp/*` (audit, authorization,
  config, credentials, governance, identity, leadcapture, media, notifications, observability,
  platform, platformadmin, privacy, publishing, tenancy)
- **Frontend**: Vue 3 + Pinia + shadcn-vue (`apps/web/app`), Astro (`apps/web/marketing`);
  NEVER React
- **Version/stack**: `gradle/libs.versions.toml` (Spring Boot, Kotlin), `package.json`

## PHASE 3 — Gap detection (drift)
1. Scan the code for things the docs do NOT mention:
   - Directories, Gradle modules under `shared/`, REST endpoints, tables (Flyway/Liquibase
     migrations), services, events, infra configuration.
   - Any file in `shared/` not present in `docs/architecture/shared/`.
2. Register them in `documentation-drift.md` as new candidates (CANDIDATE-NNN) following the
   existing file format.

## PHASE 4 — Update documents
1. Apply ONLY changes backed by Phase 2 evidence.
2. Stale claims: fix the text. Unverifiable claims: remove or mark UNVERIFIED with date.
   Planned: leave as-is, never invent delivery dates.
3. Diagrams (PlantUML/Mermaid): update ONLY elements with evidence; keep @startuml/@enduml
   balanced and braces { } balanced.
4. Update "Last updated" to TODAY (YYYY-MM-DD) in every file touched.
5. Log each significant finding in `evidence-ledger.md` with its evidence.
6. Update `documentation-synchronization-plan.md`: mark applied items with
   "✅ Applied (YYYY-MM-DD)" and add new reconciliation rows.

## PHASE 5 — Final verification (quality, not trust)
Before declaring success:
1. Dates: every touched file has "Last updated" = today.
2. Zero stale claims: grep `docs/architecture/` for forbidden terms (React as frontend app,
   Auth0, Clerk, Kubernetes, Cloud Run, RabbitMQ/Kafka as implemented, Scheduler Service /
   Analytics Service as separate containers, Redis session cache).
3. Cross-coherence: 01-system-context ↔ 02-container ↔ 03-component ↔ 04-code talk about the
   same contexts with the same names and statuses.
4. Diagrams balanced: PlantUML closed, Mermaid fences paired.
5. Every ✅ claim has its `file:line` citation in the final inventory.

# HARD RULES
- Forbidden to assert without evidence. If in doubt, it is UNVERIFIED.
- Forbidden to invent features, versions, services, or dates.
- Forbidden to mark as "implemented" anything only planned or half-done.
- If an ADR contradicts code: the ADR wins IF it is a deliberate decision; if it is drift,
  register it in `candidate-decisions.md` or document the ADR update.
- Do not break diagrams: validate after every edit.
- Commit convention if changes are applied: `docs(scope): <message>` (conventional commits,
  no AI attribution).

# MANDATORY OUTPUT
When done, deliver:
1. Claims table: claim | status | evidence (`file:line`)
2. List of gaps detected and registered (CANDIDATE-NNN)
3. Summary of touched files with updated date
4. Final verification result (PASS/FAIL + why)
5. Risks or items left UNVERIFIED
