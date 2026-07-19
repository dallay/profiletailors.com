# Privacy Data Aggregation Specification

## Purpose

Define how user data is collected across all bounded contexts for ACCESS/EXPORT requests, the structured JSON response format, delivery mechanisms, and export file conventions.

---

## Requirements

### Requirement: Data Collection Per Bounded Context

The `DataAggregationService` MUST collect data for the given principal across these 7 bounded contexts:

| Context | Data Retrieved | Key |
|---------|---------------|-----|
| Identity | `principals`, `user_identities` | `principal_id` |
| Credentials | `refresh_sessions`, `api_key_credentials` | `principal_id` |
| Tenancy | `workspace_memberships` | `principal_id` |
| Publishing | `social_connections`, `social_accounts`, `publications`, `publication_assets`, `secure_credentials` | `principal_id` or workspace membership |
| Media | `media_assets` | `author_principal_id` |
| Governance | `consent_records` | Subject email |
| Lead Capture | `waitlist_entries` | Normalized email |

Each context's data MUST be a separate section in the response.

#### Scenario: Access request collects all data

- GIVEN a submitted ACCESS request
- WHEN `DataAggregationService.collect(principalId)` is called
- THEN it MUST return data from ALL 7 bounded contexts
- AND each context's data MUST be a separate section in the response

### Requirement: Response Format and Delivery

The response MUST be structured JSON. Small responses (≤10 MB) MUST be returned inline. Larger responses MUST be delivered via presigned S3-compatible URL (TTL: 7 days). The JSON schema MUST include a `_metadata` section with `generated_at` timestamp, `principal_id`, and `request_id`.

#### Scenario: Large export uses presigned URL

- GIVEN an EXPORT request whose serialized payload exceeds 10 MB
- WHEN the handler completes
- THEN the response MUST contain a presigned download URL
- AND the URL MUST expire after 7 days

#### Scenario: Small export returns inline

- GIVEN an EXPORT request whose serialized payload is ≤10 MB
- WHEN the handler completes
- THEN the response MAY return the JSON inline
- AND `result_ref` MUST point to the download URL if available

### Requirement: Export File Format

EXPORT requests MUST produce a single `.json` file named `profile-tailors-export-{principalId}-{timestamp}.json`. The file MUST NOT be compressed for MVP. The download URL MUST use `StorageApplicationService.presignUrl()`.
