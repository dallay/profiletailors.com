---
target: apps/web/app/src/modules/settings/presentation/SettingsView.vue
total_score: 25
max_score: 40
na_heuristics: 
p0_count: 0
p1_count: 3
p2_count: 2
timestamp: 2026-09-07T05-45-01Z
slug: src-modules-settings-presentation-settingsview-vue
---
# Settings (Operate)

## Design Specificity Verdict

**LLM assessment — half-authored, half-stock.**
The skeleton is unmistakably Profile Tailors: monochrome, dark-first, mono-uppercase eyebrows, `--bg-surface` cards with 1px hairline borders, dashed empty states, rounded-2xl cards, dot-pill status badges, segmented EN/ES radiogroup, and the distinctive `overviewBadge` pill with a leading 1.5px dot. The chrome reads PT. The information inside it does not.

What gives the page away as a settings page rather than a PT settings page:

- The `h1` for the route reads "Settings" in 11px mono caps — a navigation label, not a page title. PT's own marketing system reserves 24/36/48/72px for display, and the dashboard's own `AppHeader` elevates route names. The route's primary title is the smallest, lightest text on the page.
- The hero reads as two equal-weight panels (`Channels` and `Workspace`) side-by-side at `xl`, with the language switcher floating in the top-right as if it were a primary action. Operate surfaces in opinionated tools (Linear, Things, Notion, Arc) lead with one identity, not a constellation of cards.
- Copy is functionally correct but generic. `Manage connected channels, workspace identity, and interface preferences.` is the line Buffer would ship.
- The destructive `Account Closure` panel uses a soft `border-error/40 bg-error/[0.03]` wash that reads as "a card with a faint pink tint," not a high-stakes zone.

**Deterministic scan — no findings.** The CLI detector returned an empty JSON array (`exit_code 0`) on the SettingsView file. The shipped rule set does not cover the patterns the LLM flagged (page hierarchy, copy, emphasis, destructive treatment, IA). Treat the empty result as "no findings within the loaded rule set," not as a clean bill of health. The detector also exited 0 with no rule-id telemetry, so we cannot distinguish a true clean from an under-scanned file. Run `$impeccable audit` for the wider set if numeric confirmation is needed.

**Visual overlays** — not available. Browser navigation to `http://localhost:5173/settings` returned 404, and `https://pt-app.localhost/settings` is blocked by the untrusted local certificate. No overlay was injected. The critique is based on source review of `SettingsView.vue`, the composed `PrivacySection.vue` and `AccountClosureSection.vue`, the locale strings, and the design tokens in `apps/web/app/src/assets/main.css` / `.agents/DESIGN.md`.

## Design Health Score

Max: **40** (all 10 heuristics apply on Operate).

| # | Heuristic | Score | Key Issue |
|---|-----------|-------|-----------|
| 1 | Visibility of System Status | 3 | Channel-status pill works; rename-success and LinkedIn success toasts are present; no global "Saving…" indicator while the rename request is in flight. |
| 2 | Match System / Real World | 3 | Mono eyebrows, dot-pill, segmented switch are PT-native. The page-level copy drifts toward generic dashboard English. |
| 3 | User Control and Freedom | 3 | Rename supports Escape and Cancel; destructive closure requires typed `DELETE`. No undo on rename. |
| 4 | Consistency and Standards | 2 | Channels and Workspace are `minmax(0,0.9fr)_minmax(0,1.1fr)` siblings at xl, but Account Closure (also a Card) sits alone below with no peer, and Privacy is also alone. Vertical rhythm depends on visual scan order, not on a real layout grid. |
| 5 | Error Prevention | 3 | Workspace name uses Zod schema; closure requires `DELETE` typed. Connect-button error is shown only after attempt with no client-side preflight. |
| 6 | Recognition Rather Than Recall | 2 | Section headers are mono-uppercase 10px eyebrows; they read as metadata, not as labels a user scans to find a section. Privacy is the only one with an obvious entry signal. |
| 7 | Flexibility and Efficiency | 2 | No keyboard shortcuts, no `?` help, no quick-jump within the page. Settings lives behind one card grid; a sidebar nav would be faster for repeat visits. |
| 8 | Aesthetic and Minimalist Design | 3 | PT identity is intact; minimalism is honest. The hero is over-cluttered: badge + h1 + subtitle + language card compete in the same row. |
| 9 | Error Recovery | 2 | Rename shows `renameError` inline; closure shows `errorMessage` inline. DSAR errors are swallowed (`catch { /* Error is handled by the store */ }`) — a user submitting a failed DSAR gets a list refresh, not a message. |
| 10 | Help and Documentation | 2 | No tooltips, no inline help on the `accountId` slug, no copy explaining the consequences of "Close Account" beyond the destructive button label. |
| **Total** | | **25 / 40** | **Operate-surface pass with hierarchy and copy debt.** |

