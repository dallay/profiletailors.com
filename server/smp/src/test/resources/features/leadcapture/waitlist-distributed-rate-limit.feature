Feature: Distributed waitlist rate limiting across replicas

  @rate-limit @waitlist @distributed
  Scenario: Shared WAITLIST bucket denies the 4th combined request across two replicas
    Given distributed WAITLIST rate limiting is configured with capacity 3 per minute
    When replica A consumes one WAITLIST token for client "IP:203.0.113.42" on waitlist "profile-tailors-launch"
    And replica B consumes one WAITLIST token for client "IP:203.0.113.42" on waitlist "profile-tailors-launch"
    And replica A consumes one WAITLIST token for client "IP:203.0.113.42" on waitlist "profile-tailors-launch"
    And replica B consumes one WAITLIST token for client "IP:203.0.113.42" on waitlist "profile-tailors-launch"
    Then the first 3 distributed WAITLIST consumptions should be allowed
    And the 4th distributed WAITLIST consumption should be denied
