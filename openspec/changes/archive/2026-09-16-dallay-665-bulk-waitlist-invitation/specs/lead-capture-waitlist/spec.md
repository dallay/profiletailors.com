# Delta for lead-capture-waitlist

## ADDED Requirements

### Requirement: Bulk eligibility is PENDING-only

Bulk MUST apply single-entry eligibility per entry; `invite()` is unchanged (PENDING → INVITED only). INVITED entries MUST report `skipped`; CONVERTED, CANCELLED, missing, or active-invitation entries MUST report `failed` with a stable code. Retries MUST yield `skipped`, never duplicates.

#### Scenario: Per-entry eligibility mapping

- GIVEN 1 PENDING + 1 INVITED + 1 CONVERTED entry in one batch
- WHEN an authorized admin bulk-invites all 3
- THEN outcomes are 1 `invited`, 1 `skipped`, 1 `failed` with the single-entry codes

#### Scenario: Retry yields skips

- GIVEN a batch already bulk-invited
- WHEN the same batch is bulk-invited again
- THEN previously invited entries report `skipped` and no duplicate invitation is created
