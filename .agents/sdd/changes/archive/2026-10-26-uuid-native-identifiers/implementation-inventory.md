# Implementation inventory: native UUID identifiers

## Scope and evidence

Este inventario distingue el manifiesto de columnas del seguimiento de consumidores. La inspección
de `uuid_columns` anterior a incorporar notifications y el ámbito WAITLIST identificó 171 columnas
de 73 tablas y la raíz de cada referencia. El número anterior de 170 era una observación histórica.
El manifiesto ejecutable final incorpora además las referencias identificadas abajo: **174 entradas
únicas de 74 tablas**, contando la tabla opcional notifications y las dos nuevas referencias WAITLIST.
Sus pruebas de esquema son la evidencia de tipos resultantes. La validación de consumidores continúa mediante los gates registrados
en `apply-progress.md`; este inventario no autoriza despliegue ni acredita datos productivos.

## Relación de tablas con adaptadores actuales

Las rutas Kotlin siguientes son relativas a `server/smp/src/main/kotlin/com/profiletailors/smp/`.
Cada grupo conserva las columnas y raíces exactas del manifiesto SQL, sin inferir que una columna
llamada `id` sea interna fuera de ese manifiesto.

- Identity:
  - `principals`, `user_identities`: `identity/infrastructure/R2dbcPrincipalIdentityLookup.kt`,
    `R2dbcIdentityRegistrationGateway.kt`, `R2dbcPrincipalLifecycle.kt` y
    `platformadmin/infrastructure/persistence/R2dbcAdminUserQuery.kt`.
  - `local_password_credentials`: `identity/infrastructure/R2dbcLocalPasswordCredentialGateway.kt`
    y `R2dbcPasswordResetTokenRepository.kt`.
  - `email_verification_tokens`: `identity/infrastructure/R2dbcIdentityRegistrationGateway.kt`.
    Su PK histórico serial debe conservar generación automática después del corte.
  - `password_reset_tokens`: `identity/infrastructure/R2dbcPasswordResetTokenRepository.kt`.
  - `password_reset_notification_failures`:
    `identity/infrastructure/R2dbcPasswordResetNotificationFailureRepository.kt`.
  - `user_preferences`: `identity/infrastructure/R2dbcUserPreferencesGateway.kt`.
- Credentials:
  - `service_account_credentials`: `credentials/infrastructure/R2dbcServiceAccountCredentialStateLookup.kt`.
  - `api_key_credentials`: `credentials/infrastructure/R2dbcApiKeyCredentialStateLookup.kt`
    y `R2dbcApiKeyCredentialReplacementGateway.kt`.
  - `refresh_sessions`: `credentials/infrastructure/R2dbcRefreshSessionGateway.kt`.
    Los IDs de filas y sus referencias de rotación son UUID; los tokens de sesión son opacos.
- Tenancy y authorization:
  - `workspaces`: `tenancy/infrastructure/R2dbcWorkspaceReadRepository.kt`,
    `R2dbcWorkspaceMutationRepository.kt`, `R2dbcWorkspaceNameReader.kt` y
    `R2dbcWorkspaceProvisioningService.kt`.
  - `workspace_ownerships`: `tenancy/infrastructure/R2dbcWorkspaceOwnershipRepository.kt`,
    `R2dbcWorkspaceProvisioningService.kt` y `R2dbcWorkspaceReadRepository.kt`.
  - `workspace_memberships`: `tenancy/infrastructure/R2dbcWorkspaceMembershipRepository.kt`,
    `R2dbcWorkspaceMembershipResolver.kt`, `R2dbcWorkspaceProvisioningService.kt`,
    `R2dbcWorkspaceReadRepository.kt` y el query administrativo de usuarios.
  - `roles`, `permissions`, `role_permissions`, `membership_roles`:
    `authorization/infrastructure/persistence/R2dbcWorkspaceMembershipRoleResolver.kt`,
    provisión/lectura de workspace y query administrativo de usuarios.
  - `workspace_direct_grants`: `authorization/infrastructure/persistence/R2dbcDirectGrantResolver.kt`.
  - `workspace_entitlements`: `authorization/infrastructure/persistence/R2dbcWorkspaceEntitlementResolver.kt`.
  - `workspace_target_scopes`: `authorization/infrastructure/persistence/R2dbcWorkspaceTargetScopeResolver.kt`.
