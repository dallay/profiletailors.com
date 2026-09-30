# WireMock Mocks — Local Development

Este directorio contiene mocks de WireMock para simular las APIs de redes sociales durante desarrollo local.

## Estructura

```
infra/wiremock/
├── __files/                    # Archivos estáticos servidos por WireMock
├── mappings/
│   ├── linkedin/              # Mocks de LinkedIn API
│   └── threads/              # Mocks de Threads API (Meta)
├── compose.yaml               # Configuración Docker Compose
└── README.md                  # Este archivo
```

## Proveedores Soportados

### LinkedIn

Documentación de la API real: [LinkedIn Marketing API](https://learn.microsoft.com/en-us/linkedin/marketing/)

| Archivo | Método | Path | Descripción |
|---------|--------|------|-------------|
| `070-oauth-token.json` | POST | `/oauth/v2/accessToken` | OAuth token exchange |
| `060-userinfo.json` | GET | `/v2/userinfo` | Perfil de usuario |
| `010-posts-create-success.json` | POST | `/rest/posts` | Crear post |
| `030-images-initialize-upload.json` | POST | `/rest/images` | Inicializar upload de imagen |
| `031-images-get.json` | GET | `/rest/images/{id}` | Obtener imagen |
| `040-documents-initialize-upload.json` | POST | `/rest/documents` | Inicializar upload de documento |
| `041-documents-get.json` | GET | `/rest/documents/{id}` | Obtener documento |
| `050-videos-initialize-upload.json` | POST | `/rest/videos` | Inicializar upload de video |
| `051-videos-finalize-upload.json` | POST | `/rest/videos/{id}` | Finalizar upload de video |

#### Mocks de Error — LinkedIn

| Archivo | Condición | Status | Uso en Tests |
|---------|-----------|--------|--------------|
| `005-posts-rate-limit.json` | `commentary` contiene `LINKEDIN_RATE_LIMIT_TEST` | 429 | `THREADS_RATE_LIMIT_TEST` |
| `006-posts-validation-error.json` | `commentary` contiene `LINKEDIN_VALIDATION_TEST` | 400 | Validación de campos |
| `007-posts-invalid-token.json` | `Authorization: Bearer invalid-token` | 401 | Token inválido |

---

### Threads (Meta)

Documentación oficial: [Threads API](https://developers.facebook.com/docs/threads)

**Base URL:** `https://graph.threads.net/v1.0`

#### Mocks de Éxito

| Archivo | Método | Path | Descripción |
|---------|--------|------|-------------|
| `070-oauth-token.json` | POST | `/v1.0/oauth/access_token` | Exchange código por token corto |
| `071-oauth-long-lived-token.json` | GET | `/v1.0/access_token` | Convierte a token largo (60 días) |
| `072-oauth-refresh-token.json` | POST | `/v1.0/refresh_access_token` | Refresca token antes de expirar |
| `073-user-profile.json` | GET | `/v1.0/me` | Perfil del usuario (`id,username,name,threads_profile_picture_url`) |
| `080-threads-create-container.json` | POST | `/v1.0/{user_id}/threads` | Crea container para publicación |
| `081-container-status.json` | GET | `/v1.0/{container_id}?fields=status` | Consulta estado del container |
| `082-threads-publish.json` | POST | `/v1.0/{user_id}/threads_publish` | Publica el post |

#### Mocks de Error — Threads

| Archivo | Condición | Status | Código | Uso |
|---------|-----------|--------|--------|-----|
| `005-threads-invalid-token.json` | `Authorization: Bearer invalid-token` | 401 | 190/463 | Token inválido |
| `006-threads-expired-token.json` | `Authorization: Bearer expired-token` | 401 | 190/467 | Token expirado |
| `007-threads-permission-denied.json` | `text` contiene `THREADS_FORBIDDEN_TEST` | 403 | 10 | Permisos insuficientes |
| `008-threads-rate-limit.json` | `text` contiene `THREADS_RATE_LIMIT_TEST` | 429 | 4 | Rate limit excedido |
| `009-threads-server-error.json` | `text` contiene `THREADS_SERVER_ERROR_TEST` | 500 | 1 | Error interno |
| `010-threads-container-error.json` | Container ID contiene `ERROR` | 200 | — | Container en estado ERROR |

## Uso

### Levantar WireMock

```bash
just infra-up
```

WireMock estará disponible en `http://localhost:8080`.

### Configurar el Backend

Para que el backend use WireMock en desarrollo, configura las siguientes variables de entorno:

```bash
# LinkedIn
LINKEDIN_BASE_URL=http://localhost:8080
LINKEDIN_TOKEN_BASE_URL=http://localhost:8080

# Threads
THREADS_API_BASE_URL=http://localhost:8080
```

O en `application-local.yml`:

```yaml
publishing:
  linkedin:
    apiBaseUrl: http://localhost:8080
    tokenBaseUrl: http://localhost:8080
  threads:
    apiBaseUrl: http://localhost:8080
```

### Verificar que WireMock está corriendo

```bash
curl http://localhost:8080/__admin/health
```

Respuesta esperada: `{"status":"UP"}`

### Admin UI

WireMock provee una UI administrativa en: `http://localhost:8080/__admin/webapp`

Desde ahí puedes:

- Ver todas los stubs registrados
- Consultar requests recibidos
- Resetear el estado
- Ver logs

## Cómo Funcionan los Mocks

### Templates de Respuesta

WireMock usa [Response Templating](https://wiremock.org/docs/response-templating/) habilitado con `--global-response-templating`.

Variables disponibles en las respuestas:

```json
{
  "id": "{{randomValue length=12 type='NUMERIC'}}",
  "token": "mock-threads-{{randomValue length=8 type='ALPHANUMERIC'}}"
}
```

### Prioridades

- `priority: 10` — stubs normales (default)
- `priority: 5` — stubs de error (más específicos, se matchean primero)

### Headers Requeridos

Threads requiere:

```text
Authorization: Bearer <token>
Content-Type: application/json
```

LinkedIn requiere:

```text
Authorization: Bearer <token>
X-Restli-Protocol-Version: 2.0.0
Content-Type: application/json
```

## Agregar Nuevos Mocks

### 1. Crear el archivo JSON

Sigue la nomenclatura: `{priority}-{descripcion}.json`

Ejemplo: `090-threads-list-posts.json`

### 2. Estructura básica

```json
{
  "priority": 10,
  "request": {
    "method": "GET",
    "urlPathPattern": "/v1.0/[user-id]/threads",
    "headers": {
      "Authorization": { "matches": "Bearer mock-threads-.+" }
    }
  },
  "response": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "jsonBody": {
      "data": [],
      "paging": {}
    }
  }
}
```

### 3. Patrones de Body

Para matchear valores específicos en el body:

```json
"bodyPatterns": [
  { "matchesJsonPath": "$.text" },
  { "matchesJsonPath": "$[?(@.media_type == 'IMAGE')]" }
]
```

### 4. Regex en valores

```json
"bodyPatterns": [
  { "matchesJsonPath": "$[?(@.text =~ /.*TEST.*/)]" }
]
```

## Troubleshooting

### El mock no se activa

1. Verifica que el archivo está en `mappings/linkedin/` o `mappings/threads/`
2. Verifica que el JSON es válido: `cat file.json | jq .`
3. Revisa los stubs activos en `http://localhost:8080/__admin/webapp`
4. Verifica que el request real llega a WireMock (revisa logs)

### Token no matchea

Los mocks de Threads esperan tokens que empiecen con `mock-threads-`.

Para tokens específicos de error usa:

- `Bearer invalid-token` → 401 Invalid Token
- `Bearer expired-token` → 401 Expired Token

### Container status siempre retorna FINISHED

El mock `081-container-status.json` siempre retorna `FINISHED` para simplicidad.
Para simular otros estados, usa el mock `010-threads-container-error.json` con un ID de container que contenga `ERROR`.

## Referencias

- [Documentación WireMock](https://wiremock.org/docs/)
- [WireMock Stubbing](https://wiremock.org/docs/stubbing/)
- [WireMock Response Templating](https://wiremock.org/docs/response-templating/)
- [LinkedIn API](https://learn.microsoft.com/en-us/linkedin/marketing/)
- [Threads API](https://developers.facebook.com/docs/threads)
