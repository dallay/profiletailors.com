---
name: adr-writer
description: Write, review, supersede and index Architecture Decision Records (ADRs) for the profiletailors.com monorepo, following the house format in docs/architecture/adr/template.md. Use this skill whenever the user mentions ADR, architecture decision, decision record, "document this decision", "why did we choose X", superseding or deprecating a decision, or when a change introduces a cross-cutting technical choice (new dependency, persistence/messaging/auth strategy, module boundary, licensing, CI gate, build tooling, bounded-context split) — even if they never say the word "ADR".
---

# ADR Writer — profiletailors.com

Produce ADRs that are consistent with the repository's existing records, auditable, and honest about
what is and is not decided.

## 0. Ground rules

- **Language:** English. Repo docs and existing ADRs are English. Reply to the user in their
  language, write the file in English.
- **Location:** `docs/architecture/adr/NNNN-kebab-case-title.md` (4-digit, zero-padded,
  monotonically increasing, never reused).
- **Immutability:** an accepted ADR is not rewritten to change the decision. Change of mind = new
  ADR that supersedes the old one. Editorial fixes, status updates and acceptance-criteria progress
  are allowed and tracked in `Last revised`.
- **Decision vs. opinion:** an ADR records a decision *already made or proposed for ratification*.
  If the user is still exploring, help them analyze options first, then write the ADR with
  `Status: Proposed`.
- **No invented facts.** Dates, issue keys, owners, benchmark numbers and "we rejected X because…"
  must come from the user, the repo, or the conversation. If missing, ask once (max one question) or
  mark as `TBD` explicitly.

## 1. Workflow

1. **Triage — does this deserve an ADR?** Write one when the decision is hard to reverse, affects
   more than one module/bounded context, introduces or removes a dependency or platform capability,
   sets a policy (security, licensing, CI gate, data handling), or will be questioned in 6 months.
   Skip it for local refactors, naming, or reversible implementation details — suggest a code
   comment, PR description or SDD design note instead.
2. **Get the next number:** list `docs/architecture/adr/` and use max existing NNNN + 1
   (4-digit, zero-padded, never reused). Do not guess the number.
3. **Calibrate against the repo (when you have access):** read the 1–2 most recent ADRs and
   `docs/architecture/c4/` to confirm the header fields, tone and link style have not drifted from
   this skill. If they differ, follow the repo and mention the drift.
4. **Gather inputs** (infer from the conversation first, ask only for what is missing): the
   problem/forces, the decision, the options considered and why they lost, consequences (positive
   *and* negative), owner, scope, related issue/PR/SDD change.
5. **Draft** from `docs/architecture/adr/template.md`, then conform the header and sections
   to the newest accepted ADR when they differ (see step 3).
6. **Run the quality gate** (section 4) and fix before presenting.
7. **Wire it in:** update the ADR index if one exists (`docs/architecture/adr/README.md`), and if
   this ADR supersedes another, edit the old one's `Status` and `Superseded by` in the same change.
8. **Suggest the commit:** `docs(adr): add ADR-NNNN <short title>` (Conventional Commits, as
   required by CONTRIBUTING.md).

## 2. Format (house style, from `docs/architecture/adr/template.md` as used by recent ADRs)

Title: `# ADR-NNNN: Title in Title Case`

Header block as a bullet list, in this order:

- `Status:` Proposed | Accepted | Deprecated | Superseded by ADR-NNNN | Rejected
- `Date:` ISO `YYYY-MM-DD` of the original decision
- `Last revised:` ISO date; only when edited after first publication
- `Decision owners:` role or GitHub handle
- `Scope:` repo area affected (e.g. `server/smp`, `apps/web/app`, `shared/*`, whole repo)
- `Supersedes:` ADR-NNNN or `None`
- `Superseded by:` ADR-NNNN or `None`
- `Related:` nested list — `Issues/PRs` (e.g. `DALLAY-498`), docs, SDD artifacts under
  `.agents/sdd/`
