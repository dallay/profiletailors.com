# Design: pluggable-storage-providers

## Technical Approach
Implementaremos un Storage Abstraction Layer (SAL) en un nuevo módulo shared/storage. El SAL expondrá una interfaz Storage basada en Kotlin coroutines (suspend + Flow) y ofrecerá implementaciones para: LocalFilesystemProvider, S3Provider (AWS SDK v2 async) y S2Provider (endpoint override). Se añadirá una StorageAutoConfiguration para crear beans condicionalmente desde application.yml y un BucketRegistry para resolver providers por nombre.

El diseño sigue la recomendación de la spec: combinación de API basada en Flow (para streaming) y una auto-configuración plug & play.

## Architecture Decisions

### Decision: Use Flow-based streaming API
**Choice**: Usar suspend + kotlinx.coroutines.Flow para upload/download.
**Alternatives considered**: Exponer Reactor Flux-only API (más integrado con WebFlux), o API bloqueante simple.
**Rationale**: Proyecto usa Kotlin coroutines ampliamente; Flow ofrece memory-efficient streaming y es fácil adaptar a Reactor cuando necesario.

### Decision: Module placement in shared/
**Choice**: Crear módulo `shared/storage` dentro del monorepo.
**Alternatives considered**: Colocar dentro de server/smp o shared/spring-boot-common.
**Rationale**: storage es una abstracción compartida entre bounded contexts; shared/ permite reusar sin acoplar al servicio principal.

### Decision: Spring Boot AutoConfiguration + Named Registry
**Choice**: Implementar StorageAutoConfiguration + StorageProperties + BucketRegistry.
**Alternatives considered**: Manual bean registration o per-module config.
**Rationale**: AutoConfiguration permite declarativo y plug & play; Registry permite multi-bucket multi-provider runtime resolution.

### Decision: Use AWS SDK v2 Async for S3 provider
**Choice**: Usar AWS SDK v2 async client y adaptar a coroutines.
**Alternatives considered**: AWS SDK v1 sync (bloqueante) o S3-compatible HTTP clients.
**Rationale**: SDK v2 async ofrece non-blocking I/O, multipart support, presign capabilities; adapt to Flow for coroutine support.

## Data Flow

Upload flow (high-level):

Client (Controller) -> Storage.upload(bucket,key, Flow<ByteArray>) -> selected Provider
    - LocalFilesystem: withContext(Dispatchers.IO) write chunks to temp file, then move
    - S3Provider: stream to S3 multipart (using async client)

Download flow:
Provider download -> Flow<ByteArray> -> Controller -> Response (Flux/DataBuffer via adapter)

Registry resolution:
Controller/Service requests storage via BucketRegistry.getStorage(name) -> returns provider bean

## File Changes

| File | Action | Description |
|------|--------|-------------|
| openspec/changes/pluggable-storage-providers/design.md | Create | Este archivo de diseño |
| shared/storage/build.gradle.kts | Create | Nuevo módulo gradle (plugins, dependencies) |
| settings.gradle.kts | Modify | Incluir module ':shared:storage' en settings si aplica |
| shared/storage/src/main/kotlin/com/profiletailors/storage/Storage.kt | Create | Interfaz principal Storage |
| shared/storage/src/main/kotlin/com/profiletailors/storage/BucketRegistry.kt | Create | Registry interface + impl |
| shared/storage/src/main/kotlin/com/profiletailors/storage/LocalFilesystemStorage.kt | Create | Local FS provider implementation |
| shared/storage/src/main/kotlin/com/profiletailors/storage/S3Storage.kt | Create | S3 provider impl (uses AWS SDK v2 async) |
| shared/storage/src/main/kotlin/com/profiletailors/storage/StorageAutoConfiguration.kt | Create | AutoConfig + StorageProperties bindings |
| shared/storage/src/main/kotlin/com/profiletailors/storage/StorageProperties.kt | Create | @ConfigurationProperties mapping |
| shared/storage/src/main/kotlin/com/profiletailors/storage/FlowReactorAdapters.kt | Create | Flow <-> Flux adapters |
| shared/storage/src/test/kotlin/... | Create | Unit tests for interface and LocalFS provider |

Modificaciones menores:
- server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure/PlatformBootstrapConfiguration.kt
  - Posible registro del bean defaultStorage o referencia a shared storage auto-configuración (no forzado aquí, auto-config será independiente).

## Interfaces / Contracts

(Refrescar de spec)

package com.profiletailors.storage

interface Storage {
    suspend fun upload(bucket: String, key: String, content: Flow<ByteArray>, metadata: Map<String, String> = emptyMap())
    fun download(bucket: String, key: String): Flow<ByteArray>
    suspend fun delete(bucket: String, key: String)
    suspend fun list(bucket: String, prefix: String = ""): List<String>
    suspend fun presignGet(bucket: String, key: String, expirySeconds: Long = 300): String
}

BucketRegistry impl sketch:

class InMemoryBucketRegistry(private val providers: Map<String, Storage>): BucketRegistry {
    override fun getStorage(bucketName: String): Storage = providers[bucketName]
        ?: throw NoSuchBucketException(bucketName)
}

LocalFilesystemStorage sketch:
- basePath per bucket
- upload: write to temp file using newFile = basePath/normalized(key); ensure normalized path inside basePath; write chunks with withContext(Dispatchers.IO)
- download: open InputStream and emit chunks as Flow

S3Storage sketch:
- Use S3AsyncClient
- For upload, accumulate multipart or use TransferManager (if available) adapted to coroutines
- For download, use GetObjectRequest and adapt ResponsePublisher to Flow

## Testing Strategy

| Layer | What to Test | Approach |
|-------|--------------|----------|
| Unit | Storage contract behavior | Tests for interface: upload->download equality; list; delete; error cases |
| Unit | LocalFS provider | Temporary directory filesystem tests, path traversal attempts fail |
| Integration | S3 provider | Use Localstack or mocked S3 (WireMock or AWS SDK TestUtils) to validate presign and streaming |
| Integration | AutoConfiguration | Spring Boot test with test properties to create multiple providers and ensure registry wiring |

## Migration / Rollout
No migration de datos requerida. Rolling out:
- Merge shared/storage module behind feature flag or config absent by default.
- Enable provider in staging with S3 mock or real bucket.
- Switch consumers to use BucketRegistry.getStorage("attachments") or inject default Storage.

## Open Questions
- Prefer TransferManager for uploads? (helps multipart) — requires adding aws-s3-transfer-manager dependency. Decision: start with S3AsyncClient + simple multipart implementation; consider TransferManager later.
- Naming: shared/storage vs shared/spring-boot-common/storage — prefer shared/storage unless repo conventions force elsewhere.
- Should we expose higher-level helpers for presigned PUT as well? Not in initial scope.


