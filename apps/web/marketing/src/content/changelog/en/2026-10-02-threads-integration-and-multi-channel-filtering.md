---
slug: 2026-10-02-threads-integration-and-multi-channel-filtering
locale: en
date: "2026-10-02"
version: "v0.3.14"
title: "Threads integration and multi-channel calendar filtering"
summary: "Connect personal Threads profiles alongside LinkedIn and filter your week, day, month, and list views by channel."
categories:
  - feature
image: /changelog/threads.svg
imageAlt: "Preview of the Profile Tailors sidebar showing LinkedIn and Threads channels with queued counts next to a filtered week calendar view."
---

## Threads personal profile connection

Threads joins LinkedIn as the second integrated platform in early access. You can initiate personal profile OAuth from Settings or directly from the sidebar Channels section.

- Provider OAuth callbacks stay isolated from human authentication flows.
- Publishing remains gated while platform integrations complete early-access validation.

## Channel filtering and queued counts

The sidebar now displays live queued counts per connected provider and lets you switch between all channels or a single account without losing your current calendar view.

- Filter week, day, month, and list surfaces by one or more channels.
- URL query state preserves your active channel filter when reloading or sharing a workspace link.
