---
slug: 2026-10-09-workspace-shortlinks-and-click-analytics
locale: en
date: "2026-10-09"
version: "v0.3.18"
title: "Workspace shortlinks and click analytics"
summary: "New posts automatically shorten distinct URLs on submission, with non-blocking failure warnings and paginated workspace click analytics."
categories:
  - feature
  - improvement
image: /changelog/shortlinks.svg
imageAlt: "Preview of Profile Tailors workspace shortlinks showing an original URL converted into a short link alongside total clicks and active link counters."
---

## Automatic URL shortening on new posts

When you create and submit a new publication in the composer, Profile Tailors now detects distinct URLs in your copy and replaces them with workspace-scoped shortlinks automatically.

- Shortening is best-effort: if a link fails to shorten, the original URL stays intact and the composer shows a non-blocking warning.
- Existing publications are never rewritten when edited, keeping published copy and historical redirects predictable.

## Paginated workspace click analytics

Every shortened link records redirect counts inside your workspace. You can review link performance from the authenticated Shortlinks view with server-backed pagination and clear per-link totals.

- Review total clicks and active shortlinks without leaving the dashboard.
- All redirect counts remain scoped to your active workspace and stored in our EU Frankfurt infrastructure.
