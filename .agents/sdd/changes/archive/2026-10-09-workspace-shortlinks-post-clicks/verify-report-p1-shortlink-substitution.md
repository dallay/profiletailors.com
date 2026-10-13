# Verification Report — P1 Shortlink Substitution

## Change and scope

- Change: `workspace-shortlinks-post-clicks`
- Scope: Re-verify the P1 correction in `CreatePostModal.vue` after QA found that selecting Use/Keep did not update the visible composer text.
- Verification mode: `fallback`. `sdd-quality-runner` was unavailable; deterministic runner envelopes were not available.
- Verdict: **PASS WITH WARNINGS** for the P1 behavior and relevant local checks. This is technical verification only, not acceptance QA.
- No implementation was edited. No publication, external send, data mutation, commit, push, or archive was performed.

## Evidence and checks

| Check | Result | Evidence |
|---|---|---|
| Composer focused tests | PASS | `pnpm exec vitest run src/modules/publishing/presentation/components/CreatePostModal.test.ts`; 1 file, 67 tests passed. |
| Dashboard type check | PASS | `pnpm --filter app type-check`; `vue-tsc --build` exited successfully. |
| Diff whitespace check | PASS | `git diff --check` scoped to the composer source/test and change artifacts; no output, exit 0. |
| Full app lint/build/test suite | NOT RUN in this verification | Existing phase-3-slice-2 report records prior complete app checks, but they are not evidence of the newly requested P1 regression beyond the fresh focused test and type check above. |
| `sdd-quality-runner` | UNAVAILABLE | Used fallback verification; unavailable is not a pass. |
| Remote CI / deployed behavior | NOT RUN | No remote or deployed evidence claimed. |

## P1 behavioral compliance

| Required behavior | Result | Runtime evidence |
|---|---|---|
| Choosing **Use shortlink** updates the text visible in the composer | PASS | Focused Vitest regression test asserts the textarea contains `https://pt.link/a` immediately after the Use action. |
| Choosing **Keep original** restores the selected original URL in the visible text | PASS | Same test asserts the textarea contains `https://example.com/article` after Keep original. |
| A distinct, unselected URL is preserved | PASS | Test starts with two URLs and asserts `https://example.com/other` remains after both choices. |
| Use → Keep original → Use works repeatedly | PASS | Same test performs that exact interaction sequence and checks the textarea after each action. |
| Saving submits the currently visible content | PASS | Test schedules after the final Use and asserts `schedulePost` receives `Read https://pt.link/a and https://example.com/other`. |

Source evidence: `CreatePostModal.vue`'s `chooseShortlink` transforms `postText.value`, the same text rendered by the composer; the focused test `requires an explicit choice before publishing a created shortlink and replaces only its target URL` covers the interactions and final submit payload. The result therefore verifies the visible text and persisted/scheduled payload agree for this flow.

## Correctness and design

| Area | Assessment |
|---|---|
| Spec | The verified interaction aligns with `specs/publishing-shortlinks/spec.md`: no silent substitution; explicit choice; substitution limited to the selected URL; final body reflects the user choice. |
| Design | The correction writes the chosen URL back into the existing composer text state rather than presenting a separate display-only preview. Save continues to consume composer content. No architectural deviation observed for this P1 fix. |
| Tasks | Phase 3 Slice 2 implementation tasks were already marked complete. This re-verifies the P1 scenario only; it does not establish acceptance QA completion. |

## Issues and risks

### CRITICAL

- None for the P1 composer behavior covered by the passing regression test.

### WARNING

- Publisher mock-only routing remains **unproven**. The existing QA report documents that mock-exclusive routing evidence was not established. This verification did not run publisher routing QA and does not authorize publication, external sends, or a claim that publisher calls are mock-only.
- Acceptance QA remains blocked/pending its rerun after the P1 correction. The separate workspace-member reread gap recorded in `qa-report.md` is outside this narrow P1 check and remains unresolved unless QA demonstrates otherwise.
- Full dashboard test/lint/build were not rerun for this P1 verification. Do not interpret prior slice evidence as fresh evidence for this rerun.

### SUGGESTION

- Re-run `sdd-qa` after this verification, retaining the no-publish/no-external-send constraint and explicitly capture mock-only publisher routing proof before any publication authorization.

## Verdict

**PASS WITH WARNINGS** for P1 shortlink substitution and the fresh focused tests/type check. **Not acceptance sign-off and not authorization to publish.** Handoff to `sdd-qa` for acceptance scenarios.