- Publishing:
  - `social_connections`, `social_accounts`, `social_content_actor_capabilities`,
    `social_content_comments`, `social_content_reply_commands`, `social_content_webhook_events`:
    `publishing/infrastructure/persistence/R2dbcPublishingConnectionRepositories.kt`.
  - `social_content_posts`, `social_content_sync_checkpoints`:
    `publishing/infrastructure/persistence/R2dbcSocialContentRepositories.kt` y
    `R2dbcPublishingConnectionRepositories.kt`.
  - `social_content_payload_cache`: sin consumidor SQL Kotlin literal en la búsqueda de producción;
    permanece en el manifiesto porque existe en el esquema y puede contener datos históricos.
  - `publications`, `publication_assets`, `publication_asset_links`, `publication_jobs`,
    `delivery_attempts`: `publishing/infrastructure/persistence/R2dbcPublishingRepositories.kt`.
    La hidratación de assets y el reemplazo de jobs son parte del mismo recorrido de retry.
  - `recurring_schedules`: `publishing/infrastructure/persistence/R2dbcRecurringScheduleRepository.kt`.
  - `bulk_import_jobs`, `bulk_import_rows`:
    `publishing/infrastructure/persistence/R2dbcBulkImportJobRepository.kt`.
  - `notification_events`: `publishing/infrastructure/persistence/R2dbcNotificationEventRepository.kt`.
  - `secure_credentials`: `publishing/infrastructure/credentials/ProviderCredentialGateway.kt`,
    `LinkedInCredentialGateway.kt` y `persistence/R2dbcCredentialPurgeRepository.kt`.
  - `credential_deletion_tombstones`:
    `publishing/infrastructure/persistence/R2dbcCredentialDeletionTombstoneRepository.kt`.
    Una conexión ya borrada puede conservarse aquí; el mapping histórico no exige una fila viva.
- Media:
  - `media_assets`, `workspace_upload_slots`, `media_rate_limits`, `workspace_file_blobs`:
    `media/infrastructure/persistence/R2dbcMediaRepositories.kt`.
    Los hashes, claves de almacenamiento y referencias Unsplash no se convierten por parecer IDs.
- Shortlinks:
  - `links`: `shortlinks/infrastructure/persistence/R2dbcLinkRepository.kt` y `R2dbcLinkFinderRepository.kt`.
  - `link_clicks`: `shortlinks/infrastructure/persistence/R2dbcRedirectClickRecorder.kt`
    y `R2dbcLinkClickMetricsRepository.kt`.
  - `shortlink_idempotency`: `shortlinks/infrastructure/persistence/R2dbcIdempotencyAdapter.kt`.
    El owner es UUID de workspace; la clave de idempotencia no es una entidad.
- Platform admin:
  - `platform_role_assignments`:
    `platformadmin/infrastructure/persistence/R2dbcPlatformRoleAssignmentRepository.kt`.
  - `invitations`: `platformadmin/infrastructure/persistence/R2dbcInvitationRepository.kt`,
    `R2dbcAdminInvitationQuery.kt` y `R2dbcAdminWaitlistQuery.kt`.
    `source_reference_id` WAITLIST se convierte mediante la raíz `waitlist_entries`.
  - `waitlist_invitations`: `platformadmin/infrastructure/persistence/R2dbcWaitlistInvitationRepository.kt`
    y `R2dbcAdminWaitlistQuery.kt`.
  - `platform_admin_user_control_idempotency`:
    `platformadmin/infrastructure/persistence/R2dbcUserControlIdempotencyStore.kt`.
  - `platform_admin_configuration_idempotency`:
    `platformadmin/infrastructure/persistence/R2dbcConfigurationIdempotencyStore.kt`.
  - `platform_admin_audit_events`: `platformadmin/infrastructure/persistence/R2dbcAdminAuditRepository.kt`.
    `target_id` y metadata exigen mapping contextual, no conversión universal de cadenas.
