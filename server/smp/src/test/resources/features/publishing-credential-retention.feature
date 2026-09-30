@publishing @retention @smoke @fast
Feature: Credential retention on OAuth disconnect
  Disconnecting a provider deletes its OAuth credential and records a deletion
  tombstone so repeated disconnects are idempotent and restores can re-apply
  the erasure instead of silently bringing the credential back.

  Scenario: Threads disconnect deletes the credential and replays idempotently
    Given a connected Threads social account exists
    When the client disconnects the Threads connection
    Then the publishing response status should be 200
    And the connection response should be deleted for Threads
    When the client disconnects the Threads connection
    Then the publishing response status should be 200
    And the connection response should be deleted for Threads
    When the client lists connected channels
    Then the publishing response status should be 200
    And the channels list should omit Threads