## What's Working

- **PT visual identity is load-bearing.** The mono-uppercase 10–11px eyebrows, dot-pill status, hairline 1px borders, dashed empty states, and rounded-2xl cards read PT at a glance. The `overviewBadge` pill with a 1.5px leading dot is genuinely distinctive.
- **Destructive-action design pattern.** Close Account demands `DELETE` typed in the input, and the rate-limited 429 path returns its own message. This is the right shape for irreversible action.
- **Locale + theme already in scope.** The EN/ES segmented control with `role="radiogroup"` and `sr-only` inputs is correct; the active segment uses `bg-bg-primary text-text-display` which keeps it readable against the surface.

## Priority Issues

### [P1] Page-level hierarchy is inverted

- **What**: The route title (`h1`) is 11px mono uppercase; the cards beneath it carry no consistent visual hierarchy; the language switcher (a preference, not a primary action) sits at the top-right as if it were the most important thing on the page. There is no visible "settings overview" anchor — the user is dropped into a wall of cards.
- **Why it matters**: A first-time visitor cannot tell at a glance what this page does, where to start, or where dangerous actions live. Repeat users have to re-scan the page every time because nothing is prioritized.
- **Fix**:
  - Promote the page title to `text-display-lg` (24px Space Grotesk Medium) or larger and make the route name a mono eyebrow above it.
  - Move the EN/ES switcher inside a single "Preferences" card, lower in the page, next to the theme control (if any), so the hero is about *identity and channels* — the two things that affect what users see and post.
  - Replace the two-card hero with a single hero strip that names the workspace (with avatar and rename affordance), shows connection state (LinkedIn active / needs reconnect / not configured), and points to the channels card. Workspace identity and channel state are the same idea from two angles; merging them removes a parallel decision point.
- **Suggested command**: `$impeccable layout` then `$impeccable typeset`.

### [P1] Default `bg-error/[0.03]` on Account Closure reads as decorative, not destructive

- **What**: The destructive panel uses `border border-error/40 bg-error/[0.03]` — a faint pink tint that does not separate from a normal card visually. The 10px mono "Close Account" eyebrow is the same size as non-destructive eyebrows.
- **Why it matters**: A user landing on this page could mistake the closure panel for a "Settings → danger zone" decoration rather than a terminal, irreversible action. The high-stakes moment should be visually unambiguous before the user reaches for the `DELETE` confirm.
- **Fix**:
  - Give the panel a real border treatment (`border-error/60` minimum) plus an icon and a one-line consequence statement before the description ("This deletes your account, all workspaces, all OAuth tokens, and all published posts. LinkedIn must be disconnected separately.").
  - Lift the eyebrow to `text-[12px]` or use a small icon-on-the-left pattern (`AlertTriangle` 14px) so the eyebrow reads as a warning, not metadata.
  - Keep the typed-`DELETE` confirm; add an explicit "I understand this is permanent" checkbox.
- **Suggested command**: `$impeccable bolder` scoped to the closure section.

### [P1] Information architecture is flat — no entry map, no grouping

