# Verification Report: Backoffice Admin Shell

- Change: `add-backoffice-admin-shell`
- Mode: openspec
- Date: 2026-09-14
- Verifier: sdd-verify sub-agent

## Completeness

| Source     | Result                                                                                                                                                                       |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Tasks      | 14/14 marked complete in `tasks.md`                                                                                                                                          |
| Tests      | 44/44 admin Vitest pass (6 files)                                                                                                                                            |
| Type-check | `just admin-check` (`vue-tsc --build`) clean, exit 0                                                                                                                         |
| Build      | `just admin-build` clean, exit 0 (built in 3.52s)                                                                                                                            |
| Lint       | `biome check` on 9 touched/new files: clean                                                                                                                                  |
| Scope      | Only `apps/web/admin/**` + `openspec/specs/admin-authorization/spec.md` + change dir; 0 diff lines in `server/`, `shared/`, `apps/web/app/`, `apps/web/marketing/`, `tools/` |

## Build / Tests / Coverage Evidence

Commands executed by the verifier (real runtime evidence, not attested):

- `just admin-test` → `Test Files 6 passed (6)`, `Tests 44 passed (44)`:
  `LoginView.test.ts` (2), `nav-registry.spec.ts` (11), `DirectInvitationsView.spec.ts` (9),
  `auth.store.test.ts` (14), `RevokeInvitationDialog.spec.ts` (6), `PlannedAreaView.spec.ts` (2).
- `just admin-check` → `vue-tsc --build`, no errors.
- `just admin-build` → `✓ built in 3.52s`.
- `pnpm --filter admin exec biome check <9 files>` → `Checked 9 files in 54ms. No fixes applied.`
- `git diff HEAD --stat -- server/ shared/ apps/web/app/ apps/web/marketing/ tools/ | wc -l` → `0`.

## Spec Compliance Matrix

### `backoffice-admin-shell` (5 requirements, 8 scenarios)

| Requirement / Scenario                                                     | Implementation evidence                                                                        | Covering test (runtime)                                                                                    | Status                                                       |
|----------------------------------------------------------------------------|------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------|
| Shell Loads / Authorized admin opens shell                                 | `AdminLayout` renders nav from registry; guard chain untouched                                 | `nav-registry.spec.ts` mounts `AdminLayout` as `PLATFORM_OWNER`, asserts nav text renders without redirect | PASS                                                         |
| Shell Loads / Unauthorized principal is denied                             | `beforeEach` guard (`isAuthenticated → hasPlatformAccess → hasPermission`) unchanged           | No dedicated guard test (pre-existing gap, guard not modified by this change)                              | WARNING (pre-existing, out of scope)                         |
| Registry-Driven Navigation / Nav filtered by permission                    | `visibleNavEntries(hasPermission)` in `nav-registry.ts`, consumed by `AdminLayout`             | Mount as `SUPPORT_AGENT`/`AUDITOR` hides unpermitted entries                                               | PASS                                                         |
| Registry-Driven Navigation / Extensibility is one entry                    | Planned children generated from registry in `router/index.ts`; `design.md` Extensibility Guide | Structural: `plannedNavEntries()` + generated routes; no layout change per area                            | PASS                                                         |
| Direct-Invitations Nav Entry / Permitted principal sees direct-invitations | Live registry entry (`platform.invitations.read`) routing to existing view; route untouched    | Registry shape test + OWNER mount shows "Direct Invitations", SUPPORT_AGENT mount hides it                 | PASS                                                         |
| Inert Planned-Area Placeholders / Planned area shows planned state         | `PlannedAreaView.vue`: static `t('planned.message')` + area label; no store, no fetch          | Renders "planned and not yet available" + "Overview"                                                       | PASS                                                         |
| Inert Planned-Area Placeholders / zero fetch                               | Same view; `meta.permission` = nearest existing server-enforced key                            | `fetch` stubbed, asserted `not.toHaveBeenCalled()`                                                         | PASS                                                         |
| Inert Planned-Area Placeholders / Unpermitted planned area stays hidden    | Generated routes carry `meta.permission`, covered by unchanged `beforeEach`                    | Nav-filtering tests (hidden); direct-navigation denial relies on unchanged guard (see WARNING above)       | PASS WITH WARNING                                            |
| Localized Labels / Spanish labels render                                   | `nav.*` + `planned.message` in EN+ES (`i18n/index.ts`, typed via `types.ts`)                   | Key-existence asserted for both locales per entry; no mounted-ES render test                               | PASS (key coverage; mounted-ES render untested — SUGGESTION) |

Acceptance mapping (4 criteria): 1 PASS, 2 PASS, 3 PASS, 4 PASS (criterion 4 via mirror tests in
`auth.store.test.ts` + clean `admin-check`/`admin-build`).

### `admin-authorization` delta (2 ADDED + 1 MODIFIED)

