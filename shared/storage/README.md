# Shared Storage Module (`shared:storage`)

Plug-and-play reactive object storage abstraction supporting multi-provider storage backends (Local Filesystem, AWS S3, Cloudflare R2 / S2), path traversal protection, and memory-efficient streaming via Kotlin Coroutines `Flow`.

## Role in the platform

Provides unified file and media asset storage abstractions for Profile Tailors backend services (`server/smp`). It abstracts media bucket storage across local development drives and production S3 / Cloudflare R2 buckets, facilitating media uploads, asset deduplication, signed URLs, and file retrieval.

## Tech stack

- **Runtime & Language**: Java 21, Kotlin 2.4, Coroutines & Flow
- **Framework & Cloud SDKs**: AWS S3 SDK v2, Spring Boot 4.0, Project Reactor Bridge
- **Testing**: JUnit 5, Kotest, MockK

## Getting started

### Prerequisites

- Java JDK `>= 21`
- Gradle wrapper (`./gradlew`)

### Installation

Included automatically as a Gradle project dependency `:shared:storage`.

### Running locally

Run unit tests:

```bash
./gradlew :shared:storage:test
```

### Environment variables

| Variable | Required | Description | Default |
| --- | --- | --- | --- |
| `PLATFORM_STORAGE_DEFAULT` | No | Default active storage provider (`local`, `s3`, `r2`) | `local` |
| `AWS_S3_BUCKET` | No | Target S3 bucket name when using S3 provider | `profiletailors-media` |
| `AWS_S3_REGION` | No | Target AWS region | `us-east-1` |

## Project structure

```text
shared/storage/
├── src/main/kotlin/com/profiletailors/storage/
│   ├── config/      # StorageProperties and Spring Auto-Configuration
│   ├── domain/      # StorageProvider interface, StorageBucket, StorageObject
│   ├── provider/    # LocalFileSystemStorageProvider, S3StorageProvider, R2StorageProvider
│   └── security/    # PathTraversalValidator and file path sanitizers
└── build.gradle.kts
```

## Testing

Run unit tests:

```bash
./gradlew :shared:storage:test
```

## API / Public interface

Main types in package `com.profiletailors.storage`:

- `StorageProvider`: Core interface for `putObject`, `getObject`, `deleteObject`, and `listObjects`.
- `StorageBucket`: Named bucket configuration wrapper.
- `StorageObject`: File metadata and reactive payload container (`Flow<ByteBuffer>`).
- `LocalFileSystemStorageProvider`: Local file system provider with path traversal safeguards.

## Configuration

Sample properties (`application.yaml`):

```yaml
platform:
  storage:
    default: "local"
    providers:
      local:
        type: "filesystem"
        base-path: "./tmp/storage"
```

## Contributing

Please review the [Root CONTRIBUTING.md](../../CONTRIBUTING.md) for workflow rules, commit conventions, and pull request guidelines.

## License

This project is licensed under the GNU Affero General Public License v3.0 (AGPL-3.0). See the [Root LICENSE](../../LICENSE) for details.

---
Back to [Root README](../../README.md)