- **What**: Five cards on one page (`Channels`, `Workspace identity`, `Privacy & Data`, `Close Account`, plus the floating language switch) with no nav, no anchor list, no visible "danger zone" separation. Sections are siblings of each other, which implies equal weight.
- **Why it matters**: Settings works best when it leads a user to the right answer fast. Putting channels next to closure flattens the risk gradient. A first-time user who needs to connect LinkedIn has to scan past "Close Account" to find the connect button.
- **Fix**:
  - Adopt a two-column layout with a left-side nav (`Channels`, `Identity`, `Preferences`, `Privacy`, `Danger zone`). The nav itself is PT-styled (mono eyebrow + dot markers) and the page scrolls to anchors.
  - On mobile, replace the sidebar with a top horizontal segmented control scoped to the same five sections.
  - Group Privacy and Closure under an explicit "Data & account" cluster so the destructive section reads as *off to the side* of everyday controls.
- **Suggested command**: `$impeccable layout`.

### [P2] Copy is generic and the locale variant proves it

- **What**: `Manage connected channels, workspace identity, and interface preferences.` is the kind of line that ships on every social tool. PT's own voice is sharper (e.g. `Build your publishing system without dashboard chaos.`). The Spanish locale strings (`Gestionar canales conectados, identidad del espacio de trabajo y preferencias de interfaz.`) translate the same generic line.
- **Why it matters**: On a settings page, copy is the only voice. The user is not distracted by a hero image — they're reading text to figure out what to do. Generic copy erodes trust in the rest of the product.
- **Fix**:
  - Rewrite subtitle to: `Connect a channel, name your workspace, change the surface.` (or whatever the actual product action verbs are). Keep it under 60 chars.
  - Make every section title say what the user does, not what the system is: `Workspace`, `Channels`, `Language`, `Privacy requests`, `Close account`. Strip the trailing nouns.
  - Add a one-line consequence statement under each destructive or rate-limited action ("Closing your account deletes all workspaces, posts, and OAuth tokens. LinkedIn must be revoked separately on LinkedIn.").
- **Suggested command**: `$impeccable clarify`.

### [P2] Privacy section swallows errors

- **What**: `PrivacySection.vue`'s submit handler catches everything and silently falls through: `try { ... } catch { /* Error is handled by the store; the error state displays via the list or inline. */ }`. The store's error state is not surfaced to the user on submit; only `submitSuccess` toggles. A user who fails a DSAR submission sees the green success message clear after 5s and the list unchanged, with no explanation.
- **Why it matters**: this is a privacy/data-subject access flow. GDPR-style requests need to be confirmed or rejected, not silently dropped. The current behavior is an accessibility and trust failure (screen reader users, in particular, get nothing).
- **Fix**:
  - Read `privacy.error` after the call and surface it in the section (`role="alert"`, `aria-live="polite"`).
  - Show the error inline under the form, in PT's `text-error` style with a mono-eyebrow label (`REQUEST FAILED`).
  - Only flip `submitSuccess` on a real success response from the store.
- **Suggested command**: `$impeccable harden`.

## Persona Red Flags

**Alex (Power User, daily operator):**

- The page has no keyboard shortcuts and no command-palette entry. The route itself is fine, but the actions inside (rename workspace, switch language, open icon picker) require mouse movement. No `?` help.
- No way to copy the workspace `workspaceId` (the slug is rendered as mono caps text). Power users want to grab it for support tickets or API calls.
- No bulk disconnect / reconnect. If two channels exist, the user has to repeat the same flow per channel.

**Jordan (First-Timer, just finished OAuth):**

- The hero shows two equal-weight cards. A new user who just connected LinkedIn cannot tell whether the channel is "good" or "needs more" — they have to read the connection card to find the status pill.
- The rename affordance lives inside the Workspace card under "Edit Identity" *and* "Rename" — two buttons for the same workspace. A first-timer will hesitate: are they different?
- The "Close Account" panel sits next to everyday controls and uses the same pink tint as a hover. A first-timer who is looking for "settings" might tap into it accidentally while learning.

**Maria (Privacy-Conscious Operator, EU early-access):**

- The DSAR form lives inside a card with no visible success/error feedback on failure (see Privacy section swallows errors).
- The `accountId` shown next to each channel is the LinkedIn numeric id. There is no plain-language explanation of what it is or why PT stores it. Privacy-conscious operators will pause here.
- There is no `Last updated` / `Connected on` timestamp on the channel list. PT's data subject story depends on timestamps, and the channel rows do not surface them.