- Governance, auditoría y privacidad:
  - `audit_events`: `audit/infrastructure/R2dbcAuditHook.kt`
    y `governance/infrastructure/R2dbcAuditEventReader.kt`.
  - `compliance_controls`, `compliance_control_applicability_rules`:
    `governance/infrastructure/R2dbcComplianceControlRepository.kt`.
  - `compliance_control_applicability_dimensions`, `compliance_control_evidence_requirements`:
    sin consumidor SQL Kotlin literal encontrado; se convierten los datos persistidos y sus referencias.
  - `compliance_evidences`, `compliance_control_evidences`, `evidence_links`:
    `governance/infrastructure/R2dbcComplianceEvidenceRepository.kt`.
  - `compliance_risk_acceptances`: `governance/infrastructure/R2dbcComplianceRiskAcceptanceRepository.kt`.
  - `consent_records`, `consent_record_events`: `governance/infrastructure/R2dbcConsentRepository.kt`.
  - `takedown_reports`: `governance/infrastructure/R2dbcTakedownReportRepository.kt`.
  - `data_subject_requests`: `privacy/infrastructure/persistence/R2dbcDataSubjectRequestRepository.kt`.
    La auditoría global DSAR representa ausencia de workspace con `null`, no con el sentinel `__DSAR__`.
- Otros consumidores:
  - `ideas`, `idea_board_configs`: `ideas/infrastructure/persistence/R2dbcIdeaRepositories.kt`.
    Los IDs de columnas del tablero y los tipos de enlaces son semánticos.
  - `hashtag_saved_sets`: `hashtags/infrastructure/persistence/R2dbcHashtagSavedSetRepository.kt`.
  - `waitlists`, `waitlist_entries`: `leadcapture/infrastructure/persistence/R2dbcWaitlistRepositories.kt`,
    `platformadmin/infrastructure/persistence/R2dbcWaitlistEntryAdmin.kt` y `R2dbcAdminWaitlistQuery.kt`.
  - `waitlist_withdrawal_urls`: `leadcapture/infrastructure/notification/R2dbcWaitlistWithdrawalUrlRepository.kt`.
  - `idempotency_records`: `mcp/infrastructure/R2dbcIdempotencyRecordRepository.kt`.
    El PK histórico serial debe conservar generación automática; workspace/principal son referencias UUID.
  - `analytics/infrastructure/persistence/R2dbcAnalyticsRepository.kt` lee `publications` y sus relaciones.
- Notifications, tabla opcional creada fuera del baseline Liquibase:
  - `notifications.id`: `notifications/infrastructure/persistence/NotificationsSchemaInitializer.kt`,
    `R2dbcNotificationRepository.kt` y
    `platformadmin/infrastructure/persistence/R2dbcNotificationAdminQueryAdapter.kt`.
  - Su raíz histórica es `ntf-*`. El manifiesto la registra solo cuando existe la tabla antes del
    corte; el inicializador posterior crea UUID para instalaciones nuevas, sin migración al arrancar.
  - Las referencias lógicas incluyen auditoría `NOTIFICATION`, metadata `notificationId` y
    `retryNotificationId`, y payloads de templates conocidos. El mapping debe conservar secretos,
    destinatarios, claves de idempotencia y texto opaco.
- Consentimiento público WAITLIST aprobado en ADR-0030:
  - `consent_records.waitlist_id` y `consent_record_events.waitlist_id` referencian `waitlists.id`.
  - `workspace_id` y `waitlist_id` son excluyentes. `GovernanceWaitlistConsentRecorder` recibe la
    identidad real mediante el puerto `WaitlistConsentRecordRequest`; `waitlistKey` sigue siendo
    semántica. El dominio governance representa el ámbito sin importar entidades leadcapture.
  - Los consumidores de registro, consulta, historial, eventos y retirada deben discriminar el ámbito;
    la implementación y sus pruebas de integración están en curso tras aprobación del usuario.

