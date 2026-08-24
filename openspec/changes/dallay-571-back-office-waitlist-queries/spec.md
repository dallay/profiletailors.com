# Delta for Platform Admin Waitlist Queries

## Overview

This delta defines operational read access over waitlist entries for Back Office administrators.
It covers list with pagination, search by email, filter by status, sort by creation date, and
detail access exposing invitation/conversion state. The waitlist aggregate and invitation concept
remain independent; queries are read-only projections.

## Changes

### ADDED Requirements

#### Requirement: Admin Waitlist List with Pagination

The admin waitlist list endpoint MUST return a paginated result of waitlist entry summaries.
Pagination MUST be bounded by a maximum page size. Sorting MUST default to creation date
descending and MUST only allow a fixed set of sort fields.

##### Scenario: List returns paginated entries

- GIVEN waitlist entries exist
- WHEN an administrator opens the waitlist
- THEN entries MUST be returned using pagination

##### Scenario: Page size exceeding the maximum is rejected

- GIVEN an administrator requests a page size above the maximum
- WHEN the list endpoint is called
- THEN the response MUST be 400 Bad Request

#### Requirement: Admin Waitlist Search by Email

The admin waitlist list endpoint MUST support searching by email. Email search MUST be
case-insensitive and match against the normalized email column.

##### Scenario: Search by email returns the matching entry

- GIVEN "user@example.com" exists on the waitlist
- WHEN an administrator searches for "user@example.com"
- THEN the matching entry MUST be returned

#### Requirement: Admin Waitlist Filter by Status

The admin waitlist list endpoint MUST support filtering by waitlist entry status. Status values
MUST be passed through as-is; the query MUST not collapse distinct statuses.

##### Scenario: Filter by status returns only matching entries

- GIVEN entries with statuses PENDING and INVITED exist
- WHEN an administrator filters by status "PENDING"
- THEN only PENDING entries MUST be returned

#### Requirement: Admin Waitlist Detail Exposes Invitation State

The admin waitlist detail endpoint MUST expose entry state (status, lifecycle timestamps,
consent, source) and a separate invitation history list. Invitation state MUST NOT be collapsed
into waitlist entry state.

##### Scenario: Detail returns entry with invitation history

- GIVEN a waitlist entry with at least one invitation
- WHEN an administrator requests the entry detail
- THEN the response MUST include the entry status and a separate invitation history list

#### Requirement: Admin Waitlist Read Authorization

Waitlist read access MUST require an active platform role assignment containing the
`WAITLIST_READ` permission. Unauthenticated requests MUST return 401; principals without the
permission MUST receive 403.

##### Scenario: Unauthenticated request is rejected

- GIVEN no authentication is provided
- WHEN the admin waitlist endpoint is called
- THEN the response MUST be 401

##### Scenario: Principal without permission is denied

- GIVEN an authenticated principal without `WAITLIST_READ`
- WHEN the admin waitlist endpoint is called
- THEN the response MUST be 403

#### Requirement: Waitlist Query Observability

Waitlist list queries MUST record an aggregate-level metric tagged by whether a status filter
was applied and whether an email search was used. Read queries MUST NOT be individually audited
as mutations.

##### Scenario: Query with status filter is measurable

- GIVEN an administrator lists waitlist entries filtered by status
- WHEN the query completes
- THEN a query counter MUST be incremented with the status-filter tag set

## Usage

### Query Semantics

Pagination uses zero-based `page` and bounded `size`. Filtering supports `status`, `email`,
`waitlistId`, `waitlistKey`, and date ranges on `joinedAt` and `invitedAt`. Sorting defaults to
`joinedAt` descending and allows `joinedAt`, `invitedAt`, `email`, and `status`.

## Troubleshooting

### Blockers

A missing BDD scenario for list, search, or filter; missing authorization enforcement; exposed
PII beyond operationally necessary fields; or missing observability blocks acceptance.

## References

- DALLAY-571.
- Existing `lead-capture-waitlist` spec and `platformadmin` bounded context.
