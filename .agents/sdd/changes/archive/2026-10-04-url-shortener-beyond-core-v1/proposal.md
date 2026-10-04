# Propuesta: Evolución del URL shortener más allá de Core V1

## Intención

Evolucionar el contexto de shortlinks para cumplir la especificación normativa íntegra y la lista de aceptación literal proporcionadas por el usuario en la conversación. El resultado esperado es una plataforma de enlaces cortos segura y operable que preserve un redirect rápido y resiliente, gestione enlaces de forma condicional e idempotente, y añada los flujos de analítica asíncrona, dominios custom verificados, abuso/cuarentena, QR y observabilidad/capacidad requeridos. La fuente normativa no está pendiente de recuperación: se debe trasladar completa y fielmente al artefacto de especificación en su fase correspondiente.

## Estado del checkout y evidencia de partida

La rama actual es `plasma_titan_dips_11h27`, con `HEAD` `3178a883c2936d04b083a9d184e81e6a921504c5`, idéntico a `origin/main` en la comprobación realizada. El commit `feat(shortlinks): add Core V1 backend with idempotent management and safe redirect (#1305)` incluye Core V1; por tanto, el fast-forward y su presencia comprometida en HEAD están comprobados. Esta evidencia no afirma que esté desplegado ni reemplaza la verificación de comportamiento mediante pruebas.

La implementación local observada incluye Core V1: gestión y resolución de enlaces, validación de URL/alias, idempotencia, estados y expiración, caché, persistencia R2DBC, migraciones y pruebas. Su estado y cobertura deben comprobarse frente a cada requisito; no se presupone que el código local ya satisfaga el contrato íntegro. Los cambios existentes del usuario se preservan.

## Contrato normativo incluido en la propuesta

La solución deberá cumplir, como mínimo, todos estos MUSTs proporcionados por el usuario:

### Identificadores, códigos y validación de URL

- Identificador interno UUIDv7; unicidad de `code` dentro de `domain`.
- Solo URLs HTTP/HTTPS. Para schemes no admitidos, devolver 422 y preservar la URL recibida.
- Generar códigos aleatorios Base62 de 10 caracteres con aleatoriedad segura; ante colisión/violación de unicidad, reintentar inserción como máximo 5 veces.
- Aceptar alias de 4–32 caracteres que cumplan `[A-Za-z0-9_-]`, preservando distinción de mayúsculas/minúsculas. Reservar `api`, `admin`, `login`, `logout`, `signup`, `register`, `health`, `metrics`, `docs`, `robots.txt` y `favicon.ico`.

### API y semántica del recurso

- API de gestión `/api/v1`; idempotencia durante 24 h y 409 si se reutiliza una clave con una petición distinta.
- `If-Match` para concurrencia; devolver 412 en conflicto.
- Redirect público 302 con `no-store`.
- Expirado: 410; disabled/deleted: 404; quarantined: 403.
- Errores RFC 9457.
- Rate limits configurables con valores iniciales de 100 creaciones/min/usuario y 600 operaciones de gestión/min/usuario, incluyendo `Retry-After` al limitar.

### Datos, consistencia, caché y disponibilidad

- PostgreSQL como fuente de verdad. Las lecturas de réplicas deben recurrir a la primaria para confirmar negativos. No introducir sharding prematuro.
- Request coalescing para solicitudes concurrentes equivalentes.
- Caché L1 de 30 s; Redis de 5 min; caché negativa de 15 s solo tras confirmar inexistencia en la primaria. TTL de caché igual al mínimo entre TTL configurado y tiempo restante hasta expiración.
- Objetivo de invalidación de cambios de 5 s; cuarentena con invalidación inmediata.
- Redis indisponible: fallback a DB. DB indisponible: conservar redirects ya presentes en caché. Broker o analítica no pueden bloquear redirects.

### Outbox, clicks y analítica

- Publicar cambios de estado mediante transactional outbox.
- Registrar clicks de forma asíncrona como `ClickRecorded`, con entrega at-least-once y deduplicación por `eventId`.
- Objetivo de retraso de analítica de 60 s; no contar clicks sincrónicamente en el camino de redirect.

### Dominios, abuso y SSRF