## Fronteras, exclusiones y evidencia

- Hallazgo de cierre: `notifications.id` no figuraba en el manifiesto Liquibase anterior,
  porque `NotificationsSchemaInitializer` crea la tabla en `ApplicationReadyEvent`. Su PK sigue
  era `VARCHAR`, mientras `NotificationId` ya rechazaba IDs históricos `ntf-*`. La reparación
  incorpora la tabla opcional, consumidores, payloads tipados y auditoría. El orquestador repitió el
  ensayo final junto con WAITLIST: 34 guards con rollback, 93 FK y 405 índices válidos, e igualdad
  de esquema fresh/poblado. Falta la verificación runtime Kotlin/BDD central antes del cierre.
- La API conserva `application/vnd.api.v1+json`. Los DTO y JSON transportan UUID como cadenas;
  los adaptadores R2DBC enlazan columnas nativas como `java.util.UUID`. Un texto JSON no justifica
  enlazar `varchar` en SQL. Los campos opcionales deben usar el tipo UUID también al enlazar `NULL`.
- `X-Workspace-Id` entra por el resolver tenancy; membership/authorization siguen siendo controles
  separados. Validar formato no concede acceso ni demuestra aislamiento.
- Dashboard y admin consumen UUID como cadenas en sus APIs y fixtures. Los E2E comprobados están
  registrados en `apply-progress.md`. `BulkTemplate.defaultTemplates()` define `linkedin-calendar`
  como clave semántica, no PK; su fixture se conserva como tal.
- Se excluyen IDs de proveedor, URN, tokens, claves de idempotencia, hashes, claves de almacenamiento,
  `publications.group_id` y `publication_jobs.claimed_by_worker`. El lease `worker-*` no es una entidad.
- La revisión de productores restantes clasificó `ComplianceControlApplicabilityRuleId("seed")`
  como marcador transitorio del adaptador: `EvaluateComplianceHandler` consume `control` y `required`,
  y el controlador no publica esa identidad. No se persiste ni se consulta por ese valor. Es deuda de
  modelado; sustituirlo por un UUID aleatorio tampoco identificaría una regla real.
- `BulkValidationPipeline` utiliza `asset-*`, `draft-bulk-*`, `account-bulk-*` y `bulk-conflict-*`
  como correlaciones transitorias de validación; el resultado conserva índices/errores y no persiste
  esos IDs. `R2dbcNotificationEventRepository` reemplaza el ID vacío del worker por UUID antes de
  persistir. `rfs_*` pertenece al token opaco de refresh, no al PK `refresh_sessions.id`.
- Los payloads históricos tipados, auditoría polimórfica y subjects no se convierten por coincidencia
  textual genérica. El ensayo SQL debe demostrar mappings soportados y rollback ante tipos desconocidos.
- El ensayo desechable no sustituye backup/restore, ventana operativa ni validación de producción.
  La aprobación de despliegue sigue bloqueada y no se ejecuta SQL productivo desde esta tarea.

## Descubrimiento histórico

La sección siguiente conserva el estado inicial de exploración; sus pendientes y conteos no reemplazan
el manifiesto actual ni los resultados de verificación más recientes.

