# Shared Storage Module

Módulo plug-and-play para abstracción de almacenamiento (Buckets) con soporte multi-provider y streaming reactivo mediante Kotlin Coroutines (Flow).

## Características
- **Multi-provider**: Soporte para Local Filesystem, AWS S3 y Cloudflare R2 (S2).
- **Streaming nativo**: Basado en `kotlinx.coroutines.Flow` para eficiencia de memoria con archivos grandes.
- **Configuración dinámica**: Define N buckets en tu `application.yml` y resuélvelos por nombre.
- **Seguridad**: Protección contra *Path Traversal* en el provider local.
- **Interoperabilidad**: Adaptadores incluidos para `Project Reactor` (WebFlux).

## Instalación

Añade la dependencia en tu `build.gradle.kts`:
```kotlin
implementation(project(":shared-storage"))
```

## Configuración (application.yml)

```yaml
platform:
  storage:
    default: "local"
    providers:
      # Provider Local
      local:
        type: local
        base-path: "/var/app/storage"
      
      # Provider AWS S3
      attachments:
        type: s3
        bucket: "my-app-attachments"
        region: "eu-west-1"
        # AWS SDK usa default credentials chain (IAM roles, env vars, etc.)
      
      # Provider Cloudflare R2 (recomendado)
      public-images:
        type: r2
        bucket: "user-images"
        account-id: "${R2_ACCOUNT_ID}"  # Requerido para R2
        region: "auto"
        # AWS SDK usa default credentials chain
      
      # Provider Cloudflare R2 (deprecated - usa "r2" en su lugar)
      legacy-images:
        type: s2  # Deprecated: usa "r2" en su lugar
        bucket: "legacy-images"
        account-id: "${R2_ACCOUNT_ID}"
        region: "auto"
```

### Notas sobre Configuration

- **`type: r2`**: Configuración canónica para Cloudflare R2. Requiere `account-id`.
- **`type: s2`**: Alias deprecated para R2. Logra un warning al iniciar. Migra a `type: r2`.
- **Credenciales**: El módulo usa AWS SDK default credentials chain (IAM roles en producción, variables de entorno `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`, o credentials file).
- **R2 Endpoint**: Se construye automáticamente como `https://{account-id}.r2.cloudflarestorage.com`.

## Uso

### Inyectar el Registry o el Storage por defecto
```kotlin
@Service
class MyService(
    private val storage: Storage, // Storage por defecto configurado
    private val registry: BucketRegistry // Para resolver buckets específicos
) {
    suspend fun saveProfile(userId: String, content: Flow<ByteArray>) {
        // Guardar en el bucket por defecto
        storage.upload("profiles", "$userId.jpg", content)
        
        // O guardar en uno específico
        val imagesBucket = registry.getStorage("public-images")
        imagesBucket.upload("avatars", "$userId.jpg", content)
    }
}
```

### Generar Presigned URLs (S3/R2)
```kotlin
val url = storage.presignGet("public-images", "avatars/123.jpg", expirySeconds = 600)
```

## Desarrollo y Tests

Para ejecutar los tests del módulo:
```bash
./gradlew :shared:storage:test --tests "*R2Storage*"    # Solo tests de R2
./gradlew :shared:storage:test --tests "*S3Storage*"   # Solo tests de S3
./gradlew :shared:storage:test                         # Todos los tests
```

*Nota: Los tests de integración de S3/R2 requieren Docker para levantar Localstack o un emulador de R2.*

## Providers Soportados

| Type | Provider | Adapter | Presigned URLs |
|------|----------|---------|----------------|
| `local` | Local Filesystem | `LocalFilesystemStorage` | ❌ |
| `s3` | AWS S3 | `S3Storage` | ✅ |
| `r2` | Cloudflare R2 | `R2StorageAdapter` | ✅ |
| `s2` | Cloudflare R2 (deprecated) | `R2StorageAdapter` | ✅ |
