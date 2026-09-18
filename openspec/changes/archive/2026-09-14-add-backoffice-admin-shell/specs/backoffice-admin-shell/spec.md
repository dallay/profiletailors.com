# Backoffice Admin Shell Specification

## Purpose

Admin SPA shell (`apps/web/admin`, root `/` under `AdminLayout`): layout, permission-filtered nav registry, routing seams, inert placeholders. No dashboard-app change.

## Requirements

### Requirement: Shell Loads for Authorized Admin

The system MUST render `AdminLayout` with sidebar nav for any authenticated principal holding platform access.

#### Scenario: Authorized admin opens shell

- GIVEN an authenticated admin with platform access
- WHEN opening the admin shell root
- THEN layout and nav render without redirect

#### Scenario: Unauthorized principal is denied

- GIVEN a principal without platform access
- WHEN opening the shell root
- THEN the system redirects to login or access-denied

### Requirement: Registry-Driven Navigation

The system MUST render nav exclusively from a central registry `{ key, route, permission, status: live|planned }`, filtered by `hasPermission`.

#### Scenario: Nav filtered by permission

- GIVEN a principal lacking a registry entry's permission
- WHEN the shell renders nav
- THEN that entry is hidden

#### Scenario: Extensibility is one entry

- GIVEN a new future area with route and permission
- WHEN one registry entry is added
- THEN nav, route, and gating work with no layout change

### Requirement: Direct-Invitations Nav Entry

The system MUST list `direct-invitations` in nav when the principal holds `platform.invitations.read`.

#### Scenario: Permitted principal sees direct-invitations

- GIVEN a principal with `platform.invitations.read`
- WHEN the shell renders nav
- THEN `direct-invitations` is visible and routes to the existing view

### Requirement: Inert Planned-Area Placeholders

The system MUST render planned areas (overview/users/waitlist/invitations/notifications/governance/configuration/audit) via a shared static view that is explicit "planned", permission-gated, and performs zero fetch.

#### Scenario: Planned area shows planned state

- GIVEN a permitted principal opening a planned area
- WHEN the route renders
- THEN an explicit planned message shows and no API request is issued

#### Scenario: Unpermitted planned area stays hidden

- GIVEN a principal lacking the area's permission
- WHEN the shell renders nav and routes
- THEN the entry is hidden and direct navigation is denied

### Requirement: Localized Labels

The system MUST provide EN/ES nav and placeholder labels for every registry entry.

#### Scenario: Spanish labels render

- GIVEN locale `es`
- WHEN the shell renders nav
- THEN all entries show Spanish labels

## Acceptance Mapping

| # | Criterion | Scenario |
|---|-----------|----------|
| 1 | Shell loads + nav visible for authorized admin | Shell Loads / Authorized admin opens shell |
| 2 | `direct-invitations` in nav | Direct-Invitations Nav Entry |
| 3 | Planned areas show planned state, zero fetch | Inert Planned-Area Placeholders |
| 4 | Gating matches server; `admin-check` + `admin-build` pass | Registry-Driven Navigation + authz delta |
