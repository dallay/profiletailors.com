---
slug: 2026-09-15-calendar-workspace-and-security-foundation
locale: es
date: "2026-09-15"
version: "v0.3.6"
title: "Espacio de calendario y seguridad de sesión BFF"
summary: "Planifica contenido de LinkedIn en vistas de semana, día, mes y lista con autenticación BFF respaldada por Keycloak y cookies HttpOnly."
categories:
  - feature
  - security
image: /changelog/calendar-security.svg
imageAlt: "Resumen del flujo de publicación de Profile Tailors junto a los indicadores de seguridad de sesión BFF HttpOnly."
---

## Espacio de programación centrado en el calendario

El espacio principal de acceso anticipado reúne borradores y publicaciones programadas en un calendario fácil de leer con vistas de semana, día, mes y lista.

- Crea, edita, elimina y reordena publicaciones planificadas desde una única interfaz enfocada.
- Diseñado con objetivos de contraste WCAG 2.2 Nivel AA tanto en modo oscuro como en modo claro.

## Autenticación BFF respaldada por Keycloak

Las sesiones humanas en navegador se autentican ahora mediante un flujo Backend-for-Frontend dedicado para que los tokens de proveedor nunca entren en el JavaScript del navegador.

- El estado autenticado utiliza cookies de sesión HttpOnly con protección CSRF en peticiones no seguras.
- Los flujos de retirada de contenido y los registros de auditoría operan sobre identificadores UUID canónicos.
