# Especificación: management-api

## Propósito

Contrato HTTP de gestión y creación de shortlinks, complementario al baseline Core V1.

## Requisitos

### Requirement: Crear destinos y códigos válidos

La API MUST estar bajo `/api/v1`, aceptar únicamente destinos HTTP/HTTPS y devolver 422 para otros esquemas preservando el URL recibido en el error. IDs internos MUST ser UUIDv7 y `(domain_id, short_code)` único. El código generado MUST ser criptográficamente aleatorio Base62 de 10 caracteres, con hasta 5 intentos de inserción. Alias MUST cumplir `^[A-Za-z0-9_-]{4,32}$`, ser case-sensitive y rechazar `api`, `admin`, `login`, `logout`, `signup`, `register`, `health`, `metrics`, `docs`, `robots.txt`, `favicon.ico`.

#### Scenario: Destino y alias
- GIVEN una solicitud de creación con destino HTTP/HTTPS y alias válido no reservado
- WHEN se crea el enlace
- THEN el sistema persiste un ID UUIDv7 y código único por dominio
- AND conserva la distinción de mayúsculas del alias

#### Scenario: Esquema rechazado
- GIVEN un destino con esquema distinto de HTTP/HTTPS
- WHEN se crea
- THEN responde 422 y el error preserva exactamente el URL recibido

#### Scenario: Colisión de código
- GIVEN que colisionan sucesivas inserciones de códigos generados
- WHEN se intenta crear
- THEN se realizan como máximo cinco inserciones
- AND el fallo final no crea un enlace duplicado

#### Scenario: Alias inválido o reservado
- GIVEN alias fuera del patrón, longitud permitida o lista reservada
- WHEN se crea
- THEN se rechaza sin crear el enlace

### Requirement: Gestionar con idempotencia y concurrencia

Las claves de idempotencia MUST tener vigencia de 24 h: replay idéntico devuelve el resultado original y payload distinto con la misma clave devuelve 409. Las mutaciones condicionales MUST admitir `If-Match` y devolver 412 ante conflicto. Los errores MUST seguir RFC 9457.

#### Scenario: Replay y reutilización conflictiva
- GIVEN una petición ya procesada con una clave vigente
- WHEN se repite igual y luego con payload diferente
- THEN el replay idéntico devuelve el resultado original
- AND la petición diferente responde 409

#### Scenario: Versión obsoleta
- GIVEN un `If-Match` que no coincide con la versión actual
- WHEN se muta el recurso
- THEN responde 412 sin aplicar la mutación

#### Scenario: Error de API
- GIVEN una petición de gestión inválida
- WHEN la API informa el error
- THEN usa el formato RFC 9457
