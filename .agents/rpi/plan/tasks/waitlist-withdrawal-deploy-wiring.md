# Waitlist withdrawal deploy wiring — Plan

**Ruta:** Direct inline
**Goal:** Alimentar `SMP_WAITLIST_WITHDRAWAL_ENCRYPTION_KEY` (Swarm secret) y `SMP_WAITLIST_WITHDRAWAL_PUBLIC_URL_BASE` (.env) en `profiletailors-deploy` para que el backend prod resuelva `app.waitlist.withdrawal.*` sin reventar al arrancar.
**Architecture:** Sin cambio de imagen ni topología. Solo wiring siguiendo el patrón existente: secreto versionado `_v1` montado vía `configtree:/run/secrets/`, env no secreta por `production/.env`, generación en `prepare.sh`, inventario en docs. La URL usa lookup plano `${VAR}` sin default con slashes porque `docker stack config` lo maneja mal (ver comentario en `docker-compose.yml:94-97`).
**Tech Stack:** Docker Swarm `docker-compose.yml`, `production/.env.example`, `scripts/prepare.sh`, `production/secrets/README.md`, `docs/network-secrets.md`.

---

## Tareas

### Tarea 1: `production/docker-compose.yml`

**Archivos:**

- Modificar: `production/docker-compose.yml:104-146`, `291-315`

- [ ] Paso 1: En `backend.environment`, tras `SMP_REGISTRATION_MODE`, añadir:

```yaml
      SMP_WAITLIST_WITHDRAWAL_PUBLIC_URL_BASE: "${SMP_WAITLIST_WITHDRAWAL_PUBLIC_URL_BASE}"
```

Lookup plano y entrecomillado, sin default embebido (el valor resuelto vive en `production/.env`).

- [ ] Paso 2: En `backend.secrets`, tras `publishing_credentials_key`, añadir:

```yaml
      - source: waitlist_withdrawal_encryption_key
        target: SMP_WAITLIST_WITHDRAWAL_ENCRYPTION_KEY
        uid: "1002"
        gid: "1001"
        mode: 0400
```

- [ ] Paso 3: En `secrets:` al final, añadir:

```yaml
  waitlist_withdrawal_encryption_key:
    external: true
    name: profiletailors_waitlist_withdrawal_v1
```

- [ ] Paso 4: Checkpoint de revisión.

### Tarea 2: `production/.env.example` + `scripts/prepare.sh`

**Archivos:**

- Modificar: `production/.env.example:64-70`
- Modificar: `scripts/prepare.sh:45-70`

- [ ] Paso 1: En `.env.example`, bloque Optional integrations, añadir:

```
SMP_WAITLIST_WITHDRAWAL_PUBLIC_URL_BASE=https://profiletailors.com/waitlist/withdraw
```

- [ ] Paso 2: En `prepare.sh`, añadir `generate_secret "${secrets_dir}/waitlist-withdrawal-encryption-key"` tras la línea de `media-preview-signing-secret` y añadir el fichero al `chmod 600`.
- [ ] Paso 3: No crear ni mostrar valores secretos en logs. `prepare.sh` solo rellena ficheros vacíos.

### Tarea 3: Docs inventario

**Archivos:**

- Modificar: `production/secrets/README.md:11-20`
- Modificar: `docs/network-secrets.md:24-33`

- [ ] Paso 1: Añadir fila `waitlist-withdrawal-encryption-key` como Required en `secrets/README.md`.
- [ ] Paso 2: Añadir fila `profiletailors_waitlist_withdrawal_v1` → `production/secrets/waitlist-withdrawal-encryption-key` → `SMP_WAITLIST_WITHDRAWAL_ENCRYPTION_KEY` → `backend` en `network-secrets.md`.
- [ ] Paso 3: Checkpoint.

### Tarea 4: Verificación

- [ ] Paso 1: `git diff --stat` + `git diff` — solo los 5 ficheros, sin secretos.
- [ ] Paso 2: `bash -n scripts/prepare.sh` y `docker stack config` si hay Docker disponible (si no, declararlo como no ejecutado).
- [ ] Paso 3: `tests/cloudflare-tunnel-config-validation.sh` si el entorno lo permite (requiere `production/.env` + Docker); si no, declararlo no ejecutado con motivo.

---

## Evidencia previa

- `backend` usa `SPRING_CONFIG_IMPORT: configtree:/run/secrets/` (`docker-compose.yml:84`): el fichero montado como `SMP_WAITLIST_WITHDRAWAL_ENCRYPTION_KEY` resuelve el placeholder de `application.yaml`.
- Convención de nombres: source `snake_case`, fichero `kebab-case`, secreto versionado `profiletailors_*_v1`, target = nombre exacto de la env var.
- `application.yaml` en app repo ya expone `${SMP_WAITLIST_WITHDRAWAL_ENCRYPTION_KEY:}` y `${SMP_WAITLIST_WITHDRAWAL_PUBLIC_URL_BASE:...}`.

## Estado

- `Ready` — wiring aplicado en deploy repo y verificado localmente. Sin commit (no autorizado).
