@smoke @authorization @fast @postgres
Feature: Target-aware resource preview authorization
  The resource preview endpoint should evaluate the base permission first and
  apply target-aware scope reduction afterwards.

  Scenario: Member with base permission and matching scope can preview the requested resource
    Given a workspace member with the resource preview base permission
    And the member has a target scope that allows resource "30000000-0000-4000-8000-000000000001"
    When the client requests the preview for resource "30000000-0000-4000-8000-000000000001" with a valid JWT
    Then the response status should be 200
    And the resource preview response workspaceId should be "10000000-0000-4000-8000-000000000001"
    And the resource preview response principalId should be "20000000-0000-4000-8000-000000000001"
    And the resource preview response resourceId should be "30000000-0000-4000-8000-000000000001"
    And the resource preview response previewAllowed should be true
    And the latest authorization decision should be allow because "ROLE_PERMISSION"

  Scenario: Member with base permission but non-matching scope is denied
    Given a workspace member with the resource preview base permission
    And the member has a target scope that allows resource "8c858b24-25b3-578f-8555-71b565f61bab"
    When the client requests the preview for resource "30000000-0000-4000-8000-000000000001" with a valid JWT
    Then the response status should be 403
    And the latest authorization decision should be deny because "SCOPE_REDUCED_TARGET"
