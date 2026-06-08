# Spec: pluggable-storage-providers

## Resumen ejecutivo

Implementar un Storage Abstraction Layer (SAL) pluggable para soportar múltiples providers de "
buckets" (Local filesystem, AWS S3, Cloudflare S2) con una API basada en Kotlin coroutines (
suspend + kotlinx.coroutines.Flow). El módulo será reusable desde otros bounded contexts del backend
y configurable vía Spring Boot properties.

Objetivos:

- API de almacenamiento eficiente para objetos grandes (streaming con Flow).
- Soportar múltiples providers simultáneamente, configurables por nombre.
- Proveer presigned URLs, metadata, y operaciones CRUD básicas.
- Integración con observabilidad existente (MetricsHook/AuditHook) y bean auto-configurable.

## Alcance

- Crear un nuevo módulo: `shared/storage` (o `shared/spring-boot-common/storage`) con la abstracción
  y providers.
- Implementaciones iniciales: LocalFilesystemProvider, S3Provider (AWS SDK v2 async), S2Provider (
  endpoint override).
- Spring Boot AutoConfiguration: `StorageAutoConfiguration` y `StorageProperties` (
  @ConfigurationProperties).
- Registry para resolver providers por nombre: `BucketRegistry`.
- Adaptadores Reactor <-> Flow para interoperar con WebFlux cuando haga falta.
- Unit + integration tests para LocalFS y S3 (mocked).

Fuera de alcance (puede añadirse luego):

- Política de ciclo de vida de objetos (retención, lifecycle rules).
- ACLs avanzadas y políticas de CORS (solo soporte básico mediante configuración de provider).

## Requisitos funcionales (MUST)

1. El API debe exponer operaciones para:
    - upload (streaming)
    - download (streaming)
    - delete
    - list
    - presignGet
2. Debe ser posible declarar múltiples providers en application.yml y resolverlos por nombre en
   tiempo de ejecución.
3. Debe usarse coroutines + Flow como primitives para carga/descarga.
4. LocalFilesystem debe mapear cada bucket a un directorio en disco y respetar path traversal (
   proteger contra ..).
5. S3/S2 debe soportar presigned GET URLs y multipart upload para archivos grandes (usar cliente que
   lo permita).
6. Debe existir un bean defaultStorage para inyección por tipo y un BucketRegistry para resolver por
   nombre.

## Requisitos no funcionales

- Buenas prácticas Kotlin: suspend, Flow, withContext(Dispatchers.IO) para operaciones bloqueantes.
- Instrumentar latencias y errores con MetricsHook/AuditHook.
- Evitar bloquear el EventLoop en WebFlux.
- Configuración segura: no commitear credenciales, soportar variables de entorno y SecretManager
  cuando sea posible.

## Diseño de la API (Kotlin)

Interfaz principal:

package com.profiletailors.storage

interface Storage {
suspend fun upload(bucket: String, key: String, content: Flow<ByteArray>, metadata: Map<String,
String> = emptyMap())
fun download(bucket: String, key: String): Flow<ByteArray>
suspend fun delete(bucket: String, key: String)
suspend fun list(bucket: String, prefix: String = ""): List<String>
suspend fun presignGet(bucket: String, key: String, expirySeconds: Long = 300): String
}

Data classes auxiliares:

package com.profiletailors.storage

data class BucketConfig(
val name: String,
val type: String, // local | s3 | s2
val properties: Map<String, String>
)

Registro/Resolución:

package com.profiletailors.storage

interface BucketRegistry {
fun getStorage(bucketName: String): Storage
}

Adapters Reactor <-> Flow (utilidades):

- fun Flow<ByteArray>.asFlux(): Flux<DataBuffer>
- fun Flux<DataBuffer>.asFlow(): Flow<ByteArray>

Kotlin idioms:

- Exponer overloads convenience que acepten InputStream/OutputStream/ByteArray para pruebas.
- Documentar that Flow chunks represent a sequence of bytes; prefer chunk size 8KB by default.

## Configuración (application.yml)

platform:
storage:
default: "local"
providers:
local:
type: local
base-path: "/var/app/storage"
attachments:
type: s3
bucket: "profiletailors-attachments"
region: "eu-west-1"
access-key-id: "${AWS_ACCESS_KEY_ID:}"
secret-access-key: "${AWS_SECRET_ACCESS_KEY:}"
endpoint: "" # opcional, para S2 o compatibilidad

## Contratos y validaciones

- upload debe lanzar StorageException (subtipo) en errores.
- download debe lanzar NotFound exception si el objeto no existe.
- list devuelve keys relativos al bucket (sin / prefix guard).

## Given/When/Then Scenarios (Aceptación)

Scenario 1: Upload y Download local
Given un bucket "local-test" mapeado a /tmp/storage/local-test
When subo un archivo por streaming con key "foo/bar.txt"
Then puedo descargarlo y su contenido coincide
And la lista con prefix "foo/" contiene "foo/bar.txt"

Scenario 2: Presigned URL S3
Given un provider s3 configurado para bucket "attachments"
When solicito presignGet("attachments", "invoices/1.pdf", 600)
Then recibo una URL válida que permite descargar el objeto dentro de 600 segundos

Scenario 3: Seguridad de paths LocalFS
Given un bucket local mapeado a /var/data/bucket
When intento subir con key "../secret.txt"
Then la operación falla con StorageSecurityException (previene path traversal)

Scenario 4: Multi-provider resolution
Given providers "local" y "attachments" configurados
When pido registry.getStorage("attachments")
Then recibo un Storage asociado a S3Provider

Scenario 5: Large object streaming
Given un objeto grande (>100MB)
When subo mediante Flow chunks
Then el upload no consume memoria proporcional al tamaño del archivo
And la descarga también streamea en chunks

## Criterios de aceptación

- Implementación de la interface Storage + providers Local/S3/S2.
- StorageAutoConfiguration y StorageProperties con mapeo a providers.
- Tests unitarios y de integración que cubran los escenarios clave.
- Documentación con ejemplo de application.yml y snippets de uso.

## Tasks (desglosadas)

1. Crear módulo shared/storage con gradle settings (0.5d)
2. Definir API & data classes + unit tests de contrato (0.5d)
3. Implementar LocalFilesystemProvider + tests (0.5–1d)
4. Implementar S3Provider (AWS SDK v2 async) + tests (1–2d)
5. Implementar S2Provider (endpoint override) + tests (0.5d)
6. Implementar StorageAutoConfiguration + StorageProperties + BucketRegistry (0.5–1d)
7. Add Reactor <-> Flow adapters & WebFlux sample usage (0.5d)
8. Integration tests and CI updates (1d)
9. Docs and examples (0.5d)

## Riesgos y mitigaciones

- Dependencias pesadas: usar módulos AWS S3 mínimos; preferir S3 async client solo cuando se
  necesite.
- Backpressure/streaming bugs: añadir tests con grandes streams y usar bounded chunk sizes.
- Secrets leak: instruir uso de env vars y SecretManager; no checkear creds en repo.

## Artefactos a crear

- openspec/changes/pluggable-storage-providers/spec.md (este archivo)
- Módulo: shared/storage/
- Nueva configuración: StorageProperties @ConfigurationProperties
- Tests: unit + integration under shared/storage/src/test

## Next recommended

- Approve spec. Luego ejecutar sdd-design para generar bocetos de clases y diagramas de secuencia.
  Después pasar a sdd-apply para implementar el módulo.

---

Especificación creada automáticamente por el ciclo SDD para `pluggable-storage-providers`.
