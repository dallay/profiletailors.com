# Especificación: redirect-data-plane

## Propósito

Define resolución pública de shortlinks, persistencia autoritativa, cachés y resiliencia. Core V1 permanece como baseline para capacidades existentes; esta especificación establece las garantías normativas que debe cumplir el resultado, sin afirmar cumplimiento actual.

## Requisitos

### Requirement: Resolver enlaces conforme a su estado

El redirect MUST devolver 302 con `Cache-Control: no-store` para enlaces ACTIVE, 410 para EXPIRED, 404 para DISABLED o DELETED y 403 para QUARANTINED. MUST NOT obtener la URL de destino mediante fetch.

#### Scenario: Enlace activo redirige sin almacenar
- GIVEN un enlace ACTIVE
- WHEN se solicita su URL corta
- THEN responde 302 con la URL destino y `no-store`
- AND el servicio no contacta el destino

#### Scenario: Estado no activo
- GIVEN un enlace EXPIRED, DISABLED, DELETED o QUARANTINED
- WHEN se resuelve
- THEN responde respectivamente 410, 404, 404 o 403

### Requirement: Mantener consistencia de caché y disponibilidad

PostgreSQL MUST ser la fuente de verdad. L1 MUST tener TTL máximo 30 s y Redis 5 min; el TTL efectivo MUST ser el menor entre TTL configurado y tiempo restante hasta `expiresAt`. Un negativo de réplica MUST confirmarse en primaria antes de cachearse 15 s. La invalidación tras mutación MUST completarse en ≤5 s y la cuarentena inmediatamente. Redis fallido MUST permitir consultar DB; DB caída MAY permitir servir un resultado ya cacheado. Solicitudes equivalentes concurrentes SHOULD coalescerse. No se exige sharding.

#### Scenario: Expiración limita la caché
- GIVEN un enlace ACTIVE con expiración anterior al TTL configurado
- WHEN se cachea
- THEN su entrada no permanece activa después de `expiresAt`

#### Scenario: Negativo y caída de dependencias
- GIVEN una réplica no encuentra un código
- WHEN se decide que no existe
- THEN se confirma la ausencia en PostgreSQL primaria antes de cachear el negativo
- AND Redis caído usa DB; DB caída puede servir solo entradas ya cacheadas

#### Scenario: Mutación invalida lectura obsoleta
- GIVEN un enlace cacheado que cambia de estado
- WHEN la mutación se confirma
- THEN su caché se invalida en ≤5 s, o inmediatamente si pasa a QUARANTINED

#### Scenario: Analítica no bloquea redirect
- GIVEN broker o analítica indisponibles, o la cola local está llena
- WHEN se resuelve un enlace activo
- THEN la solicitud ejecuta únicamente `tryOffer()` no bloqueante para analítica
- AND responde 302 sin esperar ni fallar por esas dependencias
- AND cualquier pérdida near-real-time por cola llena se mide y puede reconciliarse desde logs durables del edge
