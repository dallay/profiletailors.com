# Spec: R2 Dedicated Storage Adapter

## Overview

This specification defines the behavior of the dedicated `R2StorageAdapter` for Cloudflare R2 object
storage, replacing the legacy `S2Storage` thin wrapper.

## Requirements

### Requirement: R2 Adapter Lifecycle

The system MUST provide a first-class `R2StorageAdapter` that implements `PresignableStorage`
directly, independent of `S3Storage` delegation.

The adapter MUST support the following operations:

- `upload(key, contentLength, content, metadata)` - Stream upload with optional metadata
- `download(key)` - Stream download returning `ByteReadChannel`
- `delete(key)` - Single object deletion
- `list(prefix, delimiter)` - List objects with optional prefix and delimiter
- `presignGet(key, expiresIn)` - Generate presigned GET URL
- `exists(key)` - Check if object exists

The adapter MUST accept `StorageProperties.R2Provider` configuration containing:

- `bucket` (required) - R2 bucket name
- `endpoint` (required) - R2 endpoint URL
- `accessKeyId` (required) - R2 access key
- `secretAccessKey` (required) - R2 secret key
- `region` (optional, default "auto") - R2 uses `auto` for global storage

#### Scenario: R2 adapter uploads object successfully

- GIVEN an `R2StorageAdapter` configured with valid credentials and bucket "user-images"
- WHEN `upload("avatars/user1.png", 1024, contentFlow, metadata)` is called
- THEN the object is uploaded to R2 bucket "user-images" with key "avatars/user1.png"
- AND metadata is stored with the object
- AND the operation completes without error

#### Scenario: R2 adapter downloads object successfully

- GIVEN an R2 bucket "user-images" contains object "avatars/user1.png"
- WHEN `download("avatars/user1.png")` is called
- THEN a `ByteReadChannel` is returned with the object content
- AND the content matches what was originally uploaded

#### Scenario: R2 adapter deletes object

- GIVEN an R2 bucket "user-images" contains object "temp/file.txt"
- WHEN `delete("temp/file.txt")` is called
- THEN the object is removed from the bucket
- AND subsequent `exists("temp/file.txt")` returns `false`

#### Scenario: R2 adapter lists objects with prefix

- GIVEN an R2 bucket "user-images" contains objects "avatars/user1.png" and "avatars/user2.png"
- WHEN `list("avatars/")` is called
- THEN the result contains both "avatars/user1.png" and "avatars/user2.png"

#### Scenario: R2 adapter generates presigned URL

- GIVEN an R2 bucket "attachments" contains object "invoices/2024/001.pdf"
- WHEN `presignGet("invoices/2024/001.pdf", 3600)` is called
- THEN a valid presigned URL is returned
- AND the URL allows downloading the object within 3600 seconds
- AND the URL targets the R2 endpoint, not AWS S3

### Requirement: R2-Specific Configuration

The adapter MUST support both `type: r2` (canonical) and `type: s2` (backward-compatible alias) in
YAML configuration.

The R2 configuration MUST automatically set `region = "auto"` if not explicitly provided, matching
R2's global storage model.

#### Scenario: R2 configured with type r2

- GIVEN YAML configuration with `type: r2`, bucket "media", and valid R2 credentials
- WHEN `BucketRegistry.getStorage("media")` is called
- THEN an `R2StorageAdapter` instance is returned
- AND the adapter uses the R2-specific endpoint

#### Scenario: R2 configured with type s2 (legacy alias)

- GIVEN YAML configuration with `type: s2`, bucket "media", and valid R2 credentials
- WHEN `BucketRegistry.getStorage("media")` is called
- THEN an `R2StorageAdapter` instance is returned
- AND the behavior is identical to `type: r2`
- AND a deprecation warning is logged for `type: s2`

#### Scenario: R2 configuration defaults region to auto

- GIVEN YAML configuration with `type: r2`, valid credentials, but no `region` specified
- WHEN the adapter is initialized
- THEN `region` is set to `"auto"` by default
- AND R2 global storage is used correctly

### Requirement: Error Handling

The adapter MUST wrap R2/AWS SDK exceptions into domain-appropriate `StorageException` types.

The following error mappings MUST be implemented:

- `NoSuchKey` → `StorageObjectNotFoundException`
- `AccessDenied` → `StorageAccessDeniedException`
- Network errors → `StorageConnectionException`
- All others → `StorageException` (generic)

#### Scenario: R2 adapter handles not found error

- GIVEN an R2 bucket "user-images" does not contain object "missing.png"
- WHEN `download("missing.png")` is called
- THEN `StorageObjectNotFoundException` is thrown
- AND the message contains the key "missing.png"

#### Scenario: R2 adapter handles access denied error

- GIVEN R2 credentials lack permission to bucket "restricted"
- WHEN `upload("file.txt", ...)` is called on bucket "restricted"
- THEN `StorageAccessDeniedException` is thrown
- AND the operation does not succeed

### Requirement: Path Traversal Protection

The adapter MUST validate that object keys do not contain path traversal sequences (`../`, `..\\`).

#### Scenario: R2 adapter rejects path traversal in key

- GIVEN an R2 bucket "user-images"
- WHEN `upload("../etc/passwd", ...)` is attempted
- THEN `StorageSecurityException` is thrown
- AND the object is NOT uploaded

### Requirement: Contract Test Compliance

The `R2StorageAdapter` MUST pass all tests defined in `StorageContractTest.kt`.

All scenarios defined in the platform specification's "Pluggable Storage Abstraction Layer" MUST be
satisfied by the R2 adapter:

- Upload and download streaming
- Presigned URL generation
- Multi-provider resolution
- Large object streaming (>100MB)

#### Scenario: R2 adapter passes storage contract

- GIVEN `StorageContractTest` is executed with `R2StorageAdapter`
- WHEN all contract test methods run
- THEN all tests pass
- AND the R2 adapter is verified as storage-agnostic compliant

## Acceptance Criteria

| ID   | Criterion                                          | Verification Method                    |
|------|----------------------------------------------------|----------------------------------------|
| AC-1 | `R2StorageAdapter` implements `PresignableStorage` | Compilation + interface check          |
| AC-2 | Upload downloads with content integrity            | Unit test with MD5/SHA256 verification |
| AC-3 | Presigned URLs target R2 endpoint                  | Integration test URL inspection        |
| AC-4 | `type: r2` and `type: s2` both work                | Configuration parsing test             |
| AC-5 | Path traversal blocked                             | Security test with malicious keys      |
| AC-6 | Error mapping correct                              | Unit test with mocked SDK errors       |
| AC-7 | All contract tests pass                            | `StorageContractTest` execution        |
| AC-8 | >80% code coverage                                 | JaCoCo/Kover report                    |
