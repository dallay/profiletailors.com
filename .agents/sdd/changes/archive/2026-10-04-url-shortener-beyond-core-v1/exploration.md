# Exploración: URL shortener más allá de Core V1

## Fuente normativa y alcance de la evidencia

La especificación normativa íntegra fue proporcionada por el usuario en la conversación, incluida su lista de aceptación literal. Este artefacto resume sus requisitos vinculantes para orientar las fases posteriores; no la sustituye ni afirma que se haya creado ya `spec.md`. No se debe solicitar recuperar la especificación ni reinterpretar como abiertas decisiones que ya quedaron fijadas por ella.

## Estado observado del checkout

La rama de este worktree es `plasma_titan_dips_11h27`, con `HEAD` `3178a883c2936d04b083a9d184e81e6a921504c5`, idéntico a `origin/main` en la comprobación realizada. Ese commit es `feat(shortlinks): add Core V1 backend with idempotent management and safe redirect (#1305)`, por lo que Core V1 está comprometido en HEAD y la evidencia de fast-forward queda comprobada. Esto verifica el estado del checkout y la referencia local `origin/main`; no demuestra despliegue ni sustituye la verificación de comportamiento mediante pruebas.

Se conservan intactos los cambios preexistentes. `.agents/rpi/plan/tasks/url-shortener-v1.md` permanece fuera de alcance y no se usa como fuente normativa. No se modificó código ni documentación RPI, y no se inició spec, diseño ni tareas.

## Core V1 observado localmente

El código local organiza shortlinks como contexto Kotlin hexagonal (`domain`, `application`, `infrastructure`). Contiene creación y gestión de enlaces, validación de URL y alias, idempotencia, estados/expiración, resolución con caché, persistencia R2DBC, migraciones Liquibase y pruebas unitarias, integración PostgreSQL y BDD. `QUARANTINED` existe como estado local, pero no prueba por sí solo la implementación del flujo normativo de abuso.

La inspección previa de Core V1 no encontró el flujo completo de dominios custom verificados, analítica de clicks/outbox, QR, protección antiabuso u observabilidad específica. Esta observación no modifica ni limita el alcance normativo proporcionado por el usuario. La presencia del Core en HEAD tampoco equivale a conformidad demostrada con todos los MUSTs.

## Requisitos normativos que deben conservarse

### Identidad, códigos y destinos

- Identificador interno UUIDv7 y unicidad de `code` dentro de `domain`.
- Aceptar exclusivamente destinos HTTP/HTTPS. Un scheme no permitido produce 422 y se preserva la URL recibida en el error.
- Código aleatorio Base62 de 10 caracteres generado con aleatoriedad segura; reintentar la inserción ante colisión/violación de unicidad como máximo cinco veces.
- Alias opcional de 4–32 caracteres con patrón `[A-Za-z0-9_-]`, sensible a mayúsculas/minúsculas. Rechazar los alias reservados `api`, `admin`, `login`, `logout`, `signup`, `register`, `health`, `metrics`, `docs`, `robots.txt` y `favicon.ico`.

### API, respuestas y ciclo de vida

- API de gestión bajo `/api/v1`; claves de idempotencia con vigencia de 24 horas y 409 cuando se reutiliza la clave con una petición distinta.
- Soportar `If-Match`; los conflictos de concurrencia devuelven 412.
- El redirect público responde 302 y `no-store`. Un enlace expirado devuelve 410; deshabilitado o eliminado, 404; en cuarentena, 403.
- Errores con RFC 9457. Los rate limits son configurables; valores iniciales: creación 100/min/usuario y gestión 600/min/usuario. Al limitar, incluir `Retry-After`.

### Persistencia, cachés y resiliencia

- PostgreSQL es la fuente de verdad. Las réplicas se pueden usar para lecturas, pero un resultado negativo debe confirmarse en la primaria antes de tratarlo como inexistente. No introducir sharding prematuro.
- Coalescer requests concurrentes equivalentes. Caché L1 de 30 s y Redis de 5 min. Caché negativa de 15 s solo tras confirmación de la primaria. El TTL efectivo de caché es el mínimo entre la duración de caché y el tiempo restante hasta la expiración.
- Objetivo de invalidación tras cambios: 5 s. Si Redis falla, resolver desde DB. En caída de DB, conservar redirects ya presentes en caché. Fallos de analítica o del broker no deben bloquear redirects.