- `Legal review status:` **only** for licensing, privacy/GDPR, contributor-rights or compliance
  decisions. Use an explicit marker such as `legal-review-required` and state that the ADR is an
  engineering/product decision, not legal advice.

Sections, in this order:

1. `## Context` — forces, constraints, current state. Facts, not advocacy.
2. `## Decision` — **numbered list**, one imperative, testable statement per item ("Keep…", "Do
   not…", "Reconsider X only through a new ADR…"). State explicit non-decisions and out-of-scope
   items.
3. `## Acceptance Criteria Status` — *optional*. Table `Criterion | Status`. Use `Done`, `Partial`,
   `Not started`, `Not applicable`, each with a one-line justification of what is still unverified.
   Never mark `Done` without evidence.
4. `## Consequences` — bullets; include costs, risks, and obligations created, not only benefits.
5. `## Alternatives Considered` — one `###` per option, each with an honest rejection reason or
   `Not adopted` with why.
6. `## References` — links to repo files (relative paths), external specs/RFCs, related ADRs.

## 3. Repo-specific lenses

Tie the decision to the architecture the project actually uses; name the layer it touches.

- **Backend (`server/smp`, `shared/*`):** hexagonal (ports & adapters), DDD bounded contexts, CQRS
  via `shared/bus`, Spring Boot 4 + Kotlin + WebFlux/R2DBC, Spring Modulith boundaries. Say which
  port/adapter or module boundary changes, and whether `shared/*` stays framework-agnostic.
- **Frontend (`apps/web/*`):** Astro marketing (EN default, ES under `/es/`), Vue 3 app/admin,
  Biome. Note i18n and light/dark impact if UI-related.
- **Identity/data conventions:** UUIDs are generated client-side, not by the database — flag any
  decision that conflicts.
- **Cross-cutting:** AGPL-3.0 + CLA implications, GDPR (`shared/web`, `tools/compliance`), CI gates
  (Detekt, Biome, Sonar, Semgrep, Gitleaks, Trivy, licence inventory), release-please versioning,
  Justfile/Makefile commands.
- **Process link:** if the decision came out of an SDD change
  (`explore → propose → spec/design → tasks → apply → verify → archive`), reference the artifacts in
  `.agents/sdd/` and have the ADR capture the *durable* decision, not the task list.

## 4. Quality gate (check before presenting)

- [ ] Number is max existing + 1 in `docs/architecture/adr/`; filename is kebab-case and matches the title.
- [ ] Status and dates are consistent (`Date` ≤ `Last revised`; `Accepted` has a date).
- [ ] Context contains no recommendation; Decision contains no background story.
- [ ] Every Decision item is falsifiable (someone could check it in code, CI or docs).
- [ ] At least two real alternatives, including "do nothing" when relevant.
- [ ] Consequences list at least one negative or risk.
- [ ] No overclaiming: where something is unverified, the ADR says so (mirror the "Partial — …
  remain unverified" style of ADR-0012).
- [ ] Legal/compliance topics carry the legal-review marker.
- [ ] Links are relative, resolve in the repo (lychee runs in CI), and Markdown passes markdownlint
  (blank lines around headings/lists, hard-wrap consistent with existing files).
- [ ] If superseding: old ADR updated in the same change.

## 5. Other modes

- **Review an existing ADR:** run the quality gate, report findings by severity, propose minimal
  diffs. Do not change the decision.
- **Supersede:** create the new ADR (`Supersedes: ADR-OLD`), set the old one to
  `Status: Superseded by ADR-NEW`, keep its body intact.
- **Backfill (retroactive ADR):** set `Date` to when the decision was actually made if known; add a
  line in Context saying the record is retroactive.
- **Index/audit:** list all ADRs with number, title, status, scope; flag gaps in numbering, missing
  supersede links and stale `Proposed` records.

## 6. Output

Write the file to the ADR directory (or `/mnt/user-data/outputs/` when no repo access), then give
the user: path, one-paragraph summary of the decision, open `TBD`s, and the suggested commit
message. No long post-amble.
