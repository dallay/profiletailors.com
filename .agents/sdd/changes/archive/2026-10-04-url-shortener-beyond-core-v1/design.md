# Diseño: URL shortener más allá de Core V1

## Enfoque técnico

Extender el módulo SMP `shortlinks` existente, manteniendo CQRS, hexagonalidad, R2DBC y PostgreSQL como autoridad. Core V1 está comprometido en `HEAD 3178a883` (PR #1305); este cambio añade capacidades, no reimplementa el baseline. Los specs delta vinculantes son `management-api`, `redirect-data-plane`, `domain-management`, `analytics`, `abuse-security` y `operations-observability`.

## Decisiones arquitectónicas

| Opción | Trade-off | Decisión |
|---|---|---|
| Entrega de eventos | Outbox DB ofrece durabilidad, pero añadir cada click a la transacción de redirect añade latencia y puede hacer fallar el redirect | El redirect llama `tryOffer()` sobre una cola local acotada, sin esperar al broker ni fallar por analytics. Un publisher en background agrupa y publica lotes. Si la cola desborda, se pierde el evento en memoria; se observa el overflow y se reconcilia desde el edge log durable, retenido 30 días. Handoff al publisher es best-effort; tras la aceptación del broker la entrega es at-least-once. Esta garantía comienza en el límite de aceptación del broker y depende de su semántica de aceptación; consumidores deduplican por `eventId`. |
| Dominios personalizados | Cloudflare automatiza la provisión/validación TLS; requiere cuenta, zona, API token y configuración operativa apropiada | Usar Cloudflare for SaaS / Custom Hostnames. La plataforma orquesta la API de Cloudflare; el cliente configura su DNS apuntando al target SaaS indicado. Cloudflare gestiona TLS. Un hostname es `ACTIVE` solo cuando el estado del hostname y `ssl.status` son ambos `active`, y el DNS apunta al target SaaS. La API admite creación/lectura del Custom Hostname y reporta ambos estados; la provisión requiere cuenta/zona elegibles, permisos/token y configuración de SaaS/origin en Cloudflare. |
| Analytics | Lecturas separadas de redirect evitan bloquear el plano de datos; agregados y reconciliación añaden almacenamiento | Consumidor idempotente y modelo de lectura separado, tenant-scoped. No llega IP raw al store. Sin cookies, browser storage, JavaScript ni fingerprinting. Retener solo dominio del referrer y categoría de user-agent; derivar visitante mediante HMAC-SHA256 con secreto rotatorio cada 30 días. Retención: raw 30 días, agregado minuto 7 días, hora 90 días, día 25 meses y edge reconciliation logs 30 días. Solicitud de borrado/acceso se atiende inmediatamente; borrado de raw en ≤24 h, agregados en ≤7 días y tombstone de backups en ≤30 días. |
| QR | Cálculo bajo demanda evita almacenamiento e invalidación | Generar localmente QR Model 2 en UTF-8 para la URL corta únicamente. SVG por defecto a 512 px, corrección M y quiet zone de 4 módulos; también PNG. ZXing core+javase 3.5.4 es la referencia de dependencia indicada por el usuario, no una afirmación de que sea la versión más reciente. |

## Arquitectura y flujo

`RedirectController → ResolveLinkQuery/Handler → LinkFinderRepository (Postgres) + LinkCachePort (Caffeine/Redis) → 302/410/404/403`

Tras resolver el redirect, `tryOffer()` intenta encolar localmente el evento sin espera ni dependencia de éxito analítico. Un worker toma lotes de la cola y los entrega al broker. Overflow se registra como señal operativa y se reconcilia leyendo el edge log durable; no se presenta el tramo anterior a la aceptación del broker como at-least-once. El redirect no hace fetch del destino.

Mutación y evento de estado comparten transacción y luego invalidan cachés (≤5 s; cuarentena inmediata). Redis fallido recurre a DB; caída de DB puede usar hit cacheado. L1 TTL ≤30 s y Redis ≤5 min, limitados por `expiresAt`; negativo de réplica se confirma en primaria.

La integración de dominios crea y consulta Custom Hostnames mediante la API de Cloudflare. El cliente configura el DNS; Cloudflare verifica/provisiona TLS. La transición a `ACTIVE` requiere en conjunto hostname `active`, SSL `active` y DNS al target SaaS.

Los eventos aceptados alimentan el consumidor idempotente y las vistas de analytics. Endpoints link-level `/summary`, `/timeseries` y `/breakdown` son autenticados y tenant-scoped. Granularidad: minuto hasta 24 h, hora hasta 90 días y día hasta 25 meses. `from`/`to` son RFC3339 en intervalo semiabierto `[from,to)`; `tz` es IANA. Breakdown limitado a dimensiones controladas. Respuestas declaran `dataThrough` y `reconciledThrough`; `uniqueVisitors` es no aditivo entre buckets.

## Cambios de archivos

| Archivo | Acción | Descripción |
|---|---|---|
| `server/smp/src/main/kotlin/com/profiletailors/smp/shortlinks/domain/` | Modificar/crear | Puertos/modelos para dominios, entrega de eventos, analytics y consultas QR. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/shortlinks/application/` | Modificar/crear | Handlers CQRS, coordinación de idempotencia, concurrencia, ETag y abuso. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/shortlinks/infrastructure/http/` | Modificar/crear | Controladores `/api/v1`, DTO, errores RFC 9457, consultas y QR. |
| `server/smp/src/main/kotlin/com/profiletailors/smp/shortlinks/infrastructure/persistence/` | Modificar/crear | Repositorios R2DBC, logs/reconciliación y agregados analytics. |
| `server/smp/src/main/resources/db/changelog/shortlinks/` | Crear | Liquibase aditivo: dominios, analytics, logs/reconciliación e índices necesarios. |
| `server/smp/src/test/kotlin/com/profiletailors/smp/shortlinks/`, `server/smp/src/test/resources/features/` | Modificar/crear | Unitarias, integración PostgreSQL y BDD observable. |
| OpenAPI y `docs/api-versioning.md` | Modificar | Contratos nuevos y compatibilidad v1. |

## Interfaces y contratos

Puertos propuestos: `EventDeliveryPort` con handoff por lotes al broker, `DomainCustomHostnamePort`, `ClickAnalyticsRepository.recordOnce(eventId, ...)` y consultas por link/periodo. La cola local ofrece `tryOffer(event)` y reporta explícitamente rechazo por capacidad; no promete persistencia. El edge log durable es la fuente de reconciliación de eventos perdidos por overflow. `eventId` sostiene dedupe downstream.

Analytics: `/summary`, `/timeseries`, `/breakdown`; granularidades y ventanas según arriba, intervalo `[from,to)`, `from`/`to` RFC3339, `tz` IANA, dimensiones de breakdown controladas y marcadores `dataThrough`/`reconciledThrough`. `uniqueVisitors` no puede sumarse entre buckets. Privacidad/retención según la tabla de decisiones.

QR: salida SVG predeterminada de 512 px, corrección M, quiet zone 4; salida PNG opcional; payload siempre `shortURL`, nunca destino largo.

Mantener `application/vnd.api.v1+json`, workspace scope y RFC 9457. UUIDv7; `(domain_id, short_code)` único; Base62 criptográfico de 10 caracteres y máximo 5 intentos; alias case-sensitive y reserved list de spec. Idempotencia 24 h (replay idéntico devuelve resultado; payload distinto 409); mutaciones `If-Match` y conflicto 412.

## Seguridad, migración y operación

Autorización tenant-scoped; rate limits configurables: 100 creaciones/min y 600 operaciones/min por usuario, con `Retry-After`. Redirect nunca hace fetch; cualquier fetcher futuro bloqueará IPs no públicas y DNS rebinding. No almacenar IP raw ni permitir que llegue al store. Sin cookies, almacenamiento del navegador, JavaScript ni fingerprinting; secreto HMAC-SHA256 de visitante rota cada 30 días. Métricas operativas sin URL/código/usuario/IP ni alta cardinalidad; observar overflow, backlog, publicación y reconciliación.

Liquibase aditivo, preservando enlaces y API; backfill de dominio predeterminado. No activar dominios custom hasta que Cloudflare hostname, SSL y DNS SaaS estén activos. La operación requiere cuenta/zona Cloudflare for SaaS configurada, credencial API con permisos mínimos y configuración del SaaS/origin; no se incluye secreto en repositorio. Rollout: migración, cola/publisher, logs de reconciliación y APIs; load test representativo precede cualquier afirmación de SLO/capacidad. Objetivos 99.99%, latencias y volúmenes no son garantías.

Retención: raw 30 días, minuto 7 días, hora 90 días, día 25 meses y logs de reconciliación 30 días. Acceso/borrado se procesa inmediatamente; raw ≤24 h, agregados ≤7 días y tombstone de backups ≤30 días.

## Estrategia de pruebas

| Capa | Cobertura |
|---|---|
| Unit | Estados, alias, colisiones, ETag, overflow observable, dedupe, privacidad, rangos/granularidades y QR encode/decode. |
| Integración | Liquibase/Postgres, handoff/batching, aceptación broker, reintentos/dedupe, reconciliación desde edge log, analytics, concurrencia y caché/fallos con Testcontainers. |
| BDD/API | Seis specs, auth/tenant, errores, dominios y abuso; `just backend-bdd-fast` y PostgreSQL donde aplique. |
| Carga | Perfiles sostenido/burst/creación; medir impacto de `tryOffer()` y publisher antes de declarar objetivos. |

Sin migración destructiva ni garantía de entrega analítica antes de la aceptación del broker. La pérdida de eventos por overflow debe ser detectable y reconciliable desde el edge log durante su retención.

## Preguntas abiertas

- [ ] Broker concreto, límites/reintentos del publisher por lotes y alertas operativas; confirmar la configuración de cuenta/zona, permisos del service account/API token y SaaS/origin para cada entorno.
- [ ] Formato/campos, particionado y procedimiento operacional de lectura/reconciliación del edge log durable.
