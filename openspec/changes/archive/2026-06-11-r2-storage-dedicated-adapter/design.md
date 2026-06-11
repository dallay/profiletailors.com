# Design: R2 Dedicated Storage Adapter

## Architecture

The `R2StorageAdapter` follows hexagonal architecture principles, implementing the `PresignableStorage` port directly rather than delegating to `S3Storage`. This provides:

1. **Clear ownership**: R2-specific client configuration lives in one place
2. **Testability**: Mock-friendly without depending on S3Storage internals
3. **Extensibility**: R2-specific behavior can be added without modifying S3Storage
4. **Observability**: Distinct metrics/logs for R2 operations

## Component Design

### R2StorageAdapter

```
┌─────────────────────────────────────────────────────────────┐
│                    R2StorageAdapter                          │
│  ┌───────────────────────────────────────────────────────┐  │
│  │  Implements: PresignableStorage                       │  │
│  │  Dependencies: S3AsyncClient (AWS SDK v2)             │  │
│  └───────────────────────────────────────────────────────┘  │
│                                                              │
│  Responsibilities:                                           │
│  - R2 client lifecycle (builder with R2-specific defaults)    │
│  - S3-compatible operations via AWS SDK v2                   │
│  - R2 endpoint configuration                                 │
│  - Error mapping to domain exceptions                        │
└─────────────────────────────────────────────────────────────┘
```

### R2 Client Builder (Factory)

```kotlin
object R2ClientFactory {
    fun create(config: StorageProperties.R2Provider): S3AsyncClient {
        return S3AsyncClient.builder()
            .region(Region.of(config.region ?: "auto"))
            .endpointOverride(Uri.of(config.endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsCredentials.create(config.accessKeyId, config.secretAccessKey)
            ))
            .serviceConfiguration(
                ServiceConfiguration.builder()
                    .pathStyleAccessEnabled(true) // R2 requires this
                    .build()
            )
            .build()
    }
}
```

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| R2 client type | `S3AsyncClient` | R2 is S3-compatible; AWS SDK v2 supports coroutines |
| Path style access | `true` | R2 requires path-style addressing |
| Region default | `"auto"` | R2 uses global storage; `auto` enables automatic routing |
| Error handling | Domain exceptions | Matches existing `StorageException` hierarchy |

## File Structure

```
server/smp/modules/storage/src/
├── main/kotlin/com/profiletailors/storage/
│   ├── domain/
│   │   ├── Storage.kt
│   │   ├── PresignableStorage.kt
│   │   ├── BucketRegistry.kt
│   │   └── StorageException.kt
│   ├── application/
│   │   └── StorageService.kt
│   └── infrastructure/
│       ├── S3Storage.kt
│       ├── S2Storage.kt          # DEPRECATED: alias to R2StorageAdapter
│       ├── R2StorageAdapter.kt   # NEW: dedicated R2 implementation
│       ├── R2ClientFactory.kt    # NEW: R2 client builder
│       ├── LocalFilesystemStorage.kt
│       ├── StorageProperties.kt
│       └── StorageAutoConfiguration.kt
└── test/kotlin/com/profiletailors/storage/
    ├── S3StorageUnitTests.kt
    ├── R2StorageUnitTests.kt     # NEW
    ├── R2StorageIntegrationTest.kt # NEW
    ├── StorageContractTest.kt    # Abstract, parameterized
    └── StorageIntegrationTest.kt
```

## Sequence Diagram: Upload Flow

```
Client                    R2StorageAdapter              R2ClientFactory
  │                            │                              │
  │ upload(key, content, ...)  │                              │
  │───────────────────────────>│                              │
  │                            │                              │
  │                            │ validateKey(key)            │
  │                            │──────────┐                   │
  │                            │          │ (pass/fail)       │
  │                            │<─────────┘                   │
  │                            │                              │
  │                            │ PutObjectRequest             │
  │                            │─────────────────────────────>│
  │                            │                              │
  │                            │            S3AsyncClient    │
  │                            │                              │
  │                            │              PutObjectResponse│
  │                            │<─────────────────────────────│
  │                            │                              │
  │ success                    │                              │
  │<───────────────────────────│                              │
  │                            │                              │
```

## Error Mapping Strategy

| R2/AWS SDK Exception | Domain Exception |
|---------------------|------------------|
| `NoSuchKey` | `StorageObjectNotFoundException` |
| `NoSuchBucket` | `StorageBucketNotFoundException` |
| `AccessDenied` | `StorageAccessDeniedException` |
| `TooManyRequests` | `StorageRateLimitException` |
| `ServiceUnavailable` | `StorageConnectionException` |
| Other | `StorageException` |

## Backward Compatibility Strategy

```
Configuration YAML                          Resolution
─────────────────────────────────────────────────────────────────
type: r2                    ──────────────►  R2StorageAdapter (canonical)
type: s2                    ──────────────►  R2StorageAdapter (alias + deprecation warning)
```

The `StorageAutoConfiguration` will:
1. Check provider `type` field
2. If `s2`, log deprecation warning and treat as `r2`
3. If `r2`, instantiate `R2StorageAdapter` directly

## Test Design

### Unit Tests (R2StorageUnitTests.kt)

Using `mockk` and `S3Mock` or SDK test utils:

```kotlin
class R2StorageUnitTests {
    @Test
    fun `upload should delegate to S3 client`(): Unit = runTest {
        // Given: mocked S3AsyncClient
        // When: adapter.upload()
        // Then: verify PutObjectRequest sent
    }

    @Test
    fun `download should throw StorageObjectNotFoundException when key missing`(): Unit = runTest {
        // Given: S3AsyncClient returns NoSuchKey
        // When: adapter.download()
        // Then: StorageObjectNotFoundException
    }

    @Test
    fun `presignGet should return R2 endpoint URL`(): Unit = runTest {
        // Given: configured R2 endpoint
        // When: adapter.presignGet()
        // Then: URL contains R2 endpoint, not AWS
    }
}
```

### Integration Tests (R2StorageIntegrationTest.kt)

Using LocalStack with R2 emulation or testcontainers:

```kotlin
@SpringBootTest
class R2StorageIntegrationTest {
    @Test
    fun `end-to-end upload and download`(): Unit = runTest {
        val adapter = R2StorageAdapter(R2ClientFactory.create(testConfig))
        adapter.upload("test/file.txt", bytesFlow)
        val downloaded = adapter.download("test/file.txt")
        assertEquals(originalContent, downloaded.readBytes())
    }
}
```

### Contract Tests

`StorageContractTest` will be parameterized to include R2 alongside S3 and LocalFS.

## Dependencies Added

```kotlin
// No new dependencies required
// Uses existing:
// - software.amazon.awssdk:s3 (S3AsyncClient)
// - kotlinx-coroutines (for async operations)
// - mockk (for unit testing)
```

## Migration Path

1. **Phase 1**: Create `R2StorageAdapter` alongside existing `S2Storage`
2. **Phase 2**: Add `type: r2` support to auto-configuration
3. **Phase 3**: Mark `type: s2` as deprecated alias
4. **Phase 4**: Update documentation to recommend `type: r2`
5. **Phase 5** (future): Remove `S2Storage.kt` in next major version
