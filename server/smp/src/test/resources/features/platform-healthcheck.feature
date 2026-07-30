@smoke @platform @fast
Feature: Platform healthcheck
  The healthcheck endpoint provides a lightweight liveness probe
  for load balancers and orchestration infrastructure.

  Scenario: Healthcheck returns OK
    When the healthcheck endpoint is called
    Then the healthcheck response status should be 200
    And the healthcheck response body should be "OK"
