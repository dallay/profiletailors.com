Feature: LinkedIn OAuth Integration

  Background:
    Given the user is authenticated with verified email

  @integration @linkedin @oauth
  Scenario: LinkedIn callback requires authentication
    When the user navigates to "/integrations/linkedin/callback"
    Then the user should be redirected to "/login"

  @integration @linkedin @oauth
  Scenario: Successful OAuth callback adds channel to list
    Given the user is on the LinkedIn OAuth callback page
    And the OAuth code is valid
    When the OAuth exchange completes
    Then the channel should be added to the user's channel list

  @integration @linkedin @oauth
  Scenario: OAuth callback without code handles error gracefully
    Given the user is on the LinkedIn OAuth callback page
    And there is no OAuth code in the URL
    Then an error message should be displayed

  @integration @linkedin @sse
  Scenario: SSE endpoint streams channel events
    Given the user has a connected LinkedIn channel
    When the user connects to the SSE endpoint "/api/channels/events"
    Then an event stream should be established

  @integration @linkedin @sse
  Scenario: Publication completion event is received via SSE
    Given the user is connected to the SSE endpoint
    And a scheduled post is published
    When LinkedIn returns a completion event
    Then the post status should update to "PUBLISHED"
    And the SSE should receive the completion event

  @integration @linkedin @sse
  Scenario: Publication failure event is received via SSE
    Given the user is connected to the SSE endpoint
    And a scheduled post fails to publish
    When LinkedIn returns a failure event
    Then the post status should update to "FAILED"
    And the SSE should receive the failure event