### Eventos, analítica y privacidad

- Los cambios de estado producen eventos mediante transactional outbox.
- `ClickRecorded` se procesa asíncronamente con entrega al menos una vez y deduplicación mediante `eventId`; objetivo de retraso de analítica: 60 s. No calcular el conteo de clicks sincrónicamente en el camino de redirect.
- Eliminar/descartar IP sin procesar conforme al requisito de privacidad. El esquema de retención y tratamiento de datos que no fija la fuente queda para diseño, sin relajar la prohibición de retener IP raw.

### Dominios, abuso y seguridad

- Dominios custom solo se habilitan tras verificación DNS TXT y TLS.
- Aplicar controles antiabuso, transiciones de cuarentena e invalidación inmediata de cachés ante cuarentena.
- Las protecciones SSRF corresponden únicamente a componentes que recuperan URLs. El redirect nunca debe hacer fetch del destino.

### Operación, objetivos y capacidades

- Métricas operativas con cardinalidad acotada.
- SLO de disponibilidad de redirect 99.99%; latencias objetivo p50/p95/p99 de 20/50/150 ms; gestión p95 de 300 ms.
- Objetivos de diseño de capacidad: 100 M enlaces activos, 50k redirects/s sostenidos y 250k burst, 1k creaciones/s. No declarar que se alcanzan sin evidencia de pruebas de carga.
- Proporcionar QR que apunte a la URL corta; el formato queda por decidir en diseño.

La lista de aceptación del usuario se considera normativa y debe trasladarse literalmente cuando se escriba el artefacto de especificación; no se reconstruye ni se reemplaza con esta síntesis.

## Áreas de implementación y enfoques

La implementación debe conservar el contexto de shortlinks y sus límites hexagonales salvo que el diseño, respaldado por evidencia, justifique otro límite. El redirect constituye un camino crítico: resolver con almacenamiento/caché y evitar trabajo sincrónico de analítica o fetching del destino. Persistir eventos de estado en outbox transaccional; desacoplar consumo analítico y broker del redirect. Aplicar invalidación coordinada dentro del objetivo de 5 s, con invalidación inmediata para cuarentena.

Para el contrato integral, se recomienda una especificación normativa común y trazable a la fuente del usuario, con escenarios de aceptación también literales. Para la implementación, planificar slices dependientes (redirect/resiliencia e invalidación; gestión e idempotencia/concurrencia; eventos y analítica; dominios verificados; abuso/rate limits; QR/observabilidad y carga) sin reducir el alcance. No se decide aquí orden final ni distribución de cambios.

## Decisiones genuinamente abiertas para diseño

Estas decisiones no están fijadas por los MUSTs resumidos y no deben emplearse para reabrir requisitos normativos:

1. Propietario operativo y estrategia de ejecución/reintentos del outbox.
2. Proveedor o mecanismo concreto para DNS TXT y emisión/renovación de TLS.
3. Superficie API para consultar analytics.
4. Formato y representación del QR.
5. Esquema de datos/retención de privacidad para IP una vez descartada la IP sin procesar.
6. Política de cuota/billing más allá de los rate limits iniciales configurables indicados.

Las elecciones de infraestructura y cualquier decisión arquitectónica durable se justifican en diseño y, cuando corresponda, en el ADR adecuado. No se cambian en esta exploración.

## Riesgos y siguientes pasos

- El estado de merge no se puede inferir de este checkout: `HEAD` es `465e767c`, la feature está en cambios locales sin commit y el merge existe solo como afirmación del usuario hasta verificar el remoto en una fase autorizada.
- No se ejecutaron pruebas ni se verificó conformidad de la implementación local con la especificación completa.
- Los objetivos de carga y latencia exigen evidencia de pruebas representativas antes de comunicarlos como resultados alcanzados.
- La consistencia de caché, lectura desde réplicas, cuarentena e invalidación requiere pruebas que demuestren los objetivos de 5 s/inmediata definidos.
- La entrega al menos una vez requiere idempotencia real por `eventId`; no confundirla con exactamente una vez.
- Próxima recomendación: reconciliar primero la fuente normativa del usuario y la lista de aceptación literal en el artefacto de spec; después continuar diseño y tareas únicamente por instrucción/orquestación posterior. Esta actualización no inicia ninguna de esas fases.
