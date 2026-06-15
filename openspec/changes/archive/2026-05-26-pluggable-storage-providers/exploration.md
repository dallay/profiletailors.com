# Exploration: pluggable-storage-providers

## Estado actual

- Proyecto: monorepo con backend Kotlin + Spring Boot (server/smp).
- Stack relevante: Kotlin 2.2, Spring Boot 4, WebFlux, coroutines.
- No se encontró una implementación central de almacenamiento de objetos/buckets en el backend (no
  hay providers S3/FS/S2).
- Lugares relevantes para integrar:
    - server/smp/src/main/kotlin/com/profiletailors/smp/platform/infrastructure —
      bootstrap/configuración de beans y hooks.
    - server/smp/src/main/kotlin/com/profiletailors/smp — bounded contexts (platform, tenancy):
      posibles consumidores de almacenamiento.
    - shared/ (assets, common, spring-boot-common) — ubicación recomendada para la abstracción
      reutilizable.

## Áreas afectadas

- platform/infrastructure — auto-configuración y beans (PlatformBootstrapConfiguration.kt).
- Módulos que necesiten almacenar artefactos, assets o archivos de usuario — evaluar por caso.
- CI/Build — añadir dependencias (AWS SDK v2 u otro cliente S3-compatible) y tests.
- openspec/changes — nuevo cambio para SDD: pluggable-storage-providers.

## Enfoques considerados

1) API mínima sin streaming (Bajo esfuerzo)
    - Interfaz sencilla en shared/common con métodos suspend para put/get/delete/list.
    - Implementaciones: LocalFS (mapear buckets a directorios), S3/S2 (cliente S3 sync o async
      adaptado).
    - Pros: rápido, fácil de probar.
    - Contras: no es eficiente para objetos grandes, no soporta streaming nativo ni presigned URLs
      avanzadas.

2) API con streaming basada en coroutines/Flow (Recomendada, Esfuerzo Medio)
    - Interfaz con streaming:
        - suspend fun upload(bucket, key, content: Flow<ByteArray>, metadata: Map<String, String>)
        - fun download(bucket, key): Flow<ByteArray>
        - suspend fun presignGet(bucket,key, expiry): URL
    - Implementación: LocalFS usa Dispatchers.IO; S3 usa cliente async adaptado a Flow.
    - Pros: memoria eficiente, encaja con coroutines y WebFlux, soporta archivos grandes y
      multipart.
    - Contras: más código y pruebas necesarias; manejar backpressure.

3) Auto-configuración de Spring Boot + registry (Arquitectura plug & play)
    - StorageAutoConfiguration que crea beans condicionalmente según propiedades:
        - platform.storage.default=local|s3|s2
        - platform.storage.providers.{name} = {type: s3, bucket:, region:, endpoint:,
          credentials:...}
    - Exponer BucketRegistry para resolver providers por nombre.
    - Pros: configurable, soporta múltiples providers al mismo tiempo.
    - Contras: diseño e implementación más extensos.

4) Reusar librerías existentes (Low/Med)
    - Uso de AWS SDK v2 (S3 Async) o cliente S3 compatible; Cloudflare S2 usa endpoint S3
      compatible.
    - Pros: características maduras (multipart, presign, retries).
    - Contras: dependencia pesada y manejo de compatibilidad.

## Recomendación

Combinar (2) + (3):

- Definir una API de Storage basada en coroutines/Flow en shared (por ejemplo shared/storage o
  shared/spring-boot-common).
- Implementar providers:
    - LocalFilesystemProvider: operaciones de archivo con Dispatchers.IO, streaming desde/hacia
      Flow.
    - S3Provider: usar AWS SDK v2 async adaptado a coroutines/Flow.
    - S2Provider: usar mismo cliente con endpoint personalizado para Cloudflare S2.
- Proveer StorageAutoConfiguration, BucketRegistry y typed @ConfigurationProperties para declarar
  múltiples buckets/providers en application.yml.
- Exponer utilidades: presigned URLs, metadatos, y endpoints de verificación/health.

Notas técnicas / idioms Kotlin

- Preferir suspend functions + kotlinx.coroutines.Flow para streaming. Proveer adaptadores
  Reactor <-> Flow para interoperar con WebFlux.
- Para I/O bloqueante usar withContext(Dispatchers.IO).
- Definir interfaces y data classes (BucketConfig, ObjectMetadata).
- Instrumentar con los hooks de observabilidad ya presentes (MetricsHook/AuditHook).
- Usar @ConfigurationProperties para mapear la configuración por providers.

## Riesgos

- Manejo de streaming y backpressure requiere pruebas robustas.
- Gestión de secretos/credenciales (no subir credenciales al repo).
- Tamaño de dependencias (AWS SDK) — considerar alternativas o minimizar módulos.
- Decisiones de API podrían necesitar extensión (ACLs, SSE, lifecycle) en el futuro.

## Tareas (alto nivel y estimado)

1. Especificación (sdd-spec) — definir forma de la API y features (1 día)
2. Crear módulo shared/storage + interfaces (0.5–1 día)
3. Implementar LocalFilesystemProvider + tests (0.5–1 día)
4. Implementar S3Provider (AWS SDK v2 async) + integration tests (1–2 días)
5. Implementar S2Provider (endpoint override) + tests (0.5 día)
6. StorageAutoConfiguration + BucketRegistry + @ConfigurationProperties (0.5–1 día)
7. Documentación y ejemplos de application.yml (0.5 día)
8. Integración CI/Tests (1 día)
   Total aproximado: 5–8 días de desarrollador.

## Listo para propuesta

Sí. Nombre del change confirmado: `pluggable-storage-providers`.
Decisión de API: usar suspend/Flow (confirmada).

---
Generado automáticamente como parte del ciclo SDD. Siguiente paso: crear la propuesta (sdd-spec) y
los escenarios Given/When/Then. Si quieres que proceda ahora, comienzo a generar la especificación y
la guardo en `openspec/changes/pluggable-storage-providers/spec.md`.
