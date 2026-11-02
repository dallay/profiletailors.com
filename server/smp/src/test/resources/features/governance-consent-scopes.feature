@consent-scopes @governance @smoke @fast @postgres
Feature: Public waitlist consent scopes
  Public list consent belongs to the real waitlist rather than a tenant workspace.

  Scenario: One person joins two public lists without sharing consent state
    Given two public consent lists and an authorized tenant workspace exist
    When one person joins both public consent lists and repeats the first join
    Then each public list has one active consent and one scoped event

  Scenario: Tenant consent endpoints cannot query or withdraw public list consent
    Given two public consent lists and an authorized tenant workspace exist
    When one person joins both public consent lists and repeats the first join
    And the tenant queries and attempts to withdraw the public consent identity
    Then each public list has one active consent and one scoped event

  Scenario: Tenant consent endpoints cannot query or withdraw platform Terms consent
    Given two public consent lists and an authorized tenant workspace exist
    And platform Terms consent exists for the workspace user
    When the tenant queries and attempts to withdraw platform Terms consent
    Then the platform Terms consent remains active
