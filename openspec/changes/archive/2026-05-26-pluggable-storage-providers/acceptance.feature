# Acceptance tests for pluggable-storage-providers

Feature: Storage providers

  Scenario: Upload and download local (Given/When/Then style)
    Given a local provider configured for bucket 'local-test'
    When I upload a file 'foo/bar.txt' with content 'hello world'
    Then I can download it and content matches