| Requirement / Scenario                                                 | Evidence                                                                                                                                                                                                                               | Status                                                               |
|------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| Frontend Mirror Matches Server / Mirror includes publishing stale read | `auth.store.ts` appends `platform.publishing.stale.read` to OWNER + OPERATOR only; server `PLATFORM_ROLE_PERMISSIONS` grants it to exactly those roles                                                                                 | PASS (covered by `auth.store.test.ts`, 14 tests)                     |
| Frontend Mirror Matches Server / No implied permissions                | Planned entries reuse only existing server-enforced keys (`dashboard.read` for overview/notifications, `operators.read` for governance/configuration); enforced by `gates every planned entry by an existing platform permission` test | PASS                                                                 |
| Frontend Gating Is Additive Only / Frontend bypass attempt             | No backend diff; server `OperatorAccessResolver` default-deny untouched                                                                                                                                                                | PASS (by construction; server BDD suite not re-run — unchanged code) |
| Permission Registry Completeness / Registry audit                      | Main spec fixed: header "14 keys" + `platform.invitations.create` row added; matches `PlatformPermission.kt` (14 enum entries, `INVITATIONS_CREATE` enforced by `CreateInvitationHandler.kt`)                                          | PASS (implementation) / WARNING (docs — see issues)                  |

## Correctness Table

| Check                               | Result                                                                                                                             |
|-------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| `direct-invitations` nav entry      | Correct: live, `platform.invitations.read`, existing view untouched                                                                |
| Planned mappings                    | Per design: overview/notifications → `dashboard.read`; governance/configuration → `operators.read`; no invented permission strings |
| Mirror fix additive only            | Correct: 2-line append, no other array changes                                                                                     |
| Zero-fetch placeholders             | Correct: view imports only registry + i18n; fetch-mock test passes                                                                 |
| Guard untouched                     | Correct: diff shows only planned-children spread added to `router/index.ts`                                                        |
| Main spec reconciliation (task 2.6) | Correct: 14 keys + `invitations.create` row                                                                                        |

## Design Coherence Table

| Design decision                                        | Implementation                                                                    | Status                 |
|--------------------------------------------------------|-----------------------------------------------------------------------------------|------------------------|
| Registry in `src/router/nav-registry.ts`               | Created, matches specified interface                                              | Coherent               |
| One shared `PlannedAreaView.vue`                       | Created, static only                                                              | Coherent               |
| Generated planned children, guard untouched            | Implemented as specified                                                          | Coherent               |
| Nearest-existing-key gating                            | Implemented as specified                                                          | Coherent               |
| Additive mirror fix                                    | Implemented as specified                                                          | Coherent               |
| Extensibility (task 4.1)                               | `design.md` Extensibility Guide; no code comment (zero-comment policy forbids it) | Coherent               |
| Open question: 14 vs 13 count                          | Resolved in code + main spec; delta text still stale                              | WARNING — amend delta  |
| Open question: planned-permission mapping confirmation | Implemented per design; product confirmation still pending                        | WARNING — non-blocking |

## Issues

### CRITICAL

None.

### WARNING

1. **Delta spec text is factually wrong and must be amended before archive.**
   `specs/admin-authorization/spec.md` line 35 says the registry lists "all 13 keys"
   and "`platform.invitations.create` does not exist". Server truth (`PlatformPermission.kt` lines
   6–21, `CreateInvitationHandler.kt` lines 42–43):
   14 keys, `INVITATIONS_CREATE("platform.invitations.create")` exists and is enforced.
   The main spec was already corrected; the delta was not. Archive would propagate a
   false statement. Required amendment: rewrite the MODIFIED requirement to
   "all 14 keys … (header corrected from 15; `platform.invitations.create` row restored)".
2. **Main spec role-permission mapping table lacks the `platform.invitations.create` row**
   (13 rows for 14 keys). Server grants it to OWNER + OPERATOR (`PlatformPermission.kt` lines
   31–47). Required amendment: add the row (`✓ | ✓ | — | —`) in the same edit as (1).
3. **Planned-permission mapping (notifications → `dashboard.read`) awaits product confirmation.**
   Per design and harmless (additive, server-authoritative), but still open. Non-blocking.
4. **Unauthorized-redirect / direct-navigation-denial has no dedicated test.**
   Pre-existing gap: the guard predates this change and was intentionally untouched.
   Nav-hiding is tested; guard denial is not. Recommend a router-guard test as follow-up,
   not in this change.

### SUGGESTION

- Tighten shell spec wording: the planned-area parenthetical lists
  `overview/users/waitlist/invitations/.../audit`, but the implementation (correctly)
  keeps users/waitlist/audit/direct-invitations as live with real views and only
  overview/notifications/governance/configuration as planned. One-line clarification.
- Add a mounted-ES render assertion for nav labels (currently key-existence only).

## Verdict Table

| Finding                                                                            | Judge A | Judge B | Severity                            | Status    |
|------------------------------------------------------------------------------------|---------|---------|-------------------------------------|-----------|
| Delta spec line 35 contradicts server enum (13 vs 14, denies `invitations.create`) | ✅      | ✅      | WARNING (must-fix before archive)   | Confirmed |
| Main spec mapping table missing `invitations.create` row                           | ✅      | ✅      | WARNING (must-fix before archive)   | Confirmed |
| No dedicated unauthorized-redirect guard test                                      | ✅      | ❌      | WARNING (pre-existing, theoretical) | INFO      |
| Loose planned-area wording in shell spec                                           | ✅      | ❌      | SUGGESTION                          | Suspect   |

## Final Verdict

**PASS WITH WARNINGS** — implementation matches specs, design, and all 14 tasks with
real runtime evidence (44 tests, type-check, build, lint clean; zero out-of-scope diff).
Archive is **blocked until WARNING 1 + 2 amendments land** (small doc edits, no code change).
