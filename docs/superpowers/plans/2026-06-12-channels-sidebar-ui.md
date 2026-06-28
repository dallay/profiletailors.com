# Channels Sidebar UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this
> plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move channels into the primary app sidebar, make them scannable and actionable, and keep
the scheduler calendar as the main work surface.

**Architecture:** The publishing Pinia store remains the source of truth for channels and
publications. `App.vue` derives sidebar channel rows, queue counts, and filter behavior from the
store. `SchedulerView.vue` removes its duplicate channel rail so the calendar can use the available
width.

**Tech Stack:** Vue 3 Composition API, Pinia, vue-router, vue-i18n, Tailwind CSS v4, lucide-vue,
Vitest.

---

## Overview

The approved UI direction is the hybrid of option A and C from the visual companion: Buffer-like
channel visibility in the global sidebar, with Profile Tailors' monochrome industrial treatment and
room for a future right-side inspector. This first implementation focuses on the sidebar channel
surface and scheduler layout cleanup.

## Changes

### Task 1: Store-Derived Channel Sidebar Data

**Files:**

- Modify: `apps/web/app/src/App.vue`
- Test: `apps/web/app/src/stores/publishing.test.ts`

- [ ] Add a typed sidebar channel presenter in `App.vue` that maps each `publishingStore.channels`
  entry to queue counts derived from `publishingStore.publications`.
- [ ] Add connect-channel presenter rows for Threads, Bluesky, Facebook, and More channels.
- [ ] Add `selectChannel(channel)` and `clearChannelFilter()` handlers that update
  `publishingStore.filterChannel` and `publishingStore.filterSocialAccountId`, then route to
  `/scheduler`.

### Task 2: Global Sidebar Channels UI

**Files:**

- Modify: `apps/web/app/src/App.vue`
- Modify: `apps/web/app/src/i18n/index.ts`

- [ ] Replace the empty `Channels` nav group with an "All channels" row, connected channel rows, and
  compact connect actions.
- [ ] Show avatar, provider badge, channel identity, connection status, and queued count.
- [ ] Keep the layout usable when the sidebar is collapsed by preserving icon-only buttons and
  tooltips.
- [ ] Add EN/ES strings for channel labels, statuses, and connect actions.

### Task 3: Scheduler Calendar Width

**Files:**

- Modify: `apps/web/app/src/views/SchedulerView.vue`

- [ ] Remove the duplicate in-page channel manager rail.
- [ ] Let calendar and list modes fill the main content width.
- [ ] Remove imports that are no longer needed after the rail is removed.

### Task 4: Verification

**Files:**

- Test: `apps/web/app/src/stores/publishing.test.ts`

- [ ] Run `pnpm --filter app test:run`.
- [ ] Run `pnpm --filter app build`.
- [ ] Start the app and inspect the scheduler in the browser at the local dev URL.

## Usage

Open the app, go to Scheduler, and use the Channels section in the left sidebar. "All channels"
clears channel filtering. Clicking a connected channel filters scheduler publications for that
provider/account and keeps the calendar as the central view.

## Troubleshooting

If sidebar counts look wrong, inspect `publishingStore.publications` in local storage because mock
publications are persisted. If no channel row filters posts, confirm the publication `channels`
array uses the same provider key as the channel provider.

## References

- `.agents/DESIGN.md`
- `.agents/skills/frontend-platform/nothing-design/SKILL.md`
- `apps/web/app/src/App.vue`
- `apps/web/app/src/views/SchedulerView.vue`
