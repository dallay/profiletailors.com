@hashtags @smoke @fast
Feature: AI Hashtag Generator

  Background:
    Given an authorized workspace member exists
    And a connected LinkedIn social account exists

  @must-have
  Scenario: System suggests hashtags from content analysis
    When the client analyzes content "We're building cutting-edge AI software development tools for startup founders"
    Then the hashtags response status should be 200
    And the hashtags response should contain at least 3 suggestions
    And the suggestions should include detected topics

  @must-have
  Scenario: Short content still returns suggestions
    When the client analyzes content "Leadership in tech"
    Then the hashtags response status should be 200
    And the hashtags response should contain at least 1 suggestion

  @must-have
  Scenario: Trending hashtags endpoint returns results
    When the client fetches trending hashtags
    Then the hashtags response status should be 200
    And the trending hashtags response should contain at least 1 hashtag

  @must-have
  Scenario: User saves a hashtag set for reuse
    When the client saves a hashtag set named "Tech Industry" with hashtags "#Tech,#Innovation,#Startups"
    Then the hashtags response status should be 201
    And the saved set response should contain a setId
    And the saved set name should be "Tech Industry"

  @must-have
  Scenario: User lists saved hashtag sets
    Given a saved hashtag set "My Set" with hashtags "#Leadership,#Career" exists
    When the client lists saved hashtag sets
    Then the hashtags response status should be 200
    And the saved sets response should contain at least 1 set

  @must-have
  Scenario: User deletes a saved hashtag set
    Given a saved hashtag set "Temporary Set" with hashtags "#Delete,#Me" exists
    When the client deletes the saved hashtag set
    Then the hashtags response status should be 204

  @must-have
  Scenario: Saving set with blank name is rejected
    When the client saves a hashtag set named "" with hashtags "#Tech"
    Then the hashtags response status should be 400

  @must-have
  Scenario: Saving set with no hashtags is rejected
    When the client saves a hashtag set named "Empty" with hashtags ""
    Then the hashtags response status should be 400

  @must-have
  Scenario: Hashtags are normalized with # prefix on save
    When the client saves a hashtag set named "Normalized" with hashtags "Tech,Innovation"
    Then the hashtags response status should be 201
    And the saved hashtags should all start with "#"
