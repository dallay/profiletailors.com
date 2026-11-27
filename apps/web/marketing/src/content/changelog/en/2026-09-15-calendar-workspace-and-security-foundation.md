---
slug: 2026-09-15-calendar-workspace-and-security-foundation
locale: en
date: "2026-09-15"
version: "v0.3.6"
title: "Calendar workspace and BFF session security"
summary: "Plan LinkedIn content across week, day, month, and list views backed by Keycloak BFF authentication and HttpOnly session cookies."
categories:
  - feature
  - security
image: /changelog/calendar-security.svg
imageAlt: "Overview of the Profile Tailors publishing pipeline alongside BFF HttpOnly session security indicators."
---

## Calendar-first scheduling workspace

The core early-access workspace brings drafts and scheduled posts into one scannable calendar with week, day, month, and list surfaces.

- Create, edit, delete, and reorder planned posts from a single focused interface.
- Built to WCAG 2.2 Level AA contrast targets in both dark and light modes.

## Keycloak-backed BFF authentication

Human browser sessions now authenticate through a dedicated Backend-for-Frontend flow so provider tokens never enter browser JavaScript.

- Authenticated state relies on HttpOnly session cookies with CSRF protection on unsafe requests.
- Governance takedown workflows and workspace audit trails operate on canonical UUID identifiers.