- Dominios custom verificados mediante DNS TXT y TLS.
- Controles de abuso y transiciones a cuarentena, con invalidación inmediata.
- Protecciones SSRF solamente en componentes que hacen fetch de URLs. El redirect nunca recupera la URL de destino.

### Operación, privacidad y capacidad

- Métricas observables con cardinalidad acotada.
- SLO de disponibilidad de redirect 99.99%; latencias p50/p95/p99 objetivo 20/50/150 ms y p95 de gestión 300 ms.
- Diseño para 100 M enlaces activos, 50k redirects/s sostenidos, burst de 250k redirects/s y 1k creaciones/s. Se requiere evidencia de pruebas de carga antes de afirmar que estos objetivos se alcanzan.
- Descartar IP raw según el requisito de privacidad.
- Generar QR que apunte a la URL corta.

La lista de aceptación del usuario es fuente normativa y se trasladará verbatim a la especificación en su fase, no se reemplazará por esta paráfrasis. Esta propuesta no certifica cumplimiento de la implementación local ni declara resultados de carga.

## Alcance propuesto

- Especificar y cubrir por escenarios todo el contrato normativo anterior, incluida literalmente la lista de aceptación proporcionada por el usuario.
- Validar Core V1 comprometido en HEAD contra el contrato, separando lo ya implementado, lo que requiera ajuste y las capacidades aún pendientes; la presencia del commit está verificada, pero no implica conformidad funcional ni despliegue.
- Diseñar y planificar la evolución completa: semántica API/ciclo de vida; garantías de persistencia, caché e invalidación; outbox/eventos/analítica; dominios custom y TLS; abuso, cuarentena y límites de tasa; QR, privacidad y observabilidad; resiliencia y evidencia de capacidad.
- Proteger el camino crítico de redirect: 302/no-store, respuestas por estado, sin fetch del destino y sin dependencia sincrónica de analítica/broker.
- Preservar el límite del contexto de shortlinks y las convenciones arquitectónicas existentes; resolver dependencias entre slices mediante diseño, no reduciendo requisitos.

Fuera de esta propuesta quedan cualquier cambio de código, modificación de documentos RPI, verificación de merge remoto o despliegue, y el inicio de las fases de spec, design o tasks. Esta actualización se limita a los artefactos existentes `exploration.md` y `proposal.md`.

## Decisiones reservadas para diseño

La fuente normativa no fija estas elecciones de implementación; deben decidirse en diseño sin debilitar los MUSTs:

1. Propietario operativo del transactional outbox y su estrategia concreta de ejecución y recuperación.
2. Proveedor/mecanismo de verificación DNS TXT y provisión/renovación TLS.
3. Superficie API para consultas de analytics.
4. Formato del QR.
5. Esquema de datos y retención de privacidad compatible con descartar IP raw.
6. Policy de quota/billing más allá de los límites de tasa iniciales configurables.

El resto de requisitos enumerados aquí se considera fijado por la especificación del usuario, no una decisión pendiente.

## Riesgos

- Diferencia entre HEAD (`465e767c`), cambios locales sin commit y la afirmación del usuario de que el PR se mergeó; no hay verificación remota en esta actualización.
- La existencia de Core V1 local no demuestra cobertura normativa completa, y no se ejecutaron pruebas.
- La semántica de confirmación de negativos en la primaria, TTLs, request coalescing e invalidación (5 s e inmediata para cuarentena) necesita pruebas de consistencia y fallos.
- Entrega at-least-once exige consumidor idempotente por `eventId`; un broker caído no debe afectar el redirect.
- Los SLOs y objetivos de capacidad no deben anunciarse como conseguidos sin mediciones representativas, especialmente pruebas de carga para los niveles declarados.
- Privacidad, proveedor DNS/TLS, consulta de analítica, QR y cuotas requieren decisiones explícitas en diseño, pero no son motivo para aplazar la especificación de requisitos ya provistos.

## Siguiente fase recomendada

Conservar la secuencia SDD: escribir la especificación normativa íntegra y sus escenarios de aceptación literales; después preparar diseño y tareas. No iniciar esas fases como parte de esta actualización. Antes de declarar la implementación integrada, verificar por separado el merge remoto según autorización y el estado de los checks pertinentes.
