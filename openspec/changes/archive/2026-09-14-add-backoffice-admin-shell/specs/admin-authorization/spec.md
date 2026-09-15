# Delta for Admin Authorization

## ADDED Requirements

### Requirement: Frontend Mirror Matches Server

The frontend `ROLE_PERMISSIONS` mirror MUST equal the server `PLATFORM_ROLE_PERMISSIONS` for every key, including `platform.publishing.stale.read` for OWNER and OPERATOR. The system MUST NOT imply permissions the API does not enforce.

#### Scenario: Mirror includes publishing stale read

- GIVEN OWNER or OPERATOR session permissions
- WHEN the frontend evaluates `hasPermission('platform.publishing.stale.read')`
- THEN it returns true, matching the server map

#### Scenario: No implied permissions

- GIVEN a planned area with no backing admin API
- WHEN its placeholder renders
- THEN no permission beyond the registry entry is implied or checked

### Requirement: Frontend Gating Is Additive Only

The frontend MUST treat gating as display convenience only; the server (`OperatorAccessResolver`, default-deny) SHALL remain authoritative per #659.

#### Scenario: Frontend bypass attempt

- GIVEN a principal lacking a permission who forces client-side nav
- WHEN calling the corresponding `/api/admin/**` endpoint
- THEN the server denies with 401/403 or `PlatformAccessDeniedException`

## MODIFIED Requirements

### Requirement: Permission Registry Completeness

The registry MUST list all 14 `PlatformPermission` keys with accurate meanings (header corrected from "15" to the actual key count; `platform.invitations.create` row restored — `INVITATIONS_CREATE` exists in `PlatformPermission.kt` and is enforced by `CreateInvitationHandler.kt`).

(Previously: header claimed "All 15 keys" while the table listed 13 rows.)

#### Scenario: Registry audit

- GIVEN the permission registry documentation
- WHEN counting documented keys against `PlatformPermission`
- THEN every enum key appears exactly once with a correct description
