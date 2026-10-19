# Composer interaction definition

## Overview

Conceptual UX exploration for the Profile Tailors post composer. This document defines
interaction behavior only. It proposes no visual redesign: the existing `CreatePostModal`
structure, dimensions, and design system stay as the visual reference. Buffer is the
interaction reference; `composer-prototype.html` is the behavior exploration tool.

## Changes

## Goals

- Give multi-channel composition a predictable progression: Compose, Customize, Review.
- Separate what the user is doing (workflow stage) from what the user is editing
  (editing context) and what the user is previewing (preview context).
- Keep customization optional: valid base content can ship to every channel untouched.
- Replace premature blocking errors with contextual validation plus a final gate.

## Non-goals

- No visual restyle of the modal, header, editor, or preview panel.
- No channel-selector compaction (Buffer-style avatars stay a future refinement).
- No relocation of scheduling out of its current footer placement.
- No new platform integrations, AI behavior, or hashtag behavior.

## Concepts

### Workflow state

The stage the user is in. Navigation is free in both directions; the stepper is a
flexible progress indicator, never a rigid wizard.

- `COMPOSE` — write shared base content and pick accounts. Entry point.
- `CUSTOMIZE` — optional. Edit per-account variants. Reachable from Compose and Review.
- `CUSTOMIZE` is skipped automatically when there is nothing to customize
  (single account, or base already valid everywhere).
- `REVIEW` — verify final per-account content and confirm. The only blocking gate.

### Editing context

Which content the editor is modifying. Independent of workflow state.

- `BASE` — the shared text. Editing it updates every account that still inherits it
  and never touches customized variants.
- `ACCOUNT_VARIANT(<channelId>)` — one account's independent text.
- Selecting an account tab does not fork a variant. The first edit does.
- Resetting a variant restores inheritance and asks for confirmation first.
- Adding an account initializes it from Base. Removing an account with a customized
  variant asks for confirmation; cancelling keeps everything.
- Switching tabs preserves text, caret position, and scroll.
- One account equals one variant slot, even for two accounts on the same network.

### Preview context

Which account the preview panel shows. Independent of the editing context.

- Follows the editing context by default whenever it changes.
- The user can point it at another account without altering the editor.
- Returning to `BASE` keeps the last previewed account.

## Variant rules

| Action | Expected behavior |
|---|---|
| Write in Base | Updates inheriting variants only |
| First edit on an account tab | Forks an independent variant |
| Edit Base afterwards | Never overwrites customized variants |
| Reset a variant | Returns to Base after confirmation |
| Add an account | Starts from current Base |
| Remove an account | Confirms when a customized variant exists |
| Switch tabs | Preserves content, caret, and scroll |
| Validate | Evaluates each variant against its own network limits |
| Change preview | Never changes the editing context |

## Mapping to the current modal

| Element | Decision |
|---|---|
| Modal shell | Keep structure and dimensions |
| Header | Keep title and close; add a discreet stepper that does not overload it |
| Channel selector | Keep current chips; compact avatars stay future work |
| Customize control | Becomes a navigation entry into `CUSTOMIZE`, not an on/off preference |
| Variant tabs | Keep Base plus one tab per account with remaining/limit and a customized marker |
| Editor | Stays the primary left element with a stable position across stages |
| Preview | Stays right; follows the editing context by default, overridable independently |
| Scheduling | Stays in the current footer placement; `REVIEW` validates, it does not relocate it |
| Validation | Contextual counters while writing; blocking errors only at the confirm gate |
| Accordion | Reserved for future per-platform advanced settings |

## Open decisions

- Confirm per-account variants as the durable model. The implementation already keys
  overrides by channel id, so per-network grouping would be a behavior change, not a rename.
- Stepper placement and exact discreet treatment in the header.
- Whether the independent preview control is an explicit selector or an implicit affordance.
- Location of the Tags and Templates controls referenced for the header; they were not
  found in the current composer surface during verification.

## References

- [Composer interaction prototype](./composer-prototype.html)
- [Design Sources](./README.md)
- `apps/web/app/src/modules/publishing/presentation/components/CreatePostModal.vue`
