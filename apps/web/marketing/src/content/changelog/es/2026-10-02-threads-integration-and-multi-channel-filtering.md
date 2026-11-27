---
slug: 2026-10-02-threads-integration-and-multi-channel-filtering
locale: es
date: "2026-10-02"
version: "v0.3.14"
title: "Integración con Threads y filtrado multicanal en el calendario"
summary: "Conecta perfiles personales de Threads junto a LinkedIn y filtra tus vistas de semana, día, mes y lista por canal."
categories:
  - feature
image: /changelog/threads.svg
imageAlt: "Vista previa de la barra lateral de Profile Tailors con los canales de LinkedIn y Threads y sus contadores en cola junto al calendario semanal filtrado."
---

## Conexión de perfil personal en Threads

Threads se suma a LinkedIn como la segunda plataforma integrada en acceso anticipado. Puedes iniciar la conexión OAuth de perfil personal desde Ajustes o directamente desde la sección de Canales en la barra lateral.

- Los callbacks OAuth de proveedores permanecen aislados de los flujos de autenticación humana.
- La publicación sigue restringida mientras las integraciones completan su validación de acceso anticipado.

## Filtrado por canal y contadores en cola

La barra lateral muestra ahora el número de publicaciones en cola por proveedor conectado y permite alternar entre todos los canales o una cuenta concreta sin perder tu vista actual del calendario.

- Filtra las vistas de semana, día, mes y lista por uno o varios canales.
- Los parámetros de la URL conservan el filtro activo al recargar o compartir un enlace del espacio de trabajo.
