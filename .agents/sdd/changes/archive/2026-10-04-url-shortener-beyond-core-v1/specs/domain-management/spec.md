# Especificación: domain-management

## Propósito

Define dominios personalizados y condiciones para servir shortlinks bajo esos dominios.

## Requisitos

### Requirement: Aprovisionar y activar dominios custom

El sistema MUST usar Cloudflare for SaaS/Custom Hostnames para aprovisionamiento, validación y estado del hostname. El cliente MUST conservar su DNS authoritative y dirigirlo al SaaS target indicado. La plataforma MUST orquestar operaciones de API provisioning/validation/state; Cloudflare MUST emitir y renovar TLS. Un hostname MUST permanecer no habilitado para redirects hasta que `hostname.status` sea `active`, `ssl.status` sea `active` y el DNS apunte al SaaS target.

#### Scenario: Hostname y TLS activos con DNS correcto
- GIVEN el hostname está active, ssl.status está active y DNS apunta al SaaS target
- WHEN se habilita el dominio
- THEN puede resolver los shortlinks asociados

#### Scenario: Aprovisionamiento pendiente o incompleto
- GIVEN hostname status o ssl.status no están active, o DNS no apunta al SaaS target
- WHEN se intenta habilitar o resolver el dominio
- THEN el dominio no se habilita para redirects

#### Scenario: Responsabilidades DNS y TLS
- GIVEN un dominio custom que requiere validación y certificado
- WHEN se aprovisiona
- THEN el cliente mantiene su DNS authoritative y lo configura hacia el SaaS target
- AND Cloudflare emite y renueva el certificado TLS
