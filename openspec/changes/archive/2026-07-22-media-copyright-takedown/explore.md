# Copyright/Attribution/Takedown Workflow for Media Assets (DALLAY-499)

## Overview

This document captures an exploration of the current state of media asset
attribution, the gaps that prevent license-compliant display and takedown
handling, and the architectural seams that constrain how those gaps should be
addressed.

## Current State

### Attribution Data in Domain Model

The `MediaAsset` domain model already carries full attribution fields:

- `sourceType: MediaSourceType` — `UPLOADED`, `UNSPLASH`, `TENOR`, etc.
- `sourceProvider: String?` — e.g. `"unsplash"`
- `externalId: String?`
- `sourceUrl: String?`
- `authorName: String?`
- `authorUrl: String?`
- `metadata: JsonNode?` — download location, etc.

These flow through the API via `MediaAssetResponse` and `MediaAssetSummary`
DTOs.

### CRITICAL — Database Schema Ambiguity

- Changelog `005-add-external-metadata.yaml` **adds** `source_provider`,
  `external_id`, `source_url`, `author_name`, `author_url`, and `metadata`
  (jsonb) columns with NOT NULL + CHECK constraints
- Changelog `006-drop-external-metadata.yaml` **drops** all those columns
- The domain models and DTOs still reference these fields, meaning either:
    - The drop was never applied, OR
    - The domain model is out of sync with the schema

### Unsplash Integration

- `UnsplashMediaProviderHandlers.persistPhoto()` maps `UnsplashPhoto` →
  `MediaAsset` with `sourceProvider = "unsplash"`, preserving `sourceUrl`,
  `authorName`, `authorUrl`, and `downloadLocation` in `metadata`
- Unsplash API guidelines require: hotlinked URLs (not re-hosted), download
  endpoint tracking, attribution to both Unsplash and photographer with link-back

### Current Authorization Model

- `PermissionKey` format: `<domain>:<resource>:<action>` (e.g.,
  `workspace:consent:read`)
- Authorization via `WorkspaceAuthorizationDecider` interface, implemented by
  `WorkspaceAuthorizationService`
- Media endpoints currently use `AuthFeature.UPLOAD_MEDIA` for email
  verification gating — no workspace-level permission checks for media reads
- Governance consent controller shows the established pattern with
  `workspace:consent:read`/`write`

### Media Status Lifecycle

Current statuses: `PROCESSING`, `PENDING_UPLOAD`, `UPLOADING`, `READY`,
`FAILED`, `DELETED`.
No `SUSPENDED`, `RESTRICTED`, or similar moderation status exists.

### Frontend (Vue 3)

- `MediaLibraryView.vue` displays only filename, file size, status per asset
- Attribution fields (`authorName`, `authorUrl`, `sourceUrl`,
  `sourceProvider`) are NOT rendered anywhere in the UI
- The API returns them though — they're invisible but transmitted

### Notification Infrastructure

- `EmailSender` interface with `send(email: EmailMessage)` — implementations:
  `ResendEmailSender`, `SmtpEmailSender`, `MockEmailSender`
- `EmailTemplates` class for VerificationEmail
- Publishing has `NotificationEvent` durable events (not generic template
  system)
- No shared notification module — email is in identity context only

### Moderation/Content Suspension Patterns

- Publishing has `BLOCKED` status with `scanBlockedForRecovery()` and
  `PublishingFailureCategory.blocked` flag

## Changes

The full set of changes recorded by this exploration is defined in
[`./proposal.md`](./proposal.md), [`./spec.md`](./spec.md),
[`./design.md`](./design.md), [`./tasks.md`](./tasks.md), and
[`./verify.md`](./verify.md).

## Usage

This file is an investigation note used during planning. It is not consumed by
runtime code. Refer to [`./spec.md`](./spec.md) for the resulting requirements
and to [`./design.md`](./design.md) for the technical approach.

## Troubleshooting

- **Schema ambiguity**: `005-add-external-metadata.yaml` and
  `006-drop-external-metadata.yaml` are contradictory. Confirmed at planning
  time that `006-drop-external-metadata.yaml` was never applied; the dead
  changelog has since been deleted.
- **Attribution invisible in UI**: Attribution fields are returned by the API
  but never rendered. This blocks the Phase 1 attribution display work and
  motivates adding a `licence` column.
- **No moderation status**: `READY` is the only "public" status. Adding
  `SUSPENDED` is required for the takedown flow to actually hide reported
  assets from default listings.

## References

- Issue: `DALLAY-499`
- Specification: [`./spec.md`](./spec.md)
- Design: [`./design.md`](./design.md)
- Tasks: [`./tasks.md`](./tasks.md)
- Verification: [`./verify.md`](./verify.md)