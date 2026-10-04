# Delta for privacy-compliance

## MODIFIED Requirements

### Requirement: Authenticated publication browser storage is not a canonical fallback

The authenticated scheduler MUST NOT read unscoped `pt_publications` browser data as a fallback for a failed calendar request, and MUST NOT write authenticated workspace publication data to that shared key. A failed authenticated calendar request MUST retain the last canonical in-memory result or expose the existing unavailable/error state; it MUST NOT substitute data whose workspace or authorization context cannot be proved.

Any remaining anonymous/local fallback behavior MUST be explicitly separated from authenticated workspace data, must contain only the minimum existing fields required by the anonymous product path, and MUST be validated before use. Browser fixtures and tests MUST NOT require authenticated publication persistence in `pt_publications`.

(Previously: the publishing store could load and save `pt_publications` regardless of authentication state, and an authenticated calendar network failure could fall back to that unscoped list.)

#### Scenario: Authenticated calendar failure does not show shared local data

- GIVEN an authenticated user is active in workspace `workspace-a`
- AND `pt_publications` contains a publication from an unknown or different workspace
- AND the canonical calendar request fails
- WHEN the store handles the failure
- THEN it MUST NOT replace the current authenticated calendar state with the `pt_publications` value
- AND it MUST retain canonical in-memory data or expose an unavailable state

#### Scenario: Authenticated mutation does not persist publication content to the shared key

- GIVEN an authenticated user creates or edits a publication in `workspace-a`
- WHEN the mutation succeeds
- THEN the publication content and channel metadata MUST NOT be written to the unscoped `pt_publications` key
- AND the canonical server response MUST remain the source for authenticated state

#### Scenario: Authenticated store startup ignores unscoped publication storage

- GIVEN a browser already contains `pt_publications`
- WHEN an authenticated publishing store starts
- THEN it MUST ignore that key for authenticated publication state
- AND it MUST load publication state from the workspace-scoped canonical API path

#### Scenario: Anonymous fallback remains bounded

- GIVEN the existing anonymous product path requires local publication data
- WHEN that path reads browser storage
- THEN it MUST validate the stored shape and use only its explicitly bounded scope
- AND it MUST not be reused by an authenticated workspace path
