# Privacy Data Aggregation Specification

## Purpose

Define how user data is collected across all bounded contexts for ACCESS/EXPORT requests, the
structured JSON response format, delivery mechanisms, and export file conventions.

---

## Requirements

### Requirement: Data Collection Per Bounded Context

The `DataAggregationService` MUST collect data for the given principal across these 7 bounded
contexts:

| Context      | Data Retrieved                                                                                      | Key                                    |
|--------------|-----------------------------------------------------------------------------------------------------|----------------------------------------|
| Identity     | `principals`, `user_identities`                                                                     | `principal_id`                         |
| Credentials  | `refresh_sessions`, `api_key_credentials`                                                           | `principal_id`                         |
| Tenancy      | `workspace_memberships`                                                                             | `principal_id`                         |
| Publishing   | `social_connections`, `social_accounts`, `publications`, `publication_assets`, `secure_credentials` | `principal_id` or workspace membership |
| Media        | `media_assets`                                                                                      | `author_principal_id`                  |
| Governance   | `consent_records`                                                                                   | Subject email                          |
| Lead Capture | `waitlist_entries`                                                                                  | Normalized email                       |

Each context's data MUST be a separate section in the response.

#### Scenario: Access request collects all data

- GIVEN a submitted ACCESS request
- WHEN `DataAggregationService.collect(principalId)` is called
- THEN it MUST return data from ALL 7 bounded contexts
- AND each context's data MUST be a separate section in the response

### Requirement: Response Format and Delivery

The response MUST be structured JSON. The JSON schema MUST include a `_metadata` section with
`generatedAt` timestamp and `principalId`.

#### Scenario: Aggregated payload returns inline

- GIVEN an ACCESS request is processed
- WHEN `DataAggregationService.aggregate(principalId, email)` is invoked
- THEN the response MUST contain the JSON object with `_metadata`, `identity`, `credentials`,
  `workspaces`, `publishing`, `media`, `governance`, and `leadCapture` sections

#### Scenario: Publishing section exposes connected content

- GIVEN publishing data exists for the principal
- WHEN the section is materialised
- THEN it MUST expose `socialConnections`, `socialAccounts`, and `publications` arrays for the
  current principal

### Requirement: Export File Format

EXPORT requests MUST produce a single `.json` file named
`profile-tailors-export-{principalId}-{timestamp}.json`. The file MUST NOT be compressed for MVP.
The download URL MUST use `StorageApplicationService.presignUrl()`.
