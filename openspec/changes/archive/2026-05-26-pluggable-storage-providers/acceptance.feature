# Acceptance tests for pluggable-storage-providers

Feature: Storage providers

  Scenario: Upload and download local (Given/When/Then style)
    Given a local provider configured for bucket 'local-test'
    When I upload a file 'foo/bar.txt' with content 'hello world'
    Then I can download it and content matches

  Scenario: Presigned URL generation and use
    Given an S3 provider configured for bucket 's3-test'
    When I upload a file 'test/document.pdf' with content 'sample data'
    And I generate a presigned download URL for 'test/document.pdf' with 3600 seconds expiry
    Then the presigned URL is valid and accessible
    And I can download the file using the presigned URL
    And the downloaded content matches 'sample data'

  Scenario: Path traversal rejection
    Given a local provider configured for bucket 'secure-bucket'
    When I attempt to upload a file '../../../etc/passwd' with content 'malicious'
    Then the operation is rejected or access is denied
    When I attempt to download a file '../../secret.txt'
    Then the operation is rejected or access is denied

  Scenario: Multi-provider resolution
    Given multiple providers are configured:
      | name       | type  | bucket       |
      | local-test | local | local-bucket |
      | s3-test    | s3    | my-s3-bucket |
    When I request storage for bucket 'local-test'
    Then I receive a LocalFilesystemStorage instance
    When I request storage for bucket 's3-test'
    Then I receive an S3Storage instance
    When I request storage for bucket 'nonexistent'
    Then a BucketNotFoundException is thrown

  Scenario: Large-object streaming
    Given a local provider configured for bucket 'large-files'
    When I upload a large file 'data/large.bin' with 150MB of random data
    Then the upload completes successfully
    When I download 'data/large.bin' as a stream
    Then the download streams correctly
    And the downloaded content integrity matches the original
    # Note: The following step requires manual verification or specialized tooling
    And memory usage remains bounded during streaming
