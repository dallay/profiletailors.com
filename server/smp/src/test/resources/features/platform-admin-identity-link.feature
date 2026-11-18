@platform-admin @smoke @fast
Feature: Platform-admin identity-link provisioning

  Scenario: Unauthenticated identity-link request is denied
    When an unauthenticated client submits an identity-link request
    Then the identity-link response status should be 401

  Scenario: Operator provisions an external identity link through a fresh MFA session
    Given an operator with USERS_MANAGE and a target principal with no external identity link
    When the operator completes a local OIDC authorization-code login with fresh OTP MFA
    And the authenticated operator provisions the target identity link
    Then the identity-link response status should be 201
    And the target external identity link should be persisted

  Scenario: Duplicate external identity link provisioning is rejected
    Given an operator with USERS_MANAGE and a target principal with no external identity link
    When the operator completes a local OIDC authorization-code login with fresh OTP MFA
    And the authenticated operator provisions the target identity link
    And the authenticated operator repeats the identity-link request
    Then the identity-link response status should be 409
    And the original external identity link should remain unchanged
    And the identity-link audit evidence should be persisted

  Scenario: Invalid issuer is rejected without creating a link
    Given an operator with USERS_MANAGE and a target principal with no external identity link
    When the operator completes a local OIDC authorization-code login with fresh OTP MFA
    And the authenticated operator submits an identity-link request with an invalid issuer
    Then the identity-link response status should be 400
    And the target external identity link should not be persisted
    And the identity-link invalid issuer audit evidence should be persisted

  Scenario: Missing target principal is rejected without creating a link
    Given an operator with USERS_MANAGE and a target principal with no external identity link
    When the operator completes a local OIDC authorization-code login with fresh OTP MFA
    And the target principal is removed before identity-link provisioning
    And the authenticated operator provisions the target identity link
    Then the identity-link response status should be 404
    And the target external identity link should not be persisted
    And the identity-link missing target audit evidence should be persisted

  Scenario: Invalid subject is rejected without creating a link
    Given an operator with USERS_MANAGE and a target principal with no external identity link
    When the operator completes a local OIDC authorization-code login with fresh OTP MFA
    And the authenticated operator submits an identity-link request with an invalid subject
    Then the identity-link response status should be 400
    And the target external identity link should not be persisted
    And the identity-link invalid subject audit evidence should be persisted

  Scenario: Operator without local permission is denied and audited
    Given an operator with USERS_MANAGE and a target principal with no external identity link
    When the operator completes a local OIDC authorization-code login with fresh OTP MFA
    And the operator loses USERS_MANAGE before identity-link provisioning
    And the authenticated operator provisions the target identity link
    Then the identity-link response status should be 403
    And the target external identity link should not be persisted
    And the identity-link permission denial audit evidence should be persisted

  Scenario: Identity-link request without an idempotency key is rejected
    Given an operator with USERS_MANAGE and a target principal with no external identity link
    When the operator completes a local OIDC authorization-code login with fresh OTP MFA
    And the authenticated operator submits an identity-link request without an idempotency key
    Then the identity-link response status should be 400
    And the identity-link problem detail code should be "VALIDATION_ERROR"
    And the target external identity link should not be persisted

  Scenario: Persistence failure is audited without creating an identity link
    Given an operator with USERS_MANAGE and a target principal with no external identity link
    When the operator completes a local OIDC authorization-code login with fresh OTP MFA
    And identity-link persistence fails during provisioning
    Then the identity-link response status should be 500
    And the target external identity link should not be persisted
    And the identity-link persistence failure audit evidence should be persisted
