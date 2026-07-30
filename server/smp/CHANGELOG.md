# Changelog

## [0.3.5](https://github.com/dallay/profiletailors.com/compare/smp@v0.3.4...smp@v0.3.5) (2026-07-30)


### Features

* **app:** add password recovery frontend flow ([#506](https://github.com/dallay/profiletailors.com/issues/506)) ([15d26f7](https://github.com/dallay/profiletailors.com/commit/15d26f77ee1b2a0b3ec749c93bcce5dc4faf94da))
* **app:** redesign login/register with responsive UI, capability gates, and accessibility ([#524](https://github.com/dallay/profiletailors.com/issues/524)) ([dfb500f](https://github.com/dallay/profiletailors.com/commit/dfb500f25cbe660578198f00cf0453b9bd11990e))
* dallay 433 media asset deduplication content addressed storage ([#463](https://github.com/dallay/profiletailors.com/issues/463)) ([a99cc59](https://github.com/dallay/profiletailors.com/commit/a99cc590e694b97b891d8d5e2f8ec0941b2f0f4c))
* **identity:** harden password recovery with retry, telemetry, cleanup, and audit ([#509](https://github.com/dallay/profiletailors.com/issues/509)) ([8ff6f71](https://github.com/dallay/profiletailors.com/commit/8ff6f71f2a226d0949b06ca46d669b3793a3aa7c))
* **identity:** implement secure password recovery backend ([#499](https://github.com/dallay/profiletailors.com/issues/499)) ([4b4b6a6](https://github.com/dallay/profiletailors.com/commit/4b4b6a65020800815f83e6bd91f51ec62eac5cc0))
* **lead-capture:** complete welcome email notifier wiring ([#487](https://github.com/dallay/profiletailors.com/issues/487)) ([97da228](https://github.com/dallay/profiletailors.com/commit/97da228b72f0cf7717a11c5c60d5d23a3c7a20d5))
* **platform:** add health check endpoint ([#528](https://github.com/dallay/profiletailors.com/issues/528)) ([cf3a1f1](https://github.com/dallay/profiletailors.com/commit/cf3a1f1f93ad428add97bf2752e316f08c2e9337))
* **publishing:** add resolved provider catalog ([#464](https://github.com/dallay/profiletailors.com/issues/464)) ([e5e7fc1](https://github.com/dallay/profiletailors.com/commit/e5e7fc18a06666e68848fd1aeb0919a8978dd2a6))
* **publishing:** enhance provider catalog response with lowercase provider names ([07219d5](https://github.com/dallay/profiletailors.com/commit/07219d5a7f5b06b051722154e8ab960f52d2a35e))


### Refactoring

* harden auth/media error handling and csrf-origin checks ([#456](https://github.com/dallay/profiletailors.com/issues/456)) ([108ca27](https://github.com/dallay/profiletailors.com/commit/108ca27cca6f0c76b021b7d46cf909abd4a24b34))
* **smp:** extract shared SpringJwtClaimsMapper, remove identity duplication ([#462](https://github.com/dallay/profiletailors.com/issues/462)) ([af7c08a](https://github.com/dallay/profiletailors.com/commit/af7c08a55cda50e483aa17ad84a214b4bc3d784e))

## [0.3.4](https://github.com/dallay/profiletailors.com/compare/smp@v0.3.3...smp@v0.3.4) (2026-07-30)


### Features

* **app:** add password recovery frontend flow ([#506](https://github.com/dallay/profiletailors.com/issues/506)) ([15d26f7](https://github.com/dallay/profiletailors.com/commit/15d26f77ee1b2a0b3ec749c93bcce5dc4faf94da))
* **app:** redesign login/register with responsive UI, capability gates, and accessibility ([#524](https://github.com/dallay/profiletailors.com/issues/524)) ([dfb500f](https://github.com/dallay/profiletailors.com/commit/dfb500f25cbe660578198f00cf0453b9bd11990e))
* dallay 433 media asset deduplication content addressed storage ([#463](https://github.com/dallay/profiletailors.com/issues/463)) ([a99cc59](https://github.com/dallay/profiletailors.com/commit/a99cc590e694b97b891d8d5e2f8ec0941b2f0f4c))
* **identity:** harden password recovery with retry, telemetry, cleanup, and audit ([#509](https://github.com/dallay/profiletailors.com/issues/509)) ([8ff6f71](https://github.com/dallay/profiletailors.com/commit/8ff6f71f2a226d0949b06ca46d669b3793a3aa7c))
* **identity:** implement secure password recovery backend ([#499](https://github.com/dallay/profiletailors.com/issues/499)) ([4b4b6a6](https://github.com/dallay/profiletailors.com/commit/4b4b6a65020800815f83e6bd91f51ec62eac5cc0))
* **lead-capture:** complete welcome email notifier wiring ([#487](https://github.com/dallay/profiletailors.com/issues/487)) ([97da228](https://github.com/dallay/profiletailors.com/commit/97da228b72f0cf7717a11c5c60d5d23a3c7a20d5))
* **platform:** add health check endpoint ([#528](https://github.com/dallay/profiletailors.com/issues/528)) ([cf3a1f1](https://github.com/dallay/profiletailors.com/commit/cf3a1f1f93ad428add97bf2752e316f08c2e9337))
* **publishing:** add resolved provider catalog ([#464](https://github.com/dallay/profiletailors.com/issues/464)) ([e5e7fc1](https://github.com/dallay/profiletailors.com/commit/e5e7fc18a06666e68848fd1aeb0919a8978dd2a6))
* **publishing:** enhance provider catalog response with lowercase provider names ([07219d5](https://github.com/dallay/profiletailors.com/commit/07219d5a7f5b06b051722154e8ab960f52d2a35e))


### Bug Fixes

* **smp:** align infra/scripts with PUBLISHING_CREDENTIALS_ENCRYPTION_KEY rename ([3fce115](https://github.com/dallay/profiletailors.com/commit/3fce115f5f18803d4c40b3346240a5d98b2ad550))


### Refactoring

* harden auth/media error handling and csrf-origin checks ([#456](https://github.com/dallay/profiletailors.com/issues/456)) ([108ca27](https://github.com/dallay/profiletailors.com/commit/108ca27cca6f0c76b021b7d46cf909abd4a24b34))
* **smp:** extract shared SpringJwtClaimsMapper, remove identity duplication ([#462](https://github.com/dallay/profiletailors.com/issues/462)) ([af7c08a](https://github.com/dallay/profiletailors.com/commit/af7c08a55cda50e483aa17ad84a214b4bc3d784e))

## [0.3.3](https://github.com/dallay/profiletailors.com/compare/smp@v0.3.2...smp@v0.3.3) (2026-07-29)


### Features

* **app:** add password recovery frontend flow ([#506](https://github.com/dallay/profiletailors.com/issues/506)) ([15d26f7](https://github.com/dallay/profiletailors.com/commit/15d26f77ee1b2a0b3ec749c93bcce5dc4faf94da))
* dallay 433 media asset deduplication content addressed storage ([#463](https://github.com/dallay/profiletailors.com/issues/463)) ([a99cc59](https://github.com/dallay/profiletailors.com/commit/a99cc590e694b97b891d8d5e2f8ec0941b2f0f4c))
* **identity:** harden password recovery with retry, telemetry, cleanup, and audit ([#509](https://github.com/dallay/profiletailors.com/issues/509)) ([8ff6f71](https://github.com/dallay/profiletailors.com/commit/8ff6f71f2a226d0949b06ca46d669b3793a3aa7c))
* **identity:** implement secure password recovery backend ([#499](https://github.com/dallay/profiletailors.com/issues/499)) ([4b4b6a6](https://github.com/dallay/profiletailors.com/commit/4b4b6a65020800815f83e6bd91f51ec62eac5cc0))
* **lead-capture:** complete welcome email notifier wiring ([#487](https://github.com/dallay/profiletailors.com/issues/487)) ([97da228](https://github.com/dallay/profiletailors.com/commit/97da228b72f0cf7717a11c5c60d5d23a3c7a20d5))
* **publishing:** add resolved provider catalog ([#464](https://github.com/dallay/profiletailors.com/issues/464)) ([e5e7fc1](https://github.com/dallay/profiletailors.com/commit/e5e7fc18a06666e68848fd1aeb0919a8978dd2a6))
* **publishing:** enhance provider catalog response with lowercase provider names ([07219d5](https://github.com/dallay/profiletailors.com/commit/07219d5a7f5b06b051722154e8ab960f52d2a35e))


### Bug Fixes

* **smp:** align infra/scripts with PUBLISHING_CREDENTIALS_ENCRYPTION_KEY rename ([3fce115](https://github.com/dallay/profiletailors.com/commit/3fce115f5f18803d4c40b3346240a5d98b2ad550))
* **smp:** align publishing credentials env var name across all layers ([e54456b](https://github.com/dallay/profiletailors.com/commit/e54456b499ccf74bd0642729697010adde83af94))


### Refactoring

* harden auth/media error handling and csrf-origin checks ([#456](https://github.com/dallay/profiletailors.com/issues/456)) ([108ca27](https://github.com/dallay/profiletailors.com/commit/108ca27cca6f0c76b021b7d46cf909abd4a24b34))
* **smp:** extract shared SpringJwtClaimsMapper, remove identity duplication ([#462](https://github.com/dallay/profiletailors.com/issues/462)) ([af7c08a](https://github.com/dallay/profiletailors.com/commit/af7c08a55cda50e483aa17ad84a214b4bc3d784e))

## [0.3.2](https://github.com/dallay/profiletailors.com/compare/smp@v0.3.1...smp@v0.3.2) (2026-07-23)


### Refactoring

* **smp:** move backend.Dockerfile to server/smp for Release Please detection ([e0839fb](https://github.com/dallay/profiletailors.com/commit/e0839fb4d16a01cdee05c8341964e907c8443d76))

## [0.3.1](https://github.com/dallay/profiletailors.com/compare/smp@v0.3.0...smp@v0.3.1) (2026-07-22)


### Features

* **compliance:** add release gate evaluation and evidence link management ([#438](https://github.com/dallay/profiletailors.com/issues/438)) ([04a8cfd](https://github.com/dallay/profiletailors.com/commit/04a8cfdbe51800ce917bd8cc6d99b1a3368851d1))
* **identity:** implement account closure feature (DALLAY-497) ([#430](https://github.com/dallay/profiletailors.com/issues/430)) ([f5e98be](https://github.com/dallay/profiletailors.com/commit/f5e98beda90a524e200e1a31cf19a510d66998fd))
* **media-copyright-takedown:** complete SDD cycle — implement, verify, and archive ([4a53332](https://github.com/dallay/profiletailors.com/commit/4a5333212ba8f309f55aabdda1e57dbabea117f2))


### Bug Fixes

* **config:** update log file path in development configuration ([081f341](https://github.com/dallay/profiletailors.com/commit/081f341d0bd63d79f2f596ebc2d63a28919ee611))
* **identity:** add permissive email verification policy for dev profile ([7daa41f](https://github.com/dallay/profiletailors.com/commit/7daa41f7cfd3522b292e89cf0cc251d9da589c11))
* **quality:** resolve 14 SonarCloud issues blocking quality gate ([#436](https://github.com/dallay/profiletailors.com/issues/436)) ([1593ce7](https://github.com/dallay/profiletailors.com/commit/1593ce795490429a4703991422a559debb50923b))
* **quality:** resolve 3 SonarCloud issues blocking quality gate ([#437](https://github.com/dallay/profiletailors.com/issues/437)) ([f3de1a8](https://github.com/dallay/profiletailors.com/commit/f3de1a8f5721ea6447826ea0709b30649011c818))


### Refactoring

* **publishing:** rename wiremock check-status mappings to get + update asset uploader ([87186c5](https://github.com/dallay/profiletailors.com/commit/87186c5cdc70978ec83cec31b80e2816f9d126b8))

## [0.3.0](https://github.com/dallay/profiletailors.com/compare/smp@v0.2.0...smp@v0.3.0) (2026-07-21)


### ⚠ BREAKING CHANGES

* **tests:** RealLinkedInAssetUploader.storage and RealLinkedInPublisher.storage are now Storage? (nullable). The !! call in buildAssetContentEntities is safe because that path is only reached in real mode where storage is always configured.
* **publishing:** RealLinkedInAssetUploader.storage and RealLinkedInPublisher.storage are now Storage? (nullable). The !! call in buildAssetContentEntities is safe because that path is only reached in real mode where storage is always configured.

### Features

* accessibility audit fixes, focus trap composable, and publishing backend improvements ([#139](https://github.com/dallay/profiletailors.com/issues/139)) ([67c9444](https://github.com/dallay/profiletailors.com/commit/67c94447cdc224652788cdc355268fb4ab63f4c0))
* Add email verification status to user profile and infrastructure ([d6d6e7b](https://github.com/dallay/profiletailors.com/commit/d6d6e7b43e9663eb76ee418ce030a889c3295c85))
* add LinkedIn channel avatar support with backend persistence and frontend fallback ([743ae47](https://github.com/dallay/profiletailors.com/commit/743ae47dbf8ff621d4a4cef222dae44155a3e1bb))
* Add workspace icon support (Lucide icons, phase 1) ([#81](https://github.com/dallay/profiletailors.com/issues/81)) ([09ed42f](https://github.com/dallay/profiletailors.com/commit/09ed42fb77d4abe522a800645bd94b204a08f93c))
* **auth:** harden local auth, stabilize register-flow E2E, add coverage ([#111](https://github.com/dallay/profiletailors.com/issues/111)) ([7098431](https://github.com/dallay/profiletailors.com/commit/709843133719a84407ab93355a600d86ad4a9f23))
* **auth:** implement session refresh with HttpOnly cookie and in-memory access token ([#27](https://github.com/dallay/profiletailors.com/issues/27)) ([6ae3db0](https://github.com/dallay/profiletailors.com/commit/6ae3db00d1b8565e610ee4daf5005ab5543ab7fc))
* **auth:** migrate email/password hashing from BCrypt to Argon2id ([#250](https://github.com/dallay/profiletailors.com/issues/250)) ([07bf036](https://github.com/dallay/profiletailors.com/commit/07bf036e8f2481ca0d950c1bcb01fc1e9b150b49))
* **authorization:** add workspace target scopes and resource preview ([#5](https://github.com/dallay/profiletailors.com/issues/5)) ([69b7086](https://github.com/dallay/profiletailors.com/commit/69b708613525d4a186e564424b672fe6adc3c51c))
* backend env loading, tenancy workspace, frontend dashboard, security fixes ([#78](https://github.com/dallay/profiletailors.com/issues/78)) ([7be0b4b](https://github.com/dallay/profiletailors.com/commit/7be0b4b48d97db515486574b150608446bed7b2e))
* backend feature entitlements, credential lifecycle, and DevSecOps hardening ([8f94cd5](https://github.com/dallay/profiletailors.com/commit/8f94cd5022d2130ec1e8a18bea03d0be4d5858a3))
* backend feature entitlements, credential lifecycle, and DevSecOps hardening ([76d3ad3](https://github.com/dallay/profiletailors.com/commit/76d3ad3ded0fa2b1c8cbbc6ebb2468c5e57ca428))
* **bazel:** add native Bazel compilation targets for backend ([#3](https://github.com/dallay/profiletailors.com/issues/3)) ([040dfa8](https://github.com/dallay/profiletailors.com/commit/040dfa8847c2f934bdaeaf46177166f6f89f0a7b))
* **calendar:** add publication click event and improve accessibility in month view ([#145](https://github.com/dallay/profiletailors.com/issues/145)) ([b6f1d93](https://github.com/dallay/profiletailors.com/commit/b6f1d93ccbbcadcb03ade6a06f5e1aad8ee0b966))
* **compliance:** Legal & Compliance Foundation — docs, ADR-0012, and validation tooling ([#368](https://github.com/dallay/profiletailors.com/issues/368)) ([f0b5ea3](https://github.com/dallay/profiletailors.com/commit/f0b5ea3c5d8d40dfc655e4ee76b0e74865b6bd70))
* connect Vue SPA channels to Spring Boot LinkedIn backend ([#75](https://github.com/dallay/profiletailors.com/issues/75)) ([64e6ccc](https://github.com/dallay/profiletailors.com/commit/64e6ccc65c987147c170cd335f974406953dd123))
* **coverage:** update Codecov configuration and align with SonarQube standards ([1d10e88](https://github.com/dallay/profiletailors.com/commit/1d10e8862c03d93eab25021deca74a1df723482e))
* **detekt:** add baseline configuration ([35f0c87](https://github.com/dallay/profiletailors.com/commit/35f0c87be4a4fac30f7fbdbd154cedd5009c08ea))
* **domain:** introduce core domain interfaces for command, query, and notification handling ([7e02325](https://github.com/dallay/profiletailors.com/commit/7e02325301206f72c64519ddac156a09ec20b1e3))
* **email:** add Resend adapter for transactional email delivery ([9589cb6](https://github.com/dallay/profiletailors.com/commit/9589cb60386fac956348ac838d751868692736c4))
* **email:** add Resend adapter for transactional email delivery ([866cb2b](https://github.com/dallay/profiletailors.com/commit/866cb2b77b56fae1509c2c65cb155e625c782a9c)), closes [#104](https://github.com/dallay/profiletailors.com/issues/104)
* **email:** add styled HTML verification email with plain-text fallback and env-aware URLs ([#220](https://github.com/dallay/profiletailors.com/issues/220)) ([1dda267](https://github.com/dallay/profiletailors.com/commit/1dda2672bf5757e2be01332490612f2ec018b844))
* **email:** configure Mailpit SMTP for dev and document production setup ([41d63b6](https://github.com/dallay/profiletailors.com/commit/41d63b680a223db28a4660384faa9c6d3aed3ee9)), closes [#103](https://github.com/dallay/profiletailors.com/issues/103)
* **email:** configure Mailpit SMTP for dev and document production setup ([#105](https://github.com/dallay/profiletailors.com/issues/105)) ([5422ffa](https://github.com/dallay/profiletailors.com/commit/5422ffa22c6e1fad853e4408208319872f58f74f))
* enable Docker Compose in application configuration ([3b57ee0](https://github.com/dallay/profiletailors.com/commit/3b57ee0fd2a6337bd07cf9d3813ccdb296bc74e4))
* expand app shell, API versioning docs, and backend test coverage ([#29](https://github.com/dallay/profiletailors.com/issues/29)) ([2318f50](https://github.com/dallay/profiletailors.com/commit/2318f50fee6404116bc195da6552d47ac43b559b))
* **governance:** add audit events API with cursor pagination ([ded1f4d](https://github.com/dallay/profiletailors.com/commit/ded1f4dc769c469b900490076d8b65f7bf433d41))
* **governance:** add DMCA takedown report UI and review dashboard (DALLAY-499 Phase 2) ([#417](https://github.com/dallay/profiletailors.com/issues/417)) ([0c3bd68](https://github.com/dallay/profiletailors.com/commit/0c3bd68afa86bd29d6b3150e195f8fa09abb7a5f))
* **governance:** add versioned consent records with CQRS handlers and waitlist integration ([#383](https://github.com/dallay/profiletailors.com/issues/383)) ([24ed347](https://github.com/dallay/profiletailors.com/commit/24ed3470e4bd49911fc9b64cea022c73d596a235))
* **identity:** add email verification flow with token-based verify and resend ([#85](https://github.com/dallay/profiletailors.com/issues/85)) ([b5a9644](https://github.com/dallay/profiletailors.com/commit/b5a9644bc7ecaee2f3d523019074891fb5385e1a))
* **identity:** add registration control gate and public capabilities API ([#395](https://github.com/dallay/profiletailors.com/issues/395)) ([e2dc7d9](https://github.com/dallay/profiletailors.com/commit/e2dc7d91aca3b8a1cb26dcfe38d6530d2a0d4fe2))
* **identity:** enforce age eligibility and terms acceptance during registration ([#387](https://github.com/dallay/profiletailors.com/issues/387)) ([2da26fb](https://github.com/dallay/profiletailors.com/commit/2da26fb9ee67a86e7542b2bf08e7c46072d358b3))
* **infra:** add production container deployments ([#388](https://github.com/dallay/profiletailors.com/issues/388)) ([cac929a](https://github.com/dallay/profiletailors.com/commit/cac929a98140ecd434d6fdb04758e8164f616afc))
* initialize project structure with core modules and configuration files ([30234c4](https://github.com/dallay/profiletailors.com/commit/30234c415d50aa43981017e62dc708a93a60b0de))
* integrate Detekt static analysis ([9c871be](https://github.com/dallay/profiletailors.com/commit/9c871bec2e4b8d39c373ec6ba626b3575eba7648))
* **lead-capture:** add waitlist persistence adapters ([#344](https://github.com/dallay/profiletailors.com/issues/344)) ([03a49b5](https://github.com/dallay/profiletailors.com/commit/03a49b50ec4b9797f739b1056d4a5bb80e82a3cd))
* **lead-capture:** expose POST waitlist join endpoint (DALLAY-439) ([#367](https://github.com/dallay/profiletailors.com/issues/367)) ([6c99039](https://github.com/dallay/profiletailors.com/commit/6c99039b48b1d4d1caaa2907cd3df79ee87befc6))
* **lead-capture:** rate limit waitlist joins ([#378](https://github.com/dallay/profiletailors.com/issues/378)) ([19793d2](https://github.com/dallay/profiletailors.com/commit/19793d2d10c52ccdcbfb11de3940094ed136fb14))
* **legal:** consolidate legal domain features — consent API, DSAR, compliance, identity, and policy ([#407](https://github.com/dallay/profiletailors.com/issues/407)) ([87ff2d9](https://github.com/dallay/profiletailors.com/commit/87ff2d9d07c75ab0edc7521fa131b7692813f0de))
* **media,ci:** workspace-level CAS media dedup + Postgres Testcontainers + detekt v2 + Biome root (size-exception) ([#174](https://github.com/dallay/profiletailors.com/issues/174)) ([baac461](https://github.com/dallay/profiletailors.com/commit/baac461c2e933991f9cbfa8c2493465cea512bbf))
* **media:** add centralized media library MVP ([#110](https://github.com/dallay/profiletailors.com/issues/110)) ([8f07e25](https://github.com/dallay/profiletailors.com/commit/8f07e25cf5ff00cb6959073e39b29dbd039b94b2))
* **media:** add licence schema and attribution display (DALLAY-499 Phase 1) ([#412](https://github.com/dallay/profiletailors.com/issues/412)) ([f53243b](https://github.com/dallay/profiletailors.com/commit/f53243be5e6ec30da5dccc901305cfb45a8465bc))
* **media:** add local auth and media library workflows ([#120](https://github.com/dallay/profiletailors.com/issues/120)) ([6e70486](https://github.com/dallay/profiletailors.com/commit/6e704868f3d1003d58973164f6fcbc0502207951))
* **media:** external asset metadata schema and H2 elimination ([#240](https://github.com/dallay/profiletailors.com/issues/240)) ([b432323](https://github.com/dallay/profiletailors.com/commit/b432323143548da8e1cb4bd70c6749622a1cf5bb))
* **media:** integrate Unsplash as first media provider ([#249](https://github.com/dallay/profiletailors.com/issues/249)) ([d06f833](https://github.com/dallay/profiletailors.com/commit/d06f83391eb2cee0f38263a1a1f99c31f8927a61))
* notifications module + publishing refactor + privacy DTO typing ([#410](https://github.com/dallay/profiletailors.com/issues/410)) ([0a1dbdc](https://github.com/dallay/profiletailors.com/commit/0a1dbdcc5c716d0c8d71a3718b66945018885360))
* **privacy:** implement DSAR workflows for GDPR/CCPA compliance ([#392](https://github.com/dallay/profiletailors.com/issues/392)) ([9c46ac1](https://github.com/dallay/profiletailors.com/commit/9c46ac1d458b2b699921e1249d1181b9aa1d6eb9))
* **publishing:** add failure visibility and retry recovery for MVP core publishing ([#303](https://github.com/dallay/profiletailors.com/issues/303)) ([96efe6a](https://github.com/dallay/profiletailors.com/commit/96efe6a3bab94fe0838e651a3bdedf9827eb1fdb))
* **publishing:** add LinkedIn media upload support ([#51](https://github.com/dallay/profiletailors.com/issues/51)) ([9ea9869](https://github.com/dallay/profiletailors.com/commit/9ea986960e5801edc35d89ccdd3ec5cbc1f7f262))
* **publishing:** add LinkedIn Publishing MVP with workspace-scoped social media management ([#39](https://github.com/dallay/profiletailors.com/issues/39)) ([3e17d7e](https://github.com/dallay/profiletailors.com/commit/3e17d7ea4e548471dfa0c911b88edf95d5c2cc06))
* **publishing:** complete backend-integrated edit/delete for unpublished posts ([#131](https://github.com/dallay/profiletailors.com/issues/131)) ([#143](https://github.com/dallay/profiletailors.com/issues/143)) ([60bc76e](https://github.com/dallay/profiletailors.com/commit/60bc76e5070220036a8bb0508a401a3bd0441371))
* **publishing:** improve user-facing error messages for failed post publishing ([#335](https://github.com/dallay/profiletailors.com/issues/335)) ([dbd289a](https://github.com/dallay/profiletailors.com/commit/dbd289acfe793b806a5611a5403a9b1a5cedc52d))
* **publishing:** LinkedIn integration publication ([#99](https://github.com/dallay/profiletailors.com/issues/99)) ([5f15d0a](https://github.com/dallay/profiletailors.com/commit/5f15d0a0c9b2e81b4cfc7ab9eddd5d632a756b0c))
* **publishing:** real delete endpoint + persisted edit for unpublished posts ([#131](https://github.com/dallay/profiletailors.com/issues/131)) ([#138](https://github.com/dallay/profiletailors.com/issues/138)) ([ed06c17](https://github.com/dallay/profiletailors.com/commit/ed06c17e013d5dee68d2c47188b01a8d496a4d56))
* **publishing:** structured worker lifecycle logs ([#338](https://github.com/dallay/profiletailors.com/issues/338)) ([e533a48](https://github.com/dallay/profiletailors.com/commit/e533a482b8a7195d46b95db6e6b376c5690d5e7e))
* refactor App shell into 10 focused components and add image proxy API ([#88](https://github.com/dallay/profiletailors.com/issues/88)) ([62d313e](https://github.com/dallay/profiletailors.com/commit/62d313ed6fede794e6b1f3e35675a0a69d8a8501))
* refactor post scheduling to single-account selection and fix credential resolution path ([#86](https://github.com/dallay/profiletailors.com/issues/86)) ([961998d](https://github.com/dallay/profiletailors.com/commit/961998df27eec2984c4a9924f266f5c164949915))
* remove username field from registration flow ([#71](https://github.com/dallay/profiletailors.com/issues/71)) ([1d6640c](https://github.com/dallay/profiletailors.com/commit/1d6640cbd0d436574d311469ffd2bdc89b549003))
* **scheduler:** E2E tests, CI workflow, and full-stack integration ([#101](https://github.com/dallay/profiletailors.com/issues/101)) ([12fd5a8](https://github.com/dallay/profiletailors.com/commit/12fd5a82fe07855758c8e549bd7c4f9a64c12977))
* **scheduler:** standardize URL state and deep-linkable post details ([#292](https://github.com/dallay/profiletailors.com/issues/292)) ([7c3d63a](https://github.com/dallay/profiletailors.com/commit/7c3d63ae988eff4debaf6a1f3bcc0436b05bd2d0))
* **security:** add production secrets docs and startup credentials validator ([#274](https://github.com/dallay/profiletailors.com/issues/274)) ([5825ea7](https://github.com/dallay/profiletailors.com/commit/5825ea7f98c087e73ebb88be4d2ea8a7d13ad07e))
* **smp:** add calendar repository queries, conflict detection policy, and activity thresholds ([#66](https://github.com/dallay/profiletailors.com/issues/66)) ([6203302](https://github.com/dallay/profiletailors.com/commit/6203302c476e77659bf8f22b048e6ee0753186b4))
* **smp:** add Detekt plugin and integrate with check ([476311a](https://github.com/dallay/profiletailors.com/commit/476311a0f90da985061e873b93ff9e5c8d0be724))
* **smp:** add direct grants persistence, service-account auth, and API key support ([edd5b34](https://github.com/dallay/profiletailors.com/commit/edd5b34c8d19de1ad65ac3edbcb359b44f06a464))
* **storage:** add R2 dedicated storage adapter with credentials wiring ([#65](https://github.com/dallay/profiletailors.com/issues/65)) ([a58e049](https://github.com/dallay/profiletailors.com/commit/a58e049fcc0acb56920c08632cc4ddb8bd06c8f9))
* **tenancy:** add multi-owner workspace ownership with audit ([99f6c80](https://github.com/dallay/profiletailors.com/commit/99f6c802198991d094427e4e4de47cc723285d9e))
* **tenancy:** implement concrete R2DBC repositories and missing owner removal handler ([#271](https://github.com/dallay/profiletailors.com/issues/271)) ([5100bdd](https://github.com/dallay/profiletailors.com/commit/5100bdda8bba28cac67b266fca4962956bb5d552))
* **tests:** bootstrap Vitest for Vue app + backend publishing test coverage ([#64](https://github.com/dallay/profiletailors.com/issues/64)) ([5c716e7](https://github.com/dallay/profiletailors.com/commit/5c716e750722e50b9d7b679b675adfd537f64b5b))
* **transactions:** standardize reactive transaction strategy (closes [#195](https://github.com/dallay/profiletailors.com/issues/195)) ([#230](https://github.com/dallay/profiletailors.com/issues/230)) ([3e99e11](https://github.com/dallay/profiletailors.com/commit/3e99e117288130cc12e36596902fde5290e0cf2b))
* **verification:** add BDD scenarios and E2E tests for email verification media gate ([#216](https://github.com/dallay/profiletailors.com/issues/216)) ([fda809a](https://github.com/dallay/profiletailors.com/commit/fda809aec4d483ebb3cde0c5e9b6031318d71255))
* wrap multi-write operations in transactional boundaries for 3 handlers ([#227](https://github.com/dallay/profiletailors.com/issues/227)) ([af2c8dd](https://github.com/dallay/profiletailors.com/commit/af2c8dd0f4930c850fc2755f02afa66a0920b9fd))


### Bug Fixes

* 13 code-review findings across apps/web/app and openspec/specs ([#156](https://github.com/dallay/profiletailors.com/issues/156)) ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))
* address CodeRabbit review findings for App shell + image proxy ([#90](https://github.com/dallay/profiletailors.com/issues/90)) ([d418d3d](https://github.com/dallay/profiletailors.com/commit/d418d3dc06b88420e0c78d09949245bce7a97c19))
* **architecture:** enforce hexagonal boundaries ([#115](https://github.com/dallay/profiletailors.com/issues/115)) ([94636b0](https://github.com/dallay/profiletailors.com/commit/94636b0a97f58a377d79803db814b31ce4fc3584))
* **auth:** assign WORKSPACE_OWNER role on provisioning, fix settings UX audit, and fix dialog transparency ([#91](https://github.com/dallay/profiletailors.com/issues/91)) ([5b337a0](https://github.com/dallay/profiletailors.com/commit/5b337a084934f382e0170bf4a3a9db06414a7a4c))
* **bdd:** enable email verification in seed and fix publishing BDD assertions ([#416](https://github.com/dallay/profiletailors.com/issues/416)) ([12bd603](https://github.com/dallay/profiletailors.com/commit/12bd603ca5c6f8f004ffedaedee49260c3771918))
* **ci:** resolve semgrep exit code, pnpm lockfile, gitleaks org license, and Java toolchain ([4ec374d](https://github.com/dallay/profiletailors.com/commit/4ec374db241f3c9d84dd1bde04bbd2c1fe1aa120))
* clear delivery attempts when replacing publication jobs ([#326](https://github.com/dallay/profiletailors.com/issues/326)) ([ffad235](https://github.com/dallay/profiletailors.com/commit/ffad235498b1a7aea3712c322cc52935153a8592))
* **common:** remove @Component from custom @Service to keep shared/common Spring-free ([9e48911](https://github.com/dallay/profiletailors.com/commit/9e489112daf71ae00561df7ec4aa1397a50f5834))
* **deps:** address ~74 Dependabot alerts - Spring Boot, Jackson, Netty, Astro, vite, undici, hono ([#162](https://github.com/dallay/profiletailors.com/issues/162)) ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* **devops:** move backend HTTP port to 7638 (SMP_BACKEND_PORT) ([3abccd7](https://github.com/dallay/profiletailors.com/commit/3abccd78fdcb6b7a89278ab336d6d4a8fc1823a7))
* **dev:** set email_status to VERIFIED for dev seed user ([c699723](https://github.com/dallay/profiletailors.com/commit/c699723337077cc9a00630aedb87b439067ecf61))
* **email:** guard Resend adapter against empty api-key ([a83a613](https://github.com/dallay/profiletailors.com/commit/a83a613c7611579f0dd9fa1f7737a551d48eb945))
* **identity:** disable auth rate limiter in tests and fix BDD registration assertions ([#409](https://github.com/dallay/profiletailors.com/issues/409)) ([6591385](https://github.com/dallay/profiletailors.com/commit/659138505445529b5d9e8271b367feba065165ba))
* **identity:** rename UNVERIFIED→PENDING, add BOUNCED to EmailStatus enum ([fcff606](https://github.com/dallay/profiletailors.com/commit/fcff6068d89fb621b96a007cf4ef9cc0567564ee))
* **identity:** wrap user registration in reactive transaction to prevent partial writes ([#205](https://github.com/dallay/profiletailors.com/issues/205)) ([bfe026e](https://github.com/dallay/profiletailors.com/commit/bfe026e092fac14366971376735079bce7e8ff76))
* **lead-capture:** wire, secure, and default-off shared waitlist limiter ([#379](https://github.com/dallay/profiletailors.com/issues/379)) ([4fe5868](https://github.com/dallay/profiletailors.com/commit/4fe58681f650c94315b439d13e9d49a793a743dd))
* **media:** partial UNIQUE on workspace_id + file_hash for active assets to prevent duplicate images ([#198](https://github.com/dallay/profiletailors.com/issues/198)) ([53f42d7](https://github.com/dallay/profiletailors.com/commit/53f42d75183043b355b33d33cc80e27833a928c0))
* **media:** reject unknown signatures once header is complete ([1a1cbd3](https://github.com/dallay/profiletailors.com/commit/1a1cbd3cc4b53157161a6f4e3553a849c6030d6a))
* **media:** soft delete violates storage invariant constraint; fix filter re-subscription context ([#418](https://github.com/dallay/profiletailors.com/issues/418)) ([80f8bbd](https://github.com/dallay/profiletailors.com/commit/80f8bbde53c689c9433a1cc0960c60a6b077fc2c))
* **media:** transactional boundaries, compensation semantics, and CI alignment ([#229](https://github.com/dallay/profiletailors.com/issues/229)) ([405d8bc](https://github.com/dallay/profiletailors.com/commit/405d8bcd0bebe6d25d76aa4603166a9c326cd875))
* **modularity:** resolve authorization-to-audit boundary violation ([#291](https://github.com/dallay/profiletailors.com/issues/291)) ([4da19b6](https://github.com/dallay/profiletailors.com/commit/4da19b6f06e60c53f38ed176faa418ad8bd32062))
* **publishing:** address PR review — add notification rollback test + kill-servers ([27629ef](https://github.com/dallay/profiletailors.com/commit/27629eff8748c402b1c986d791f9f6f301ed215f))
* **publishing:** fail fast if PUBLISHING_CREDENTIALS_KEY is not set ([#188](https://github.com/dallay/profiletailors.com/issues/188)) ([7779fc0](https://github.com/dallay/profiletailors.com/commit/7779fc04c3e3e64bb080fe402ce83997c16125ed))
* **publishing:** make worker database writes atomic ([c9914b5](https://github.com/dallay/profiletailors.com/commit/c9914b588a23de97796cc0dbafc9a34810542be9))
* **publishing:** remove hardcoded encryption fallback key and fail fast when env is unset ([#245](https://github.com/dallay/profiletailors.com/issues/245)) ([0f1b142](https://github.com/dallay/profiletailors.com/commit/0f1b142cd5b246a225947a6317e676d892ed2acb))
* **publishing:** route attachments through a single shared binding (and unblock Postgres test suite) ([#277](https://github.com/dallay/profiletailors.com/issues/277)) ([a8382ad](https://github.com/dallay/profiletailors.com/commit/a8382adc1db7788e772ab827083ce9beb1ce65ef))
* **publishing:** tri-state assetIds PATCH with edit hydration ([#223](https://github.com/dallay/profiletailors.com/issues/223)) ([2961147](https://github.com/dallay/profiletailors.com/commit/2961147b150423a48e8735997f9a79164d392fda))
* **publishing:** workspace-scoped insertOrUpdate and 404 for update misses ([#224](https://github.com/dallay/profiletailors.com/issues/224) [#225](https://github.com/dallay/profiletailors.com/issues/225)) ([c1b67d9](https://github.com/dallay/profiletailors.com/commit/c1b67d9df05591cc6512ab5c1f953775220e9cad))
* **quality:** align coverage reporting ([#116](https://github.com/dallay/profiletailors.com/issues/116)) ([62fed0f](https://github.com/dallay/profiletailors.com/commit/62fed0f78229de456186d08632d3b68ccb8c6a48))
* **quality:** resolve all SonarQube violations to pass quality gate ([#339](https://github.com/dallay/profiletailors.com/issues/339)) ([50bbf80](https://github.com/dallay/profiletailors.com/commit/50bbf80711f28f3d2eb5f80dddb5e5355291c875))
* resolve 19 SonarQube code smells and align Codecov exclusions ([#159](https://github.com/dallay/profiletailors.com/issues/159)) ([a8abfad](https://github.com/dallay/profiletailors.com/commit/a8abfad3e753c517956d875f00289198b8326779))
* resolve all Detekt findings (15 issues across 8 files) ([e2e27e0](https://github.com/dallay/profiletailors.com/commit/e2e27e0d6840c866d6ff07a83156a0c9d0aa292f))
* resolve all SonarCloud quality gate issues ([#98](https://github.com/dallay/profiletailors.com/issues/98)) ([68aaf63](https://github.com/dallay/profiletailors.com/commit/68aaf63a71e0b57d115e1bf6d1386b5070b83dd8))
* resolve and retire brokenOnH2 backend tests ([#84](https://github.com/dallay/profiletailors.com/issues/84)) ([b62f176](https://github.com/dallay/profiletailors.com/commit/b62f1768d8860e19fe180acb5f68b562deb185aa))
* resolve SonarQube violations across backend and CI/CD pipelines ([#94](https://github.com/dallay/profiletailors.com/issues/94)) ([e6993f7](https://github.com/dallay/profiletailors.com/commit/e6993f7fc9f15da763be869d401a86f1e5321e5e))
* **scheduler:** prevent scheduling publications in the past with 5-minute grace period ([#93](https://github.com/dallay/profiletailors.com/issues/93)) ([d7d866f](https://github.com/dallay/profiletailors.com/commit/d7d866f1ffd8444b9d70dc7935ff86a67eb79741))
* **security:** harden auth and media signing controls ([#341](https://github.com/dallay/profiletailors.com/issues/341)) ([20adb23](https://github.com/dallay/profiletailors.com/commit/20adb239a6c8a9ebc21e04fb688970bcedcd4497))
* **security:** resolve 3 open code scanning alerts ([#187](https://github.com/dallay/profiletailors.com/issues/187)) ([1a9c580](https://github.com/dallay/profiletailors.com/commit/1a9c5809c616173a8afcde9a5256ac4b220eea0f))
* shell injection in setup-frontend GitHub Action — use env var ([c2b1758](https://github.com/dallay/profiletailors.com/commit/c2b17588b75c030bdcdf88e4e059368203deb2a5))
* shell injection in setup-frontend GitHub Action — use env var ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* shell injection in setup-frontend GitHub Action — use env var ([a8abfad](https://github.com/dallay/profiletailors.com/commit/a8abfad3e753c517956d875f00289198b8326779))
* shell injection in setup-frontend GitHub Action — use env var ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))
* shell injection in setup-frontend GitHub Action — use env var ([ae84056](https://github.com/dallay/profiletailors.com/commit/ae8405615b105b9a0443f7262b130205251f4feb))
* **smp:** add custom @Service annotation to component scan includeFilters ([73c6e1f](https://github.com/dallay/profiletailors.com/commit/73c6e1f7013308cbebbd1601dbc8563af764d9b4))
* **sonar:** resolve SonarQube Quality Gate issues ([#411](https://github.com/dallay/profiletailors.com/issues/411)) ([2ddc7df](https://github.com/dallay/profiletailors.com/commit/2ddc7df2fe7a7611a7bc92cc8969d05332c76223))
* **tenancy:** eliminate TOCTOU race in workspace ownership transfer ([#108](https://github.com/dallay/profiletailors.com/issues/108)) ([d6660c7](https://github.com/dallay/profiletailors.com/commit/d6660c718c728599b2abdc7ff2678e7135541b3e))
* **tenancy:** improve exception handling and remove code duplication ([e95499e](https://github.com/dallay/profiletailors.com/commit/e95499eadfec4074a6288b772e6746b1762b3a07))
* **tenancy:** wrap removeIfReplacementExists lock+delete in a reactive transaction ([#279](https://github.com/dallay/profiletailors.com/issues/279)) ([5f1c1ba](https://github.com/dallay/profiletailors.com/commit/5f1c1ba9021534941bcfb6497d891240d6836fbd))
* **test:** add local_password_credentials to cleanup and remove unused Tag imports ([#107](https://github.com/dallay/profiletailors.com/issues/107)) ([ac7a777](https://github.com/dallay/profiletailors.com/commit/ac7a77700a104ccfa0b20afcd40e3343f048a399)), closes [#83](https://github.com/dallay/profiletailors.com/issues/83)
* unsplash image flow ([#329](https://github.com/dallay/profiletailors.com/issues/329)) ([e46ef4f](https://github.com/dallay/profiletailors.com/commit/e46ef4f723f9a7ed428042ab6cb85e16ffc26180))
* **ux:** prevent week grid overflow and add toast feedback on post creation ([#280](https://github.com/dallay/profiletailors.com/issues/280)) ([6146bf4](https://github.com/dallay/profiletailors.com/commit/6146bf425e4cbfefd53c4e1468a7fb726498508c))


### Refactoring

* **infra:** unify db credentials under SMP_DB_* vars ([#390](https://github.com/dallay/profiletailors.com/issues/390)) ([d20d5de](https://github.com/dallay/profiletailors.com/commit/d20d5de77180ada59f1d6b4bc7d5e5df2d7068c8))
* move AuditHook interface to domain package and update imports ([5ffb673](https://github.com/dallay/profiletailors.com/commit/5ffb67342de65d9dbf1c2f4127fd682bc2c73819))
* move AuditHook interface to domain package and update imports ([69e4648](https://github.com/dallay/profiletailors.com/commit/69e46483591e6ad40a5b9429e11850f24aca1f00))
* Remove unsplashProviderEnabled feature flag — enable Unsplash permanently ([#294](https://github.com/dallay/profiletailors.com/issues/294)) ([16b2e9a](https://github.com/dallay/profiletailors.com/commit/16b2e9a04b1af42120d0a9508f6bf3fcf776c747))
* reorganize domain packages and introduce common context types ([#24](https://github.com/dallay/profiletailors.com/issues/24)) ([f96d26b](https://github.com/dallay/profiletailors.com/commit/f96d26b07586238ec2808bb52ad739dd9c90b191))
* replace package-info.java with Kotlin ModuleMetadata ([#25](https://github.com/dallay/profiletailors.com/issues/25)) ([20cca54](https://github.com/dallay/profiletailors.com/commit/20cca5419de7d5c44e90c20f73d9129c74b75cb1))
* separate authorization domain interfaces and clean up unused code ([#18](https://github.com/dallay/profiletailors.com/issues/18)) ([9171a17](https://github.com/dallay/profiletailors.com/commit/9171a177f59949fd86611cd3f39dd94ae1f933ba))
* **smp:** drop restrictive component scan filter and normalize bean annotations ([64074da](https://github.com/dallay/profiletailors.com/commit/64074da4c2d54ea30c37097227cbcd9ae7059b0a))
* **test:** extract shared test infrastructure to reduce duplication ([#17](https://github.com/dallay/profiletailors.com/issues/17)) ([503a0d7](https://github.com/dallay/profiletailors.com/commit/503a0d7f0233eadff38dcf898dd42f65d133785f))
* **web:** restructure sidebar into modular components ([#30](https://github.com/dallay/profiletailors.com/issues/30)) ([aa51b4e](https://github.com/dallay/profiletailors.com/commit/aa51b4ec39bdf2f3936038ac4b09e87185997673))


### Documentation

* reconcile documentation with current implementation ([#393](https://github.com/dallay/profiletailors.com/issues/393)) ([9946d90](https://github.com/dallay/profiletailors.com/commit/9946d90323785d392312be9447baab217bdf6edd))
* reconcile documentation with current implementation state ([#244](https://github.com/dallay/profiletailors.com/issues/244)) ([08c0804](https://github.com/dallay/profiletailors.com/commit/08c0804f18235a3b98d4616c7564a2fea7760243))

## [0.2.0](https://github.com/dallay/profiletailors.com/compare/smp@v0.1.0...smp@v0.2.0) (2026-07-21)


### ⚠ BREAKING CHANGES

* **tests:** RealLinkedInAssetUploader.storage and RealLinkedInPublisher.storage are now Storage? (nullable). The !! call in buildAssetContentEntities is safe because that path is only reached in real mode where storage is always configured.
* **publishing:** RealLinkedInAssetUploader.storage and RealLinkedInPublisher.storage are now Storage? (nullable). The !! call in buildAssetContentEntities is safe because that path is only reached in real mode where storage is always configured.

### Features

* accessibility audit fixes, focus trap composable, and publishing backend improvements ([#139](https://github.com/dallay/profiletailors.com/issues/139)) ([67c9444](https://github.com/dallay/profiletailors.com/commit/67c94447cdc224652788cdc355268fb4ab63f4c0))
* Add email verification status to user profile and infrastructure ([d6d6e7b](https://github.com/dallay/profiletailors.com/commit/d6d6e7b43e9663eb76ee418ce030a889c3295c85))
* add LinkedIn channel avatar support with backend persistence and frontend fallback ([743ae47](https://github.com/dallay/profiletailors.com/commit/743ae47dbf8ff621d4a4cef222dae44155a3e1bb))
* Add workspace icon support (Lucide icons, phase 1) ([#81](https://github.com/dallay/profiletailors.com/issues/81)) ([09ed42f](https://github.com/dallay/profiletailors.com/commit/09ed42fb77d4abe522a800645bd94b204a08f93c))
* **auth:** harden local auth, stabilize register-flow E2E, add coverage ([#111](https://github.com/dallay/profiletailors.com/issues/111)) ([7098431](https://github.com/dallay/profiletailors.com/commit/709843133719a84407ab93355a600d86ad4a9f23))
* **auth:** implement session refresh with HttpOnly cookie and in-memory access token ([#27](https://github.com/dallay/profiletailors.com/issues/27)) ([6ae3db0](https://github.com/dallay/profiletailors.com/commit/6ae3db00d1b8565e610ee4daf5005ab5543ab7fc))
* **auth:** migrate email/password hashing from BCrypt to Argon2id ([#250](https://github.com/dallay/profiletailors.com/issues/250)) ([07bf036](https://github.com/dallay/profiletailors.com/commit/07bf036e8f2481ca0d950c1bcb01fc1e9b150b49))
* **authorization:** add workspace target scopes and resource preview ([#5](https://github.com/dallay/profiletailors.com/issues/5)) ([69b7086](https://github.com/dallay/profiletailors.com/commit/69b708613525d4a186e564424b672fe6adc3c51c))
* backend env loading, tenancy workspace, frontend dashboard, security fixes ([#78](https://github.com/dallay/profiletailors.com/issues/78)) ([7be0b4b](https://github.com/dallay/profiletailors.com/commit/7be0b4b48d97db515486574b150608446bed7b2e))
* backend feature entitlements, credential lifecycle, and DevSecOps hardening ([8f94cd5](https://github.com/dallay/profiletailors.com/commit/8f94cd5022d2130ec1e8a18bea03d0be4d5858a3))
* backend feature entitlements, credential lifecycle, and DevSecOps hardening ([76d3ad3](https://github.com/dallay/profiletailors.com/commit/76d3ad3ded0fa2b1c8cbbc6ebb2468c5e57ca428))
* **bazel:** add native Bazel compilation targets for backend ([#3](https://github.com/dallay/profiletailors.com/issues/3)) ([040dfa8](https://github.com/dallay/profiletailors.com/commit/040dfa8847c2f934bdaeaf46177166f6f89f0a7b))
* **calendar:** add publication click event and improve accessibility in month view ([#145](https://github.com/dallay/profiletailors.com/issues/145)) ([b6f1d93](https://github.com/dallay/profiletailors.com/commit/b6f1d93ccbbcadcb03ade6a06f5e1aad8ee0b966))
* **compliance:** Legal & Compliance Foundation — docs, ADR-0012, and validation tooling ([#368](https://github.com/dallay/profiletailors.com/issues/368)) ([f0b5ea3](https://github.com/dallay/profiletailors.com/commit/f0b5ea3c5d8d40dfc655e4ee76b0e74865b6bd70))
* connect Vue SPA channels to Spring Boot LinkedIn backend ([#75](https://github.com/dallay/profiletailors.com/issues/75)) ([64e6ccc](https://github.com/dallay/profiletailors.com/commit/64e6ccc65c987147c170cd335f974406953dd123))
* **coverage:** update Codecov configuration and align with SonarQube standards ([1d10e88](https://github.com/dallay/profiletailors.com/commit/1d10e8862c03d93eab25021deca74a1df723482e))
* **detekt:** add baseline configuration ([35f0c87](https://github.com/dallay/profiletailors.com/commit/35f0c87be4a4fac30f7fbdbd154cedd5009c08ea))
* **domain:** introduce core domain interfaces for command, query, and notification handling ([7e02325](https://github.com/dallay/profiletailors.com/commit/7e02325301206f72c64519ddac156a09ec20b1e3))
* **email:** add Resend adapter for transactional email delivery ([9589cb6](https://github.com/dallay/profiletailors.com/commit/9589cb60386fac956348ac838d751868692736c4))
* **email:** add Resend adapter for transactional email delivery ([866cb2b](https://github.com/dallay/profiletailors.com/commit/866cb2b77b56fae1509c2c65cb155e625c782a9c)), closes [#104](https://github.com/dallay/profiletailors.com/issues/104)
* **email:** add styled HTML verification email with plain-text fallback and env-aware URLs ([#220](https://github.com/dallay/profiletailors.com/issues/220)) ([1dda267](https://github.com/dallay/profiletailors.com/commit/1dda2672bf5757e2be01332490612f2ec018b844))
* **email:** configure Mailpit SMTP for dev and document production setup ([41d63b6](https://github.com/dallay/profiletailors.com/commit/41d63b680a223db28a4660384faa9c6d3aed3ee9)), closes [#103](https://github.com/dallay/profiletailors.com/issues/103)
* **email:** configure Mailpit SMTP for dev and document production setup ([#105](https://github.com/dallay/profiletailors.com/issues/105)) ([5422ffa](https://github.com/dallay/profiletailors.com/commit/5422ffa22c6e1fad853e4408208319872f58f74f))
* enable Docker Compose in application configuration ([3b57ee0](https://github.com/dallay/profiletailors.com/commit/3b57ee0fd2a6337bd07cf9d3813ccdb296bc74e4))
* expand app shell, API versioning docs, and backend test coverage ([#29](https://github.com/dallay/profiletailors.com/issues/29)) ([2318f50](https://github.com/dallay/profiletailors.com/commit/2318f50fee6404116bc195da6552d47ac43b559b))
* **governance:** add audit events API with cursor pagination ([ded1f4d](https://github.com/dallay/profiletailors.com/commit/ded1f4dc769c469b900490076d8b65f7bf433d41))
* **governance:** add DMCA takedown report UI and review dashboard (DALLAY-499 Phase 2) ([#417](https://github.com/dallay/profiletailors.com/issues/417)) ([0c3bd68](https://github.com/dallay/profiletailors.com/commit/0c3bd68afa86bd29d6b3150e195f8fa09abb7a5f))
* **governance:** add versioned consent records with CQRS handlers and waitlist integration ([#383](https://github.com/dallay/profiletailors.com/issues/383)) ([24ed347](https://github.com/dallay/profiletailors.com/commit/24ed3470e4bd49911fc9b64cea022c73d596a235))
* **identity:** add email verification flow with token-based verify and resend ([#85](https://github.com/dallay/profiletailors.com/issues/85)) ([b5a9644](https://github.com/dallay/profiletailors.com/commit/b5a9644bc7ecaee2f3d523019074891fb5385e1a))
* **identity:** add registration control gate and public capabilities API ([#395](https://github.com/dallay/profiletailors.com/issues/395)) ([e2dc7d9](https://github.com/dallay/profiletailors.com/commit/e2dc7d91aca3b8a1cb26dcfe38d6530d2a0d4fe2))
* **identity:** enforce age eligibility and terms acceptance during registration ([#387](https://github.com/dallay/profiletailors.com/issues/387)) ([2da26fb](https://github.com/dallay/profiletailors.com/commit/2da26fb9ee67a86e7542b2bf08e7c46072d358b3))
* **infra:** add production container deployments ([#388](https://github.com/dallay/profiletailors.com/issues/388)) ([cac929a](https://github.com/dallay/profiletailors.com/commit/cac929a98140ecd434d6fdb04758e8164f616afc))
* initialize project structure with core modules and configuration files ([30234c4](https://github.com/dallay/profiletailors.com/commit/30234c415d50aa43981017e62dc708a93a60b0de))
* integrate Detekt static analysis ([9c871be](https://github.com/dallay/profiletailors.com/commit/9c871bec2e4b8d39c373ec6ba626b3575eba7648))
* **lead-capture:** add waitlist persistence adapters ([#344](https://github.com/dallay/profiletailors.com/issues/344)) ([03a49b5](https://github.com/dallay/profiletailors.com/commit/03a49b50ec4b9797f739b1056d4a5bb80e82a3cd))
* **lead-capture:** expose POST waitlist join endpoint (DALLAY-439) ([#367](https://github.com/dallay/profiletailors.com/issues/367)) ([6c99039](https://github.com/dallay/profiletailors.com/commit/6c99039b48b1d4d1caaa2907cd3df79ee87befc6))
* **lead-capture:** rate limit waitlist joins ([#378](https://github.com/dallay/profiletailors.com/issues/378)) ([19793d2](https://github.com/dallay/profiletailors.com/commit/19793d2d10c52ccdcbfb11de3940094ed136fb14))
* **legal:** consolidate legal domain features — consent API, DSAR, compliance, identity, and policy ([#407](https://github.com/dallay/profiletailors.com/issues/407)) ([87ff2d9](https://github.com/dallay/profiletailors.com/commit/87ff2d9d07c75ab0edc7521fa131b7692813f0de))
* **media,ci:** workspace-level CAS media dedup + Postgres Testcontainers + detekt v2 + Biome root (size-exception) ([#174](https://github.com/dallay/profiletailors.com/issues/174)) ([baac461](https://github.com/dallay/profiletailors.com/commit/baac461c2e933991f9cbfa8c2493465cea512bbf))
* **media:** add centralized media library MVP ([#110](https://github.com/dallay/profiletailors.com/issues/110)) ([8f07e25](https://github.com/dallay/profiletailors.com/commit/8f07e25cf5ff00cb6959073e39b29dbd039b94b2))
* **media:** add licence schema and attribution display (DALLAY-499 Phase 1) ([#412](https://github.com/dallay/profiletailors.com/issues/412)) ([f53243b](https://github.com/dallay/profiletailors.com/commit/f53243be5e6ec30da5dccc901305cfb45a8465bc))
* **media:** add local auth and media library workflows ([#120](https://github.com/dallay/profiletailors.com/issues/120)) ([6e70486](https://github.com/dallay/profiletailors.com/commit/6e704868f3d1003d58973164f6fcbc0502207951))
* **media:** external asset metadata schema and H2 elimination ([#240](https://github.com/dallay/profiletailors.com/issues/240)) ([b432323](https://github.com/dallay/profiletailors.com/commit/b432323143548da8e1cb4bd70c6749622a1cf5bb))
* **media:** integrate Unsplash as first media provider ([#249](https://github.com/dallay/profiletailors.com/issues/249)) ([d06f833](https://github.com/dallay/profiletailors.com/commit/d06f83391eb2cee0f38263a1a1f99c31f8927a61))
* notifications module + publishing refactor + privacy DTO typing ([#410](https://github.com/dallay/profiletailors.com/issues/410)) ([0a1dbdc](https://github.com/dallay/profiletailors.com/commit/0a1dbdcc5c716d0c8d71a3718b66945018885360))
* **privacy:** implement DSAR workflows for GDPR/CCPA compliance ([#392](https://github.com/dallay/profiletailors.com/issues/392)) ([9c46ac1](https://github.com/dallay/profiletailors.com/commit/9c46ac1d458b2b699921e1249d1181b9aa1d6eb9))
* **publishing:** add failure visibility and retry recovery for MVP core publishing ([#303](https://github.com/dallay/profiletailors.com/issues/303)) ([96efe6a](https://github.com/dallay/profiletailors.com/commit/96efe6a3bab94fe0838e651a3bdedf9827eb1fdb))
* **publishing:** add LinkedIn media upload support ([#51](https://github.com/dallay/profiletailors.com/issues/51)) ([9ea9869](https://github.com/dallay/profiletailors.com/commit/9ea986960e5801edc35d89ccdd3ec5cbc1f7f262))
* **publishing:** add LinkedIn Publishing MVP with workspace-scoped social media management ([#39](https://github.com/dallay/profiletailors.com/issues/39)) ([3e17d7e](https://github.com/dallay/profiletailors.com/commit/3e17d7ea4e548471dfa0c911b88edf95d5c2cc06))
* **publishing:** complete backend-integrated edit/delete for unpublished posts ([#131](https://github.com/dallay/profiletailors.com/issues/131)) ([#143](https://github.com/dallay/profiletailors.com/issues/143)) ([60bc76e](https://github.com/dallay/profiletailors.com/commit/60bc76e5070220036a8bb0508a401a3bd0441371))
* **publishing:** improve user-facing error messages for failed post publishing ([#335](https://github.com/dallay/profiletailors.com/issues/335)) ([dbd289a](https://github.com/dallay/profiletailors.com/commit/dbd289acfe793b806a5611a5403a9b1a5cedc52d))
* **publishing:** LinkedIn integration publication ([#99](https://github.com/dallay/profiletailors.com/issues/99)) ([5f15d0a](https://github.com/dallay/profiletailors.com/commit/5f15d0a0c9b2e81b4cfc7ab9eddd5d632a756b0c))
* **publishing:** real delete endpoint + persisted edit for unpublished posts ([#131](https://github.com/dallay/profiletailors.com/issues/131)) ([#138](https://github.com/dallay/profiletailors.com/issues/138)) ([ed06c17](https://github.com/dallay/profiletailors.com/commit/ed06c17e013d5dee68d2c47188b01a8d496a4d56))
* **publishing:** structured worker lifecycle logs ([#338](https://github.com/dallay/profiletailors.com/issues/338)) ([e533a48](https://github.com/dallay/profiletailors.com/commit/e533a482b8a7195d46b95db6e6b376c5690d5e7e))
* refactor App shell into 10 focused components and add image proxy API ([#88](https://github.com/dallay/profiletailors.com/issues/88)) ([62d313e](https://github.com/dallay/profiletailors.com/commit/62d313ed6fede794e6b1f3e35675a0a69d8a8501))
* refactor post scheduling to single-account selection and fix credential resolution path ([#86](https://github.com/dallay/profiletailors.com/issues/86)) ([961998d](https://github.com/dallay/profiletailors.com/commit/961998df27eec2984c4a9924f266f5c164949915))
* remove username field from registration flow ([#71](https://github.com/dallay/profiletailors.com/issues/71)) ([1d6640c](https://github.com/dallay/profiletailors.com/commit/1d6640cbd0d436574d311469ffd2bdc89b549003))
* **scheduler:** E2E tests, CI workflow, and full-stack integration ([#101](https://github.com/dallay/profiletailors.com/issues/101)) ([12fd5a8](https://github.com/dallay/profiletailors.com/commit/12fd5a82fe07855758c8e549bd7c4f9a64c12977))
* **scheduler:** standardize URL state and deep-linkable post details ([#292](https://github.com/dallay/profiletailors.com/issues/292)) ([7c3d63a](https://github.com/dallay/profiletailors.com/commit/7c3d63ae988eff4debaf6a1f3bcc0436b05bd2d0))
* **security:** add production secrets docs and startup credentials validator ([#274](https://github.com/dallay/profiletailors.com/issues/274)) ([5825ea7](https://github.com/dallay/profiletailors.com/commit/5825ea7f98c087e73ebb88be4d2ea8a7d13ad07e))
* **smp:** add calendar repository queries, conflict detection policy, and activity thresholds ([#66](https://github.com/dallay/profiletailors.com/issues/66)) ([6203302](https://github.com/dallay/profiletailors.com/commit/6203302c476e77659bf8f22b048e6ee0753186b4))
* **smp:** add Detekt plugin and integrate with check ([476311a](https://github.com/dallay/profiletailors.com/commit/476311a0f90da985061e873b93ff9e5c8d0be724))
* **smp:** add direct grants persistence, service-account auth, and API key support ([edd5b34](https://github.com/dallay/profiletailors.com/commit/edd5b34c8d19de1ad65ac3edbcb359b44f06a464))
* **storage:** add R2 dedicated storage adapter with credentials wiring ([#65](https://github.com/dallay/profiletailors.com/issues/65)) ([a58e049](https://github.com/dallay/profiletailors.com/commit/a58e049fcc0acb56920c08632cc4ddb8bd06c8f9))
* **tenancy:** add multi-owner workspace ownership with audit ([99f6c80](https://github.com/dallay/profiletailors.com/commit/99f6c802198991d094427e4e4de47cc723285d9e))
* **tenancy:** implement concrete R2DBC repositories and missing owner removal handler ([#271](https://github.com/dallay/profiletailors.com/issues/271)) ([5100bdd](https://github.com/dallay/profiletailors.com/commit/5100bdda8bba28cac67b266fca4962956bb5d552))
* **tests:** bootstrap Vitest for Vue app + backend publishing test coverage ([#64](https://github.com/dallay/profiletailors.com/issues/64)) ([5c716e7](https://github.com/dallay/profiletailors.com/commit/5c716e750722e50b9d7b679b675adfd537f64b5b))
* **transactions:** standardize reactive transaction strategy (closes [#195](https://github.com/dallay/profiletailors.com/issues/195)) ([#230](https://github.com/dallay/profiletailors.com/issues/230)) ([3e99e11](https://github.com/dallay/profiletailors.com/commit/3e99e117288130cc12e36596902fde5290e0cf2b))
* **verification:** add BDD scenarios and E2E tests for email verification media gate ([#216](https://github.com/dallay/profiletailors.com/issues/216)) ([fda809a](https://github.com/dallay/profiletailors.com/commit/fda809aec4d483ebb3cde0c5e9b6031318d71255))
* wrap multi-write operations in transactional boundaries for 3 handlers ([#227](https://github.com/dallay/profiletailors.com/issues/227)) ([af2c8dd](https://github.com/dallay/profiletailors.com/commit/af2c8dd0f4930c850fc2755f02afa66a0920b9fd))


### Bug Fixes

* 13 code-review findings across apps/web/app and openspec/specs ([#156](https://github.com/dallay/profiletailors.com/issues/156)) ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))
* address CodeRabbit review findings for App shell + image proxy ([#90](https://github.com/dallay/profiletailors.com/issues/90)) ([d418d3d](https://github.com/dallay/profiletailors.com/commit/d418d3dc06b88420e0c78d09949245bce7a97c19))
* **architecture:** enforce hexagonal boundaries ([#115](https://github.com/dallay/profiletailors.com/issues/115)) ([94636b0](https://github.com/dallay/profiletailors.com/commit/94636b0a97f58a377d79803db814b31ce4fc3584))
* **auth:** assign WORKSPACE_OWNER role on provisioning, fix settings UX audit, and fix dialog transparency ([#91](https://github.com/dallay/profiletailors.com/issues/91)) ([5b337a0](https://github.com/dallay/profiletailors.com/commit/5b337a084934f382e0170bf4a3a9db06414a7a4c))
* **bdd:** enable email verification in seed and fix publishing BDD assertions ([#416](https://github.com/dallay/profiletailors.com/issues/416)) ([12bd603](https://github.com/dallay/profiletailors.com/commit/12bd603ca5c6f8f004ffedaedee49260c3771918))
* **ci:** resolve semgrep exit code, pnpm lockfile, gitleaks org license, and Java toolchain ([4ec374d](https://github.com/dallay/profiletailors.com/commit/4ec374db241f3c9d84dd1bde04bbd2c1fe1aa120))
* clear delivery attempts when replacing publication jobs ([#326](https://github.com/dallay/profiletailors.com/issues/326)) ([ffad235](https://github.com/dallay/profiletailors.com/commit/ffad235498b1a7aea3712c322cc52935153a8592))
* **common:** remove @Component from custom @Service to keep shared/common Spring-free ([9e48911](https://github.com/dallay/profiletailors.com/commit/9e489112daf71ae00561df7ec4aa1397a50f5834))
* **deps:** address ~74 Dependabot alerts - Spring Boot, Jackson, Netty, Astro, vite, undici, hono ([#162](https://github.com/dallay/profiletailors.com/issues/162)) ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* **devops:** move backend HTTP port to 7638 (SMP_BACKEND_PORT) ([3abccd7](https://github.com/dallay/profiletailors.com/commit/3abccd78fdcb6b7a89278ab336d6d4a8fc1823a7))
* **dev:** set email_status to VERIFIED for dev seed user ([c699723](https://github.com/dallay/profiletailors.com/commit/c699723337077cc9a00630aedb87b439067ecf61))
* **email:** guard Resend adapter against empty api-key ([a83a613](https://github.com/dallay/profiletailors.com/commit/a83a613c7611579f0dd9fa1f7737a551d48eb945))
* **identity:** disable auth rate limiter in tests and fix BDD registration assertions ([#409](https://github.com/dallay/profiletailors.com/issues/409)) ([6591385](https://github.com/dallay/profiletailors.com/commit/659138505445529b5d9e8271b367feba065165ba))
* **identity:** rename UNVERIFIED→PENDING, add BOUNCED to EmailStatus enum ([fcff606](https://github.com/dallay/profiletailors.com/commit/fcff6068d89fb621b96a007cf4ef9cc0567564ee))
* **identity:** wrap user registration in reactive transaction to prevent partial writes ([#205](https://github.com/dallay/profiletailors.com/issues/205)) ([bfe026e](https://github.com/dallay/profiletailors.com/commit/bfe026e092fac14366971376735079bce7e8ff76))
* **lead-capture:** wire, secure, and default-off shared waitlist limiter ([#379](https://github.com/dallay/profiletailors.com/issues/379)) ([4fe5868](https://github.com/dallay/profiletailors.com/commit/4fe58681f650c94315b439d13e9d49a793a743dd))
* **media:** partial UNIQUE on workspace_id + file_hash for active assets to prevent duplicate images ([#198](https://github.com/dallay/profiletailors.com/issues/198)) ([53f42d7](https://github.com/dallay/profiletailors.com/commit/53f42d75183043b355b33d33cc80e27833a928c0))
* **media:** reject unknown signatures once header is complete ([1a1cbd3](https://github.com/dallay/profiletailors.com/commit/1a1cbd3cc4b53157161a6f4e3553a849c6030d6a))
* **media:** soft delete violates storage invariant constraint; fix filter re-subscription context ([#418](https://github.com/dallay/profiletailors.com/issues/418)) ([80f8bbd](https://github.com/dallay/profiletailors.com/commit/80f8bbde53c689c9433a1cc0960c60a6b077fc2c))
* **media:** transactional boundaries, compensation semantics, and CI alignment ([#229](https://github.com/dallay/profiletailors.com/issues/229)) ([405d8bc](https://github.com/dallay/profiletailors.com/commit/405d8bcd0bebe6d25d76aa4603166a9c326cd875))
* **modularity:** resolve authorization-to-audit boundary violation ([#291](https://github.com/dallay/profiletailors.com/issues/291)) ([4da19b6](https://github.com/dallay/profiletailors.com/commit/4da19b6f06e60c53f38ed176faa418ad8bd32062))
* **publishing:** address PR review — add notification rollback test + kill-servers ([27629ef](https://github.com/dallay/profiletailors.com/commit/27629eff8748c402b1c986d791f9f6f301ed215f))
* **publishing:** fail fast if PUBLISHING_CREDENTIALS_KEY is not set ([#188](https://github.com/dallay/profiletailors.com/issues/188)) ([7779fc0](https://github.com/dallay/profiletailors.com/commit/7779fc04c3e3e64bb080fe402ce83997c16125ed))
* **publishing:** make worker database writes atomic ([c9914b5](https://github.com/dallay/profiletailors.com/commit/c9914b588a23de97796cc0dbafc9a34810542be9))
* **publishing:** remove hardcoded encryption fallback key and fail fast when env is unset ([#245](https://github.com/dallay/profiletailors.com/issues/245)) ([0f1b142](https://github.com/dallay/profiletailors.com/commit/0f1b142cd5b246a225947a6317e676d892ed2acb))
* **publishing:** route attachments through a single shared binding (and unblock Postgres test suite) ([#277](https://github.com/dallay/profiletailors.com/issues/277)) ([a8382ad](https://github.com/dallay/profiletailors.com/commit/a8382adc1db7788e772ab827083ce9beb1ce65ef))
* **publishing:** tri-state assetIds PATCH with edit hydration ([#223](https://github.com/dallay/profiletailors.com/issues/223)) ([2961147](https://github.com/dallay/profiletailors.com/commit/2961147b150423a48e8735997f9a79164d392fda))
* **publishing:** workspace-scoped insertOrUpdate and 404 for update misses ([#224](https://github.com/dallay/profiletailors.com/issues/224) [#225](https://github.com/dallay/profiletailors.com/issues/225)) ([c1b67d9](https://github.com/dallay/profiletailors.com/commit/c1b67d9df05591cc6512ab5c1f953775220e9cad))
* **quality:** align coverage reporting ([#116](https://github.com/dallay/profiletailors.com/issues/116)) ([62fed0f](https://github.com/dallay/profiletailors.com/commit/62fed0f78229de456186d08632d3b68ccb8c6a48))
* **quality:** resolve all SonarQube violations to pass quality gate ([#339](https://github.com/dallay/profiletailors.com/issues/339)) ([50bbf80](https://github.com/dallay/profiletailors.com/commit/50bbf80711f28f3d2eb5f80dddb5e5355291c875))
* resolve 19 SonarQube code smells and align Codecov exclusions ([#159](https://github.com/dallay/profiletailors.com/issues/159)) ([a8abfad](https://github.com/dallay/profiletailors.com/commit/a8abfad3e753c517956d875f00289198b8326779))
* resolve all Detekt findings (15 issues across 8 files) ([e2e27e0](https://github.com/dallay/profiletailors.com/commit/e2e27e0d6840c866d6ff07a83156a0c9d0aa292f))
* resolve all SonarCloud quality gate issues ([#98](https://github.com/dallay/profiletailors.com/issues/98)) ([68aaf63](https://github.com/dallay/profiletailors.com/commit/68aaf63a71e0b57d115e1bf6d1386b5070b83dd8))
* resolve and retire brokenOnH2 backend tests ([#84](https://github.com/dallay/profiletailors.com/issues/84)) ([b62f176](https://github.com/dallay/profiletailors.com/commit/b62f1768d8860e19fe180acb5f68b562deb185aa))
* resolve SonarQube violations across backend and CI/CD pipelines ([#94](https://github.com/dallay/profiletailors.com/issues/94)) ([e6993f7](https://github.com/dallay/profiletailors.com/commit/e6993f7fc9f15da763be869d401a86f1e5321e5e))
* **scheduler:** prevent scheduling publications in the past with 5-minute grace period ([#93](https://github.com/dallay/profiletailors.com/issues/93)) ([d7d866f](https://github.com/dallay/profiletailors.com/commit/d7d866f1ffd8444b9d70dc7935ff86a67eb79741))
* **security:** harden auth and media signing controls ([#341](https://github.com/dallay/profiletailors.com/issues/341)) ([20adb23](https://github.com/dallay/profiletailors.com/commit/20adb239a6c8a9ebc21e04fb688970bcedcd4497))
* **security:** resolve 3 open code scanning alerts ([#187](https://github.com/dallay/profiletailors.com/issues/187)) ([1a9c580](https://github.com/dallay/profiletailors.com/commit/1a9c5809c616173a8afcde9a5256ac4b220eea0f))
* shell injection in setup-frontend GitHub Action — use env var ([c2b1758](https://github.com/dallay/profiletailors.com/commit/c2b17588b75c030bdcdf88e4e059368203deb2a5))
* shell injection in setup-frontend GitHub Action — use env var ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* shell injection in setup-frontend GitHub Action — use env var ([a8abfad](https://github.com/dallay/profiletailors.com/commit/a8abfad3e753c517956d875f00289198b8326779))
* shell injection in setup-frontend GitHub Action — use env var ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))
* shell injection in setup-frontend GitHub Action — use env var ([ae84056](https://github.com/dallay/profiletailors.com/commit/ae8405615b105b9a0443f7262b130205251f4feb))
* **smp:** add custom @Service annotation to component scan includeFilters ([73c6e1f](https://github.com/dallay/profiletailors.com/commit/73c6e1f7013308cbebbd1601dbc8563af764d9b4))
* **sonar:** resolve SonarQube Quality Gate issues ([#411](https://github.com/dallay/profiletailors.com/issues/411)) ([2ddc7df](https://github.com/dallay/profiletailors.com/commit/2ddc7df2fe7a7611a7bc92cc8969d05332c76223))
* **tenancy:** eliminate TOCTOU race in workspace ownership transfer ([#108](https://github.com/dallay/profiletailors.com/issues/108)) ([d6660c7](https://github.com/dallay/profiletailors.com/commit/d6660c718c728599b2abdc7ff2678e7135541b3e))
* **tenancy:** improve exception handling and remove code duplication ([e95499e](https://github.com/dallay/profiletailors.com/commit/e95499eadfec4074a6288b772e6746b1762b3a07))
* **tenancy:** wrap removeIfReplacementExists lock+delete in a reactive transaction ([#279](https://github.com/dallay/profiletailors.com/issues/279)) ([5f1c1ba](https://github.com/dallay/profiletailors.com/commit/5f1c1ba9021534941bcfb6497d891240d6836fbd))
* **test:** add local_password_credentials to cleanup and remove unused Tag imports ([#107](https://github.com/dallay/profiletailors.com/issues/107)) ([ac7a777](https://github.com/dallay/profiletailors.com/commit/ac7a77700a104ccfa0b20afcd40e3343f048a399)), closes [#83](https://github.com/dallay/profiletailors.com/issues/83)
* unsplash image flow ([#329](https://github.com/dallay/profiletailors.com/issues/329)) ([e46ef4f](https://github.com/dallay/profiletailors.com/commit/e46ef4f723f9a7ed428042ab6cb85e16ffc26180))
* **ux:** prevent week grid overflow and add toast feedback on post creation ([#280](https://github.com/dallay/profiletailors.com/issues/280)) ([6146bf4](https://github.com/dallay/profiletailors.com/commit/6146bf425e4cbfefd53c4e1468a7fb726498508c))


### Refactoring

* **infra:** unify db credentials under SMP_DB_* vars ([#390](https://github.com/dallay/profiletailors.com/issues/390)) ([d20d5de](https://github.com/dallay/profiletailors.com/commit/d20d5de77180ada59f1d6b4bc7d5e5df2d7068c8))
* move AuditHook interface to domain package and update imports ([5ffb673](https://github.com/dallay/profiletailors.com/commit/5ffb67342de65d9dbf1c2f4127fd682bc2c73819))
* move AuditHook interface to domain package and update imports ([69e4648](https://github.com/dallay/profiletailors.com/commit/69e46483591e6ad40a5b9429e11850f24aca1f00))
* Remove unsplashProviderEnabled feature flag — enable Unsplash permanently ([#294](https://github.com/dallay/profiletailors.com/issues/294)) ([16b2e9a](https://github.com/dallay/profiletailors.com/commit/16b2e9a04b1af42120d0a9508f6bf3fcf776c747))
* reorganize domain packages and introduce common context types ([#24](https://github.com/dallay/profiletailors.com/issues/24)) ([f96d26b](https://github.com/dallay/profiletailors.com/commit/f96d26b07586238ec2808bb52ad739dd9c90b191))
* replace package-info.java with Kotlin ModuleMetadata ([#25](https://github.com/dallay/profiletailors.com/issues/25)) ([20cca54](https://github.com/dallay/profiletailors.com/commit/20cca5419de7d5c44e90c20f73d9129c74b75cb1))
* separate authorization domain interfaces and clean up unused code ([#18](https://github.com/dallay/profiletailors.com/issues/18)) ([9171a17](https://github.com/dallay/profiletailors.com/commit/9171a177f59949fd86611cd3f39dd94ae1f933ba))
* **smp:** drop restrictive component scan filter and normalize bean annotations ([64074da](https://github.com/dallay/profiletailors.com/commit/64074da4c2d54ea30c37097227cbcd9ae7059b0a))
* **test:** extract shared test infrastructure to reduce duplication ([#17](https://github.com/dallay/profiletailors.com/issues/17)) ([503a0d7](https://github.com/dallay/profiletailors.com/commit/503a0d7f0233eadff38dcf898dd42f65d133785f))
* **web:** restructure sidebar into modular components ([#30](https://github.com/dallay/profiletailors.com/issues/30)) ([aa51b4e](https://github.com/dallay/profiletailors.com/commit/aa51b4ec39bdf2f3936038ac4b09e87185997673))


### Documentation

* reconcile documentation with current implementation ([#393](https://github.com/dallay/profiletailors.com/issues/393)) ([9946d90](https://github.com/dallay/profiletailors.com/commit/9946d90323785d392312be9447baab217bdf6edd))
* reconcile documentation with current implementation state ([#244](https://github.com/dallay/profiletailors.com/issues/244)) ([08c0804](https://github.com/dallay/profiletailors.com/commit/08c0804f18235a3b98d4616c7564a2fea7760243))
