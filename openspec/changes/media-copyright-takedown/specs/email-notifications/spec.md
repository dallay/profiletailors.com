# Delta for Email Notifications

## ADDED Requirements

### Requirement: Takedown Notification Templates

The `EmailTemplates` class MUST produce three new template types for the takedown lifecycle:

| Template | Trigger | Content |
|----------|---------|---------|
| **Takedown Confirmation** | Report submitted | Report ID, asset ID, expected review timeline, support contact |
| **Takedown Resolution** | Staff approve/reject action | Outcome (approved or rejected), asset ID, explanation |
| **Counter-Notice Update** | Counter-notice resolved | Outcome (restored or escalated), next steps |

All templates SHALL follow the existing `EmailTemplates` pattern: plain-text body and inline-styled HTML body, both rendered from template variables. Templates SHALL be sent via the existing `EmailSender` interface using async dispatch.

(Previously: only verification email templates existed in `EmailTemplates`.)

#### Scenario: Confirmation email on report submission
- GIVEN a takedown report is submitted successfully via `POST /api/governance/media/takedown-reports`
- WHEN the system processes the submission
- THEN the system SHALL send a "Takedown Confirmation" email to the submitter
- AND the email SHALL include the report ID
- AND the email SHALL be dispatched asynchronously

#### Scenario: Resolution email on staff action
- GIVEN a staff member approves a takedown report via `POST .../action` with `action = "APPROVE"`
- WHEN the action is processed
- THEN the system SHALL send a "Takedown Resolution" email to the submitter
- AND the email SHALL state the outcome as approved
- AND the email SHALL include the asset ID

#### Scenario: Counter-notice update email
- GIVEN a counter-notice is accepted, restoring a `SUSPENDED` asset to `READY`
- WHEN the restoration is processed
- THEN the system SHALL send a "Counter-Notice Update" email to the submitter
- AND the email SHALL state the outcome as restored
