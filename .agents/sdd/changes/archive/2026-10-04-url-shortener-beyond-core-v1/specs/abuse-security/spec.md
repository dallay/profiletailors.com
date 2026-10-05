# Especificación: abuse-security

## Propósito

Define mitigación de abuso, cuarentena y fronteras de seguridad de fetching.

## Requisitos

### Requirement: Detectar abuso y hacer efectiva la cuarentena

El sistema MUST aplicar comprobaciones locales de abuso y permitir poner enlaces en QUARANTINED. La transición MUST invalidar inmediatamente las cachés y los redirects en cuarentena MUST responder 403.

#### Scenario: Cuarentena revoca redirect
- GIVEN un enlace ACTIVE cacheado que las comprobaciones clasifican como abusivo
- WHEN se pone en cuarentena
- THEN se invalida inmediatamente la caché
- AND las solicitudes posteriores responden 403

### Requirement: Limitar solicitudes de gestión

Las políticas de rate limit MUST ser configurables, con valores iniciales de 100 creaciones/minuto/usuario y 600 operaciones de gestión/minuto/usuario. Una respuesta limitada MUST incluir `Retry-After`.

#### Scenario: Límite excedido
- GIVEN un usuario que excede el límite vigente
- WHEN realiza otra operación
- THEN se rechaza según la política y se incluye `Retry-After`

### Requirement: Bloquear SSRF en fetchers

Todo componente que efectivamente haga fetch de URLs MUST bloquear IPs no públicas y protegerse de DNS rebinding. El redirect MUST NOT hacer fetch del destino.

#### Scenario: Resolución hostil por fetcher
- GIVEN una URL cuya resolución apunta a una IP no pública o cambia hacia ella
- WHEN un fetcher procesa la URL
- THEN bloquea la conexión antes de acceder al destino

### Requirement: Proteger privacidad en redirects y eliminar analítica

El flujo de redirect MUST NOT usar cookies, localStorage, JavaScript de tracking ni fingerprinting. La analítica MUST NOT almacenar IP raw. Al eliminar un enlace, el acceso a su analítica MUST revocarse inmediatamente; sus eventos raw MUST eliminarse en ≤24 h, agregados en ≤7 días y tombstones de backups en ≤30 días.

#### Scenario: Redirect sin tracking del cliente
- GIVEN un usuario sigue un shortlink
- WHEN el redirect se ejecuta
- THEN no se usan cookies, localStorage, JavaScript de tracking ni fingerprinting

#### Scenario: Eliminación de enlace
- GIVEN un enlace con analítica y backups que contienen sus datos
- WHEN el enlace se elimina
- THEN su analítica deja de ser accesible inmediatamente
- AND sus eventos raw se eliminan en ≤24 h
- AND sus agregados se eliminan en ≤7 días
- AND sus tombstones de backups se eliminan en ≤30 días
