# Archive Report — workspace-shortlinks-post-clicks

**Archived**: 2026-10-09 **Archived by**: sdd-archive executor **Artifact store mode**: openspec (`persistence.mode: openspec`, `artifact_policy: openspec-only`)
**Authorization**: explicit user instruction "archiva" (2026-10-09) — supersedes prior `No archivar` handoffs that required the 08:39 UTC rerun.

---

## Change Archived

**Change**: `workspace-shortlinks-post-clicks`
**Archived to**: `.agents/sdd/changes/archive/2026-10-09-workspace-shortlinks-post-clicks/`

### Gate Result

Archive gate **PASSED** on 2026-10-09.

- `state.yaml` was at `sdd_qa_after_phase4_verification` with `next: sdd_archive_pending_...`; updated to `current_phase: archive`, `next: none`, `completed: [..., archive]`.
- `verify-report.md` (Phase 1 PASS) exists; final technical verification `verify-report-phase4.md` records **PASS WITH WARNINGS**. Intermediate `verify-report-phase2.md`, `verify-report-phase3-slice1.md`, `verify-report-phase3-slice2.md`, `verify-report-p1-shortlink-substitution.md` preserved as history.
- `qa-report.md` exists and final authoritative verdict is **PASS WITH WARNINGS** (header line 10 + rerun 2026-10-09 ~08:39 UTC, `qa-report.md#matriz-actualizada-escenario-del-rerun-2026-10-09-0839-utc`). Historical BLOCKED/NOT TESTED matrices (safety-gate 07:50, runtime 07:40, composer opt-in era) are superseded and preserved as history, not as current verdict.
- No unresolved CRITICAL, P0, or P1 findings. All P0/P1 (publisher mock-only, provider refresh authorization safety, save payload, Use-shortlink FAIL) were closed by runtime evidence on 2026-10-09 07:40 UTC (authorizationUrl `http://localhost:33185/...` with HMAC-signed state, tokenBaseUrl/apiBaseUrl cascade to `localhost:33185`, 29 WireMock mappings, journal empty) and by browser end-to-end on 2026-10-09 ~08:39 UTC.
- Acceptance-relevant scenarios all PASS in final rerun:
  - A.1–A.7 browser Phase 4 end-to-end PASS (DOM sin opt-in/Use/Keep/preview, 2x `POST /api/v1/links 201` con dedup de 3 ocurrencias a 2 destinos, `POST /api/publishing/publications 200` con `bodyText` solo shortcodes `EktNZ6C4pF`/`tBnEGjZ89o`, `SCHEDULED_AT dueAt 2030-01-15T09:37:00Z`, persistencia + 302 verificados, WireMock journal 0).
  - A.8/A.9/A.11 PASS via focused suite (60 tests) — failure best-effort, no-URL no-call, edit bypass.
  - B.1–B.5 member reread PASS (seed `lgrvRHnLVZ` en `...0006` por member JWT HS256 principal `...0004`, reread 0→1 tras 302 anónimo sin relogin, aislamiento cruzado OWNER `...0002` no ve `lgrvRHnLVZ`).
- Repository policy (`config.yaml qa.archive_blockers`) blocks only unresolved CRITICAL/P0/P1 and acceptance-relevant BLOCKED/NOT TESTED. Remaining P2/P3 are warnings per skill policy and do not block. See Non-blocking Warnings below.

### Specs Synced

| Domain | Action | Details |
|--------|--------|---------|
| publishing-shortlinks | Created | 1 requirement (Automatic Shortlink Replacement at Publication Creation), 7 scenarios (single, repeated dedup, multiple distinct, no-URL, best-effort failure, live preview, edit bypass). Copied verbatim delta → `.agents/sdd/specs/publishing-shortlinks/spec.md` |
| workspace-shortlink-click-analytics | Created | 2 requirements (Record Clicks; Isolate Resolution and Analytics), 9 scenarios (consult, zero-count, list, paginate cursor composite, cross-workspace denied x2, context required, versioned media type, public resolution). Copied verbatim delta → `.agents/sdd/specs/workspace-shortlink-click-analytics/spec.md` |

No merge into existing main specs was needed (`publishing-shortlinks` and `workspace-shortlink-click-analytics` did not exist under `.agents/sdd/specs/`). Existing `.agents/sdd/specs/publishing/spec.md` was not modified — Phase 4 automatic replacement lives in the new `publishing-shortlinks` capability; no destructive merge, no other requirements touched.

### Archive Contents

