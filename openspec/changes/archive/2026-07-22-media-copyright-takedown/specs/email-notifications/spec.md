# Delta for Email Notifications

> **Archive note**: Reconciled to match shipped implementation. Counter-notice update template
> and scenario removed from scope. Implementation uses `TakedownEmailTemplates` and
> domain-event consumers (`TakedownEmailConsumers`) instead of direct `EmailSender.send()`.

## Overview

Adds takedown lifecycle email notifications (confirmation on report, resolution on approve/reject)
using domain-event consumers and the existing `EmailDispatcher` / `EmailTemplates` pattern.
Counter-notice update emails removed from implementation scope.

## Changes

### ADDED Requirements

#### Requirement: Takedown Notification Templates

The system MUST produce three template types for the takedown lifecycle:

| Template                    | Trigger              | Content                                                       |
|-----------------------------|----------------------|---------------------------------------------------------------|
| **Takedown Confirmation**   | Report submitted     | Report ID, asset ID, expected review timeline, support contact|
| **Takedown Approved**       | Staff approves       | Outcome (approved), asset ID, report ID                       |
| **Takedown Rejected**       | Staff rejects        | Outcome (rejected), asset ID, report ID                       |

All templates SHALL follow the existing `EmailTemplates` pattern: plain-text body and inline-styled
HTML body, both rendered from template variables with idempotency keys. Templates SHALL be
dispatched via domain-event consumers (`TakedownEmailConsumers`) through the `EmailDispatcher`.

Implementation lives in `TakedownEmailTemplates.kt` under `governance/infrastructure/email/`.

(Previously: only verification email templates existed.)

#### Scenario: Confirmation email on report submission

- GIVEN a takedown report is submitted successfully via
  `POST /api/governance/media/takedown-reports`
- WHEN the system processes the submission
- THEN the system SHALL send a "Takedown Confirmation" email to workspace admins
- AND the email SHALL include the report ID
- AND the email SHALL be dispatched asynchronously via `SendTakedownReportedEmailConsumer`

#### Scenario: Resolution email on staff approve

- GIVEN a staff member approves a takedown report via `POST .../approve`
- WHEN the action is processed
- THEN the system SHALL send a "Takedown Approved" email to workspace admins
- AND the email SHALL state the outcome as approved
- AND the email SHALL include the asset ID

#### Scenario: Resolution email on staff reject

- GIVEN a staff member rejects a takedown report via `POST .../reject`
- WHEN the action is processed
- THEN the system SHALL send a "Takedown Rejected" email to workspace admins
- AND the email SHALL state the outcome as rejected
- AND the email SHALL include the asset ID

## Usage

Templates live in
`server/smp/src/main/kotlin/com/profiletailors/smp/governance/infrastructure/email/TakedownEmailTemplates.kt`.
Consumers in `TakedownEmailConsumers.kt` resolve recipients and dispatch via `EmailDispatcher`.

## Troubleshooting

- **Email not delivered**: Check the `NotificationRepository.findByIdempotencyKey` to confirm
  idempotency is not silently skipping dispatch.
- **Workspace owners not resolved**: `WorkspaceOwnershipRepository.findByWorkspaceId` returned an
  empty set; verify the workspace has at least one owner.

## References

- Parent spec: [
  `../../../../specs/media-takedown/spec.md`](../../../../specs/media-takedown/spec.md)
- Parent change: [`../../proposal.md`](../../proposal.md)
- Design: [`../../design.md`](../../design.md)
