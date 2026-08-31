## Exploration: Replace blocking consent modal with a non-modal banner and harden privacy browser behavior

### Current State

The app consent flow is currently implemented as a modal dialog. `ConsentBanner.vue` decides that it
should be visible when there is no valid receipt or when `forceOpen` is set, then renders `Dialog`/
`DialogContent`. The shared dialog portal adds a full-screen `DialogOverlay` (`fixed inset-0`,
`bg-black/80`, `z-50`) and the content is centered above it at `z-[51]`. This correctly fixed the
recent “dark screen with invisible dialog” stacking symptom, but the banner still captures focus and
blocks interaction with the authenticated app until the user makes a consent decision.

The banner saves either analytics=true or analytics=false through the existing Pinia consent store,
which persists a validated receipt to localStorage and asynchronously syncs authenticated users to
the governance API. `CookieSettings.vue` is a separate, user-invoked settings dialog and uses
`source: 'settings-panel'`; it can remain modal because it is not an unsolicited gate on app access.
`AppShell.vue` mounts both components globally and exposes a footer Cookie settings link.

The current `ConsentBanner` also inherits the shared dialog close button by default. Closing the
dialog can therefore hide the initial consent UI without writing a receipt, leaving the user in an
undecided state while bypassing the intended prompt. Existing component tests are coupled to dialog
semantics (`consent-dialog`, dialog stubs, and visibility assertions), so they will need to assert
non-modal rendering and continued page interaction instead.

The recent `DialogContent` change to `z-[51]` addresses browser-dependent paint order, especially
the Brave-only overlay symptom, but it is a mitigation for a modal implementation rather than a
solution to the UX requirement. Removing the unsolicited overlay eliminates that class of stacking
and browser paint failure for the initial banner.

### Affected Areas

- `apps/web/app/src/components/consent/ConsentBanner.vue` — replace the initial consent `Dialog`
  surface with a fixed, non-modal banner; retain the existing consent actions and accessible
  category controls.
- `apps/web/app/src/components/consent/ConsentBanner.spec.ts` — replace dialog-focused
  stubs/assertions with tests for visible non-modal rendering, accept/reject/custom-save
  persistence, and the absence of a blocking overlay/focus trap.
- `apps/web/app/src/components/consent/CookieSettings.vue` — verify the intentional settings dialog
  remains separate and continues to use the settings-panel source; only change it if shared
  extraction is needed.
- `apps/web/app/src/layouts/AppShell.vue` — verify global mounting and the footer settings entry
  point still work when the initial banner no longer uses the dialog portal.
- `apps/web/app/src/components/ui/dialog/DialogContent.vue` — likely no functional change required
  for this UX fix; its z-index hardening remains relevant to intentionally modal settings dialogs.
- `apps/web/app/src/components/ui/dialog/DialogOverlay.vue` — likely no functional change required;
  the initial banner should stop using this overlay rather than further tuning it.
- `apps/web/app/src/modules/settings/infrastructure/consent.store.ts` — preserve receipt validation,
  localStorage persistence, source tracking, DNT capture, and non-blocking backend sync; confirm the
  banner’s lifecycle still reflects `hasValidConsent` and `forceOpen` correctly.

### Approaches

1. **Fixed non-modal consent banner; keep settings dialog modal** — Render the initial consent
   prompt as an `aside`/section fixed to the bottom of the viewport with a high local z-index,
   responsive layout, safe-area padding, and no backdrop or focus trap. Keep `CookieSettings.vue` as
   the explicitly opened modal for later changes.
    - Pros: directly removes the blocking behavior and overlay paint-order failure; smallest change
      to the consent store and app shell; preserves the stronger modal treatment for deliberate
      settings edits; maps to the existing marketing/banner pattern.
    - Cons: requires careful responsive sizing and keyboard/focus behavior; the banner remains
      visible while users browse until they decide; tests and selectors need updating.
    - Effort: Medium

2. **Convert the initial prompt to a non-modal popover/drawer primitive** — Use a non-modal UI
   primitive with controlled open state and anchored/fixed positioning, while leaving
   `CookieSettings.vue` unchanged.
    - Pros: could provide more built-in dismissal and focus behavior than a raw section; potentially
      reusable for other lightweight notices.
    - Cons: adds primitive complexity without needing trigger/anchor semantics; many popover
      implementations still manage focus or dismissal in ways that are wrong for an undecided
      consent prompt; does not materially improve on a semantic fixed banner.
    - Effort: Medium/High

3. **Keep the modal and only harden stacking/browser behavior** — Retain the full-screen dialog,
   further adjust z-index/portal and browser-specific CSS, and prevent accidental close without
   consent.
    - Pros: smallest visual code change; existing dialog tests and accessibility behavior largely
      remain usable.
    - Cons: does not satisfy the core requirement that consent must not block app use; remains
      vulnerable to modal-overlay regressions and creates a poor authenticated-app experience;
      browser-specific fixes would be compensating for the wrong interaction model.
    - Effort: Low/Medium

### Recommendation

Choose Approach 1. The unsolicited first-run consent prompt should be a semantic, fixed non-modal
banner with explicit Accept, Reject, and Save controls; it must not render `DialogOverlay`, trap
focus, or expose a dismiss action that leaves the receipt undecided. Preserve `CookieSettings.vue`
as the intentional modal entry point and preserve the existing Pinia/store contract, including
`source`, version validation, DNT/GPC-derived defaults, local persistence, and fire-and-forget
backend synchronization. Update tests to prove that the banner is visible without an overlay and
that the rest of the app remains interactable while it is present.

The existing dialog z-index fix should remain for `CookieSettings.vue` and other dialogs, but it
should not be treated as the implementation of this change. Browser hardening should focus on
eliminating the initial overlay and validating the banner in browsers where the overlay symptom was
observed, especially Brave, plus keyboard and narrow viewport behavior.

### Risks

- A non-modal banner can cover bottom-of-viewport controls; positioning, responsive layout,
  safe-area insets, and a sufficiently high but scoped z-index need explicit verification.
- If the banner can be closed through an inherited close affordance or Escape handling, users may
  remain without a valid receipt; undecided state must not be silently treated as a decision.
- The current `forceOpen`/`open` behavior may become redundant or split between `ConsentBanner` and
  `CookieSettings`; the proposal should define which component owns re-opening after the footer
  settings action.
- Removing the dialog portal changes test selectors and may expose assumptions in E2E fixtures that
  currently seed consent to avoid a blocking overlay.
- Backend sync is asynchronous and should remain non-blocking; UI completion must not wait on the
  governance API or turn a sync failure into a consent-save failure.

### Ready for Proposal

Yes — the core direction is clear. The proposal should define the non-modal banner’s responsive
placement, keyboard semantics, whether Escape is ignored while consent is undecided, and the browser
verification matrix (at minimum Chromium and Brave-like overlay/stacking coverage). It should
explicitly scope `CookieSettings.vue` as an intentional modal unless product requirements say all
consent settings must also be non-modal.