- proposal.md ✅
- specs/ ✅ (`publishing-shortlinks/spec.md`, `workspace-shortlink-click-analytics/spec.md`)
- design.md ✅
- tasks.md ✅ (22/22 tasks complete: Phase 1 1.1–1.2, Phase 2 2.1–2.2, Phase 3 3.1–3.9 incl. superseded Slice 2, Phase 4 4.1–4.9)
- apply-progress.md ✅
- bdd-fast-collection-coverage.log ✅
- verify-report.md ✅ (Phase 1 PASS)
- verify-report-phase2.md ✅
- verify-report-phase3-slice1.md ✅ (PASS WITH WARNINGS)
- verify-report-phase3-slice2.md ✅ (PASS WITH WARNINGS, superseded by Phase 4)
- verify-report-p1-shortlink-substitution.md ✅ (pass_with_warnings)
- verify-report-phase4.md ✅ (PASS WITH WARNINGS — authoritative technical verdict)
- qa-report.md ✅ (PASS WITH WARNINGS — acceptance evidence preserved, 08:39 UTC rerun authoritative)
- state.yaml ✅ (updated to archive completion)
- archive-report.md ✅ (this file)

### Source of Truth Updated

- `.agents/sdd/specs/publishing-shortlinks/spec.md` — Created from delta (verbatim copy)
- `.agents/sdd/specs/workspace-shortlink-click-analytics/spec.md` — Created from delta (verbatim copy)

### Evidence (preserved, not rerun in archive)

- Focused composer suite: `pnpm exec vitest run src/modules/publishing/presentation/components/CreatePostModal.test.ts` → 60 tests passed (verify-phase4 authoritative; historical 67 in apply-progress retained as warning, not inferred away).
- Full dashboard suite reported: 169 files / 1920 tests passed; type-check passed; lint exit 0 with one preexisting warning in untouched `SchedulerTimelineBody.vue:23` (`viewport` unused); affected-file Biome passed.
- Domain extraction suite: `shortlink-post-content.test.ts` 4/4 passed.
- Backend Phase 1–3 evidence retained in respective verify reports (backend-check, bdd-fast 14 scenarios, postgres integration, collection cursor exclusive/ordering).
- Browser safety: no `Schedule Now`, no publish real, no OAuth exchange/refresh, no external send, no destructive delete, no `.env`/backup/process mutation, no commit/push. `POST /api/publishing/publications` used `SCHEDULED_AT 2030-01-15T09:37:00Z` to keep worker idle; WireMock journal 0 before and after; `authorizationUrl` host `localhost:33185`.
- Test artifacts created during QA (shortlinks `EktNZ6C4pF`, `tBnEGjZ89o` in `...0002`, `lgrvRHnLVZ` in `...0006`, publication `pub-2026-10-09` queued for 2030, plus 27 `qa-pagination-*` and `po3AsYHHHg`/`oJCmcVVs0D` históricos) preserved in DB; none deleted.
- Quality runner / QA FSM unavailable → fallback manual recorded; remote CI / deployment not run, not claimed.

## Non-blocking Warnings Preserved (visible, do not block archive per policy)

- **P2 histórico informativo — frontend omite `X-Workspace-Id` en `listWorkspaceShortlinkMetrics`** (`workspaceScoped: true` no pasado en `init`; HAR sin header). Paginación funciona por tolerancia backend single-workspace, pero contrato queda eludido. Deuda de contrato, sin fix en archive (QA no toca código). No es acceptance-relevant BLOCKED.
- **P2 histórico informativo — lista `/shortlinks` stale tras cambio de workspace sin recargar** (observado como member; no repetido como OWNER single-workspace). Fricción observable, aislamiento tras recarga intacto. No bloquea.
- Technical warnings: focused count discrepancy 67→60 (60 authoritative); lint preexisting warning untouched; quality-runner unavailable fallback; `verify-report-phase4.md` task 4.9 marked incomplete at verify time (completed later by QA 08:39); diff-check scoped only (whole-tree not clean due to unrelated worktree modifications, preserved); mail subsystem DOWN (liveness/readiness UP, no impacto aceptación); cursor 400 transitorio self-recovered (race token-refresh, no bug cursor); no build rerun in Phase 4 verify; no full a11y audit / responsive matrix / i18n repeat as OWNER (histórico acotado PASS retained).

## Verification

- [x] Main specs updated correctly (2 new spec.md identical via `diff -q`)
- [x] Change folder moved to archive (`2026-10-09-workspace-shortlinks-post-clicks`)
- [x] Archive contains all artifacts (proposal, specs, design, tasks, apply-progress, all verify-reports, qa-report, state, archive-report)
- [x] Active changes directory no longer has this change
- [x] No implementation source files edited during archive; no commit or push performed; unrelated worktree dirty files preserved untouched
- [x] No publish, no delete, no data loss

## SDD Cycle Complete

The change has been fully planned, implemented, verified, and archived. Product contracts for automatic shortlink substitution at submit (dedup, best-effort, edit bypass) and workspace-scoped click analytics (zero-inclusive, cursor pagination, isolation) are now source of truth under `.agents/sdd/specs/`. Full audit trail retained in archive. Ready for the next change.
