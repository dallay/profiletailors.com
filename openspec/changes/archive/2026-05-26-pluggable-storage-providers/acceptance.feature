# Acceptance tests for pluggable-storage-providers

Feature: Storage providers

  Scenario: Upload and download local (Given/When/Then style)
    Given a local provider configured for bucket 'local-test'
    When I upload a file 'foo/bar.txt' with content 'hello world'
    Then I can download it and content matches

  Scenario: Presigned URL generation and use
    Given a local provider configured for bucket 'local-test'
    When I create a presigned upload URL for 'uploads/file.txt'
    And I create a presigned download URL for 'uploads/file.txt'
    Then I can PUT data via the presigned upload URL
    And I can GET data via the presigned download URL
    And the content matches what was uploaded

  Scenario: Path traversal rejection
    Given a local provider configured for bucket 'local-test'
    When I attempt to upload a file with path '../secret.txt'
    Then the provider returns a 4xx error or denies access
    When I attempt to download a file with path '../../etc/passwd'
    Then the provider returns a 4xx error or denies access

  Scenario: Multi-provider resolution
    Given a local provider configured for bucket 'local-test'
    And an S3 provider configured for bucket 's3-test'
    When I request storage for 'local-test'
    Then the local provider is returned
    When I request storage for 's3-test'
    Then the S3 provider is returned
    When I request storage for 'nonexistent-bucket'
    Then the registry throws an exception

  Scenario: Large-object streaming
    Given a local provider configured for bucket 'local-test'
    When I upload a file 'large/data.bin' with content larger than 100MB
    Then I can stream-download it in chunks
    And the downloaded content matches the uploaded content
    And memory usage remains bounded during streaming
