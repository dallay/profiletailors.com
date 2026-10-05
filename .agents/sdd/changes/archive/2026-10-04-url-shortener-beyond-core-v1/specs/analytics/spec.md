# Especificación: analytics

## Propósito

Especifica registro asíncrono y procesamiento fiable de clics sin añadir trabajo bloqueante al redirect.

## Requisitos

### Requirement: Registrar clics fuera del camino crítico

La solicitud de redirect MUST ejecutar únicamente `tryOffer()` no bloqueante hacia una cola local acotada para `ClickRecorded`; un publicador en background MUST enviar lotes al broker. La admisión a la cola es best effort; desde la aceptación del broker la entrega downstream MUST ser at-least-once y deduplicarse por `eventId`. La analítica MUST NOT hacer fallar ni retrasar el 302. Si la cola está llena, el clic MAY perderse en near-real-time, MUST generar una métrica observable y MUST poder reconciliarse después desde logs durables del edge. El objetivo de reflejo en dashboard en operación normal es ≤60 s.

#### Scenario: Click duplicado
- GIVEN dos entregas downstream de `ClickRecorded` con el mismo `eventId`
- WHEN se procesan
- THEN el click se contabiliza una sola vez

#### Scenario: Cola local llena
- GIVEN que la cola local acotada no admite otro evento
- WHEN el redirect llama `tryOffer()`
- THEN no espera ni falla el 302
- AND la pérdida near-real-time se observa mediante una métrica
- AND los logs durables del edge permiten reconciliación posterior

#### Scenario: Broker no disponible
- GIVEN que el publicador no puede entregar al broker
- WHEN un usuario sigue un enlace activo
- THEN el redirect responde sin esperar el broker
- AND los eventos aceptados por el broker mantienen entrega at-least-once

#### Scenario: Latencia de dashboard
- GIVEN clicks aceptados durante operación normal
- WHEN se consulta el dashboard
- THEN el objetivo de disponibilidad analítica es reflejarlos en ≤60 s

#### Scenario: Minimización de datos del clic
- GIVEN que un click incluye IP, referrer y user-agent
- WHEN se procesa y almacena analítica
- THEN no se almacena IP raw
- AND el referrer se reduce a dominio y el user-agent a categoría
- AND el visitante se representa con pseudónimo HMAC-SHA256 cuya clave rota cada 30 días

#### Scenario: Retención analítica
- GIVEN datos analíticos retenidos
- WHEN vencen sus plazos
- THEN eventos raw se eliminan a los 30 días, agregados por minuto a los 7 días, por hora a los 90 días y por día a los 25 meses
- AND logs durables de reconciliación edge se retienen 30 días

### Requirement: Consultar analítica por enlace

La API MUST ofrecer `/api/v1/links/{linkId}/summary`, `/api/v1/links/{linkId}/timeseries` y `/api/v1/links/{linkId}/breakdown`, autenticadas y con scope de workspace/tenant, manteniendo `application/vnd.api.v1+json` y errores RFC 9457. Las consultas MUST usar intervalo semiabierto `[from,to)`, timestamps RFC3339 y zona horaria IANA. La granularidad MUST ser minuto para rangos de hasta 24 h, hora hasta 90 días y día hasta 25 meses. Los breakdowns MUST tener límites acotados; las respuestas MUST incluir `dataThrough` y `reconciledThrough`. `uniqueVisitors` MUST indicarse y tratarse como no aditivo entre buckets.

#### Scenario: Rango y granularidad válidos
- GIVEN un rango RFC3339 con zona IANA dentro del límite de retención
- WHEN se consulta timeseries con granularidad permitida
- THEN se devuelven datos para `[from,to)` con `dataThrough` y `reconciledThrough`

#### Scenario: Granularidad o breakdown fuera de límites
- GIVEN granularidad incompatible con la duración o breakdown sin límite permitido
- WHEN se consulta la API
- THEN la solicitud se rechaza con el contrato de error de la API

#### Scenario: Visitantes únicos no aditivos
- GIVEN dos buckets consecutivos con `uniqueVisitors`
- WHEN se presenta un resumen del rango
- THEN no se suman los valores de visitantes únicos como si fueran aditivos

### Requirement: Producir QR de la URL corta

El sistema MUST generar QR localmente para `shortURL` solamente, como UTF-8 QR Code Model 2, con corrección de errores M, quiet zone de 4 módulos y SVG por defecto de 512 px; PNG MUST estar disponible. ZXing core y javase 3.5.4 es la versión de referencia elegida por el usuario, no una afirmación de que sea la versión más reciente.

#### Scenario: QR SVG
- GIVEN un enlace corto existente
- WHEN se solicita su QR sin especificar formato
- THEN se devuelve un SVG de 512 px que al decodificarse contiene solo la URL corta

#### Scenario: QR PNG
- GIVEN un enlace corto existente
- WHEN se solicita el QR en PNG
- THEN se devuelve una imagen PNG decodificable con el mismo contenido shortURL

### Requirement: Producir QR de la URL corta

El sistema MUST ofrecer un QR cuyo contenido sea la URL corta, sin codificar directamente el destino largo.

#### Scenario: QR válido
- GIVEN un enlace corto existente
- WHEN se solicita su QR
- THEN al decodificarlo se obtiene la URL corta del enlace