## Minor Observations

- **No `aria-live`** on the rename-success `output`. SR users may miss the rename confirmation.
- **LinkedIn success toast lives in the cards panel** and reads on the same card surface as the disconnect button. If a user reads top-to-bottom, the toast disappears into the channel list and disappears from `linkedinConnected` once the user navigates.
- **Theme control is missing.** `interfacePreferences` copy exists in the locales (`themeLabel`, `themeDesc`, `themeDark`, `themeLight`) but the Settings page does not render a theme selector. Theme lives in the AppHeader toggle. If the Settings page owns "Interface preferences," it must own the theme control.
- **Sebtext reuses `WorkspaceAvatar`** for the workspace identity avatar in the card header, but there is no contrast fallback when the avatar background is light in dark mode.
- **The two mono eyebrows** (`Channel status` in en and `Workspace identity`/`themeLabel`) appear at the same size and weight, so they read as the same kind of metadata even when one is a section title and the other is a setting label.
- **Rename success uses `output`** with `class="block rounded-2xl ..."`. `<output>` is the right semantic, but the rounded-2xl style with green text and border is similar enough to the LinkedIn success that screen readers using the accessible name alone will conflate them. Use distinct eyebrow text: `WORKSPACE UPDATED` vs `LINKEDIN CONNECTED`.
- **`connectedChannels.length`** is checked instead of an explicit empty/loading tri-state. Loading renders the loading text but does not hide the empty state; during the first paint, both empty and loading are technically shown, but the conditional `v-if/v-else-if/v-else` order saves it. Still, the loading state should override the empty state for accessibility.
- **`overviewBadge`** is a great name, but the 10px mono caps + leading dot visually competes with the route title's 11px mono caps. Differentiate one of them — usually by elevating the route title.
- **The two-column hero at `xl`** drops to single-column at `lg`, but `minmax(0,0.9fr) / minmax(0,1.1fr)` will hold the proportions oddly if the language card shrinks or grows. Validate at 1024, 1280, 1440, and 1920 widths.
- **Privacy form copy** ("Type DELETE to confirm" pattern is missing here, but) the request-type selector uses `placeholder="Select a request type"` — a placeholder is not a label. Pair it with a visible label or use a real label + placeholder pattern.

## Questions to Consider

- **What if the page led with a single sentence and a single primary action — "Connect a channel to start publishing" or a "Workspace is connected" status?** Settings doesn't need a hero; it needs a verdict.
- **What if the language switcher lived in the sidebar** (where users *find* it every time), not on this page? The top of Settings is prime real estate.
- **What if "Close Account" were one click further** — nested under `Workspace > Danger zone`, gated behind re-auth?
- **What would it look like to remove the subtitle entirely** and let the cards speak for themselves?
- **What if every section header said the action, not the noun?** `Connect a channel` instead of `Channels`. `Name your workspace` instead of `Workspace identity`. The PT voice is action-forward in marketing; settings should match.

## Run Notes

- Target slug: `src-modules-settings-presentation-settingsview-vue` (from `critique-storage.mjs slug`)
- Ignore list: none (`.impeccable/critique/ignore.md` does not exist)
- Assessment independence: A (sdd-explore, design review) and B (sdd-explore, detector evidence) ran in isolated sub-agents without shared output until synthesis.
- CLI detector: ran with `node ".agents/skills/impeccable/scripts/detect.mjs" --json <target>`; exit 0; empty findings JSON. The empty result is reported as "no findings within the loaded rule set," not as a clean bill of health.
- Browser visibility: not attempted in the second pass (local-cert blocker on `pt-app.localhost`); first pass reported 404 on `http://localhost:5173/settings` and the same cert blocker on Portless.
- Overlay injection: not attempted (no live server reachable).
- Live server: not started.
- Temp-file cleanup: not applicable; no temp body file written for this snapshot since persistence uses the helper's pipe path. Snapshot written via `critique-storage.mjs write`.