| Owner | Confirmed candidates / evidence | Status and next inspection |
|---|---|---|
| `tenancy` | `HeaderActiveWorkspaceContextResolver` receives the external `X-Workspace-Id`; `Workspace` and ownership/membership changelogs define tenant identity and references. | Workspace header parsing slice exists locally; audit all tenancy ID models, DB rows, auth/isolation and HTTP filter behavior. |
| `shortlinks` | `OwnerId` parses UUID; link ownership derives from active workspace. Changelogs include links, idempotency and click records. | Partial local slice. Audit IDs, schemas, indexes, fixtures and link API responses. |
| `identity` | Principal, user identity, password/verification/reset token and preference schema ownership. | Classify principal IDs versus opaque token values; audit models, persistence, HTTP DTOs and consumers. |
| `authorization` | Roles, grants, memberships and workspace entitlement/target relations. | Audit relation graph, seeded identifiers, policy and context consumers. |
| `publishing` | Social connections/accounts, publications, jobs, delivery attempts, recurring schedules, bulk import, credential tombstones; social/provider identifiers appear in API docs. | Classify internal IDs separately from provider IDs, token/credential material and idempotency keys; audit models, queues, events and callbacks. |
| `media` | `MediaModels.generateAssetId()` creates UUID text; media DTO examples include prefixed workspace IDs; media assets/blob relations. | Audit generator/model/persistence/API and storage references; distinguish opaque storage keys and external metadata. |
| `platformadmin` | Platform role assignments, invitations, audit events, user-control/configuration idempotency; admin frontend derives operation keys using `crypto.randomUUID()`. | Classify persistent entity IDs from idempotency/operation keys; audit API and frontend. |
| `governance`, `audit`, `privacy`, `credentials` | Audit events, consent records, takedown/evidence links, data-subject requests, service/API credentials and refresh sessions. | Classify internal row IDs versus credential/token/session opaque values; audit retention and privacy workflows. |
| `analytics`, `hashtags`, `ideas`, `leadcapture`, `notifications`, `observability`, `mcp` | Additional context-owned IDs; `shared/notifications` generates `ntf-` IDs; MCP includes idempotency records. | Audit each model, generator, schema and consumer; avoid changing public or opaque contracts without evidence. |
| Shared common / Spring | `shared/spring-boot-common` parses a UUID in `ApiController`; shared notification domain has prefixed notification IDs. | Assign precise owners and consumer traces; review nominal ID contracts and all parser callsites. |
| Dashboard (`apps/web/app`) | Publishing CSV parser, API DTOs, request headers, tests and fixtures consume backend contracts. | Inventory IDs by feature barrel and API endpoint; distinguish user-entered/external/provider values. |
| Admin (`apps/web/admin`) | Platform admin views create browser UUID operation keys. | Inventory API request/response identifiers and mocks; distinguish command idempotency from resource IDs. |
| Marketing, shared web, tools | No candidates confirmed by the initial bounded source search. | Still require repository-wide search and explicit classification before claiming no consumers. |
| Liquibase | 107 changelog files under `server/smp/src/main/resources/db/changelog`; search found 530 ID/type-like occurrences. | Unclassified. Build a per-table/column PK/FK/index/constraint graph before preparing executable conversion statements. |
| Tests and fixtures | Backend, BDD, dashboard/admin tests, HAR fixtures, QA/dev seeds and contract examples contain likely identifier literals. | Not fully enumerated; map every affected fixture to owner/contract. |

## Search evidence

The local source searches found 116 production occurrences of UUID generation/parsing and 71 prefixed-ID-shaped production occurrences across the backend, shared Kotlin, dashboard and admin. These are discovery counts, not a classified list of internal IDs. The search intentionally does not classify every raw `id` field as internal: provider IDs, credentials/tokens, idempotency keys, storage keys and opaque IDs require owner-level confirmation.

## Security and consistency review of the first slice

The workspace header resolver now rejects prefixed or non-canonical values before constructing `ResourceContext`, and the error handler maps the typed validation failure to HTTP 400. This prevents malformed tenant identifiers from entering downstream context and does not grant workspace access by itself. The change does not establish authentication, authorization, workspace membership, or isolation correctness; those remain enforced and must be tested at the HTTP filter/application boundaries. Tests were added but the Gradle runner did not start because distribution download timed out, so the behavior is not claimed as test-verified. No database, API-wide, client-wide or production migration is implied by this narrow parser boundary.

## Exclusions and deployment gate

Provider/external identifiers, credentials/tokens, idempotency keys, storage keys, and opaque values remain excluded unless evidence confirms they are internal resource IDs. No production SQL has been generated or executed. Deployment remains blocked pending complete column/consumer inventory, reviewed migration SQL and runbook, approved operational window/strategy, backup and rollback/forward-fix plan, and user-executed production pre/postcondition validation.
