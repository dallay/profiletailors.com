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
        access-key-id: "${AWS_ACCESS_KEY_ID:}"
        secret-access-key: "${AWS_SECRET_ACCESS_KEY:}"
      
      # Provider Cloudflare R2 (S2 compatible)
      public-images:
        type: s2
        bucket: "user-images"
        endpoint: "https://<id>.r2.cloudflarestorage.com"
        access-key-id: "${R2_ACCESS_KEY:}"
        secret-access-key: "${R2_SECRET_KEY:}"
        region: "auto"
```

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

### Generar Presigned URLs (S3/S2)
```kotlin
val url = storage.presignGet("public-images", "avatars/123.jpg", expirySeconds = 600)
```

## Desarrollo y Tests

Para ejecutar los tests del módulo:
```bash
./gradlew :shared-storage:test
```
*Nota: Los tests de integración de S3 requieren Docker para levantar Localstack.*
