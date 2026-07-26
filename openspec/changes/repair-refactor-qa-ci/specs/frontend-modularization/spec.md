# Delta for Frontend Modularization

## ADDED Requirements

### Requirement: Frontend Gate Recovery Without Product Change

The Vue SPA MUST complete its existing production build, type-check, and Biome lint gates without
changing routes, API contracts, state semantics, or visual behavior.

#### Scenario: Frontend quality gates pass

- GIVEN the repair is applied
- WHEN the app build, type-check, and Biome lint gates run
- THEN each MUST complete without errors
- AND no compiler suppression, dependency change, or broad formatting workaround MAY be introduced

### Requirement: Reactive Composable Contracts

Extracted composables and their public barrels MUST expose the reactive values expected by consuming
views; consumers MUST use those values according to their declared reactive contract.

#### Scenario: View consumes a reactive value

- GIVEN a view consumes an extracted composable value
- WHEN the underlying value changes
- THEN the view MUST receive the updated value through the declared ref or computed contract
- AND it MUST NOT treat a reactive wrapper as its unwrapped value, or vice versa

### Requirement: Safe Localized Action Errors

User-visible action failures MUST map known error outcomes to existing localized messages and MUST
use a safe localized fallback for unknown errors. Raw server or exception text MUST NOT be shown.

#### Scenario: Unknown action failure

- GIVEN an action fails with an unmapped error
- WHEN the view presents feedback
- THEN it MUST show the localized fallback error
- AND it MUST preserve the current product state rather than reporting false success
