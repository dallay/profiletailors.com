# Waitlist withdrawal env wiring — Plan

**Ruta:** Direct inline
**Goal:** Arrancar SMP en perfil `dev` y prod sin `IllegalArgumentException` por `app.waitlist.withdrawal.encryption-key` vacío, cableando la key y la URL pública vía variables de entorno con el patrón del repo.
**Architecture:** Sin cambio de código Kotlin. Solo wiring de `@ConfigurationProperties(prefix = "app.waitlist.withdrawal")` en `application.yaml` (requerido, sin default secreto), fallback dev en `application-dev.yaml` (clave test ya usada en `application.properties`), plantilla en `.env.example`, y referencia en `docs/production-secrets.md`. Fail-fast del provider se mantiene.
**Tech Stack:** Spring Boot 4 `application.yaml`, perfil `dev`, `.env` / `.env.example`, `openssl rand -base64 32`.

---

## Tareas

### Tarea 1: `application.yaml` — wiring requerido

**Archivos:**

- Modificar: `server/smp/src/main/resources/application.yaml:90-101`

- [ ] Paso 1: Añadir bloque bajo `app:` junto a `email`:

```yaml
  waitlist:
    withdrawal:
      encryption-key: ${SMP_WAITLIST_WITHDRAWAL_ENCRYPTION_KEY:}
      public-url-base: ${SMP_WAITLIST_WITHDRAWAL_PUBLIC_URL_BASE:https://profiletailors.com/waitlist/withdraw}
```

- [ ] Paso 2: Verificar indentación a 2 espacios y que `app.waitlist.withdrawal.encryption-key` resuelve a env var vacía por defecto (fail-fast intencional en prod si no se configura).
- [ ] Paso 3: Checkpoint de revisión (sin commit, no autorizado).

### Tarea 2: `application-dev.yaml` — fallback dev

**Archivos:**

- Modificar: `server/smp/src/main/resources/application-dev.yaml:52-57`

- [ ] Paso 1: Añadir bajo `app:`:

```yaml
  waitlist:
    withdrawal:
      encryption-key: ${SMP_WAITLIST_WITHDRAWAL_ENCRYPTION_KEY:V1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1dXV1c=}
      public-url-base: ${SMP_WAITLIST_WITHDRAWAL_PUBLIC_URL_BASE:https://profiletailors.com/waitlist/withdraw}
```

Valor fallback es la misma clave test de 32 bytes ya usada en `src/test/resources/application.properties:6`, solo para dev local. Prod la sobrescribe vía env var.

- [ ] Paso 2: Checkpoint de revisión.

### Tarea 3: `.env.example` + `.env` real sin leaks

**Archivos:**

- Modificar: `.env.example` (bloque tras waitlist rate limiting, líneas ~113-117)
- Modificar: `.env` solo con placeholders vacíos, sin valores secretos

- [ ] Paso 1: Añadir en `.env.example`:

```
SMP_WAITLIST_WITHDRAWAL_ENCRYPTION_KEY=
SMP_WAITLIST_WITHDRAWAL_PUBLIC_URL_BASE=https://profiletailors.com/waitlist/withdraw
```

- [ ] Paso 2: En `.env` real, añadir solo las claves vacías si faltan (nunca escribir secretos generados al repo ni al log). Decir al usuario que genere con:

```bash
openssl rand -base64 32
```

- [ ] Paso 3: Verificar con `grep -n WAITLIST_WITHDRAWAL .env .env.example` que existen y que `.env` sigue gitignored.

### Tarea 4: Docs `production-secrets.md`

**Archivos:**

- Modificar: `docs/production-secrets.md` (sección Credential Encryption / Media Preview Signing)

- [ ] Paso 1: Añadir entrada `SMP_WAITLIST_WITHDRAWAL_ENCRYPTION_KEY` tipo Base64 32 bytes AES, generación `openssl rand -base64 32`, riesgo CRITICAL, rotación requiere re-cifrado o invalidación de URLs de withdrawal pendientes.
- [ ] Paso 2: Añadir `SMP_WAITLIST_WITHDRAWAL_PUBLIC_URL_BASE` como setting no secreto con ejemplo `https://profiletailors.com/waitlist/withdraw`.
- [ ] Paso 3: Actualizar checklist con la nueva key.

### Tarea 5: Verificación

- [ ] Paso 1: `just backend-test-fast` o `just backend-check` según tiempo (mínimo: tests del módulo leadcapture + Detekt).
- [ ] Paso 2: Arranque dev: `just backend-run` o `./gradlew bootRun --args='--spring.profiles.active=dev'` y confirmar que desaparece `Waitlist withdrawal encryption key must be configured`.
- [ ] Paso 3: Inspeccionar diff final: sin supresiones, sin baselines, sin secretos, sin cambios no relacionados.

---

## Evidencia

- Causa: `EncryptedWaitlistWithdrawalUrlProvider.kt:87` exige key no vacía; `WaitlistWithdrawalProperties.kt:7` default `""`; `application.yaml` y `application-dev.yaml` no mapean `app.waitlist.withdrawal`; solo `src/test/resources/application.properties:6-7` lo define.
- Introducido en `e53579a9` sin wiring main.
- Patrón de referencia: `publishing.credentials.encryption.key: ${PUBLISHING_CREDENTIALS_ENCRYPTION_KEY:}` en `application.yaml:165` + fallback dev en `application-dev.yaml:46`.

## Estado

- `Ready` — wiring aplicado y verificado localmente. Sin commit (no autorizado).
