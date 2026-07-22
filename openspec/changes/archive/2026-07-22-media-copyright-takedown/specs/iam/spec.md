# Delta for IAM

> **Archive note**: Reconciled to match shipped implementation. Permission keys use dashes per
> codebase convention (`media-read`, `media-takedown`). Scenarios updated to reference split
> approve/reject endpoints instead of single `.../action` endpoint.

## Overview

Adds two new governance media permission keys (`workspace:governance:media-read` and
`workspace:governance:media-takedown`) to gate takedown report access and approval actions.
Permission keys use dash-delimited segments per codebase convention (matching existing
`workspace:consent:read`, `workspace:audit:read`).

## Changes

### ADDED Requirements

#### Requirement: Governance Media Permissions

The permission registry MUST register two new `PermissionKey` entries:
`workspace:governance:media-read` and `workspace:governance:media-takedown`. Both SHALL follow the
`<domain>:<resource>:<action>` format established by the existing IAM platform, with dashes for
multi-word actions.

| Permission Key                        | Default Roles              | Purpose                  |
|---------------------------------------|----------------------------|--------------------------|
| `workspace:governance:media-read`     | `OWNER`, `ADMIN`, `MEMBER` | View takedown reports    |
| `workspace:governance:media-takedown` | `OWNER`, `ADMIN`           | Approve/reject takedowns |

(Previously: no `governance:media` permission keys existed.)

#### Scenario: Permission keys registered in PermissionRegistry

- GIVEN the IAM platform starts
- WHEN the permission registry initializes
- THEN `workspace:governance:media-read` SHALL be a valid permission key
- AND `workspace:governance:media-takedown` SHALL be a valid permission key

#### Scenario: Takedown action gated by permission

- GIVEN a user with `workspace:governance:media-read` but NOT `workspace:governance:media-takedown`
- WHEN calling `POST .../approve` or `POST .../reject` on a takedown report
- THEN the `GovernanceAuthorizationService` SHALL deny access
- AND the endpoint SHALL return `403 Forbidden`

#### Scenario: Owner/Admin can take takedown action

- GIVEN a user with role `OWNER` or `ADMIN` in the workspace
- WHEN calling `POST .../approve` or `POST .../reject` on a takedown report
- THEN the `GovernanceAuthorizationService` SHALL grant access
- AND the action SHALL proceed

## Usage

Permissions are enforced by `GovernanceAuthorizationService` (authorizeMediaTakedown) called from
`ReportTakedownHandler`, `ApproveTakedownHandler`, and `RejectTakedownHandler`.

## Troubleshooting

- **403 on legitimate request**: Verify the user has the `media-takedown` permission through their
  role assignments.
- **Missing permission key**: Ensure the `011-seed-governance-permissions.yaml` changelog has been
  applied.

## References

- Parent spec: [
  `../../../../specs/media-takedown/spec.md`](../../../../specs/media-takedown/spec.md)
- Parent change: [`../../proposal.md`](../../proposal.md)
- Design: [`../../design.md`](../../design.md)
