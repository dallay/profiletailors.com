---
slug: 2026-10-09-workspace-shortlinks-and-click-analytics
locale: es
date: "2026-10-09"
version: "v0.3.18"
title: "Enlaces cortos y analítica de clics por espacio de trabajo"
summary: "Las nuevas publicaciones acortan automáticamente las URLs distintas al enviarse, con avisos no bloqueantes y analítica paginada de clics."
categories:
  - feature
  - improvement
image: /changelog/shortlinks.svg
imageAlt: "Vista previa de enlaces cortos en Profile Tailors mostrando una URL original convertida en enlace corto junto a contadores de clics totales y enlaces activos."
---

## Acortado automático de URLs en nuevas publicaciones

Al crear y enviar una nueva publicación desde el editor, Profile Tailors detecta ahora las URLs distintas en tu texto y las sustituye automáticamente por enlaces cortos del espacio de trabajo.

- El acortado funciona en modo best-effort: si un enlace falla al acortarse, la URL original permanece intacta y el editor muestra un aviso no bloqueante.
- Las publicaciones existentes nunca se reescriben al editarlas, manteniendo intacto el texto publicado y las redirecciones previas.

## Analítica paginada de clics del espacio de trabajo

Cada enlace acortado registra los conteos de redirección dentro de tu espacio de trabajo. Puedes consultar el rendimiento desde la vista autenticada de enlaces cortos con paginación y totales claros por enlace.

- Consulta clics totales y enlaces activos sin salir del panel.
- Todos los conteos permanecen aislados en tu espacio de trabajo y alojados en infraestructura de la UE en Fráncfort.
