# Changelog

## [0.3.0](https://github.com/dallay/profiletailors.com/compare/app@v0.2.3...app@v0.3.0) (2026-07-30)


### ⚠ BREAKING CHANGES

* **tests:** RealLinkedInAssetUploader.storage and RealLinkedInPublisher.storage are now Storage? (nullable). The !! call in buildAssetContentEntities is safe because that path is only reached in real mode where storage is always configured.

### Features

* accessibility audit fixes, focus trap composable, and publishing backend improvements ([#139](https://github.com/dallay/profiletailors.com/issues/139)) ([67c9444](https://github.com/dallay/profiletailors.com/commit/67c94447cdc224652788cdc355268fb4ab63f4c0))
* add E2E integration test suite for auth flow with WebKit fixes ([#80](https://github.com/dallay/profiletailors.com/issues/80)) ([11b8190](https://github.com/dallay/profiletailors.com/commit/11b8190136651e5662e89d4daefe5549dad99fe0))
* Add email verification status to user profile and infrastructure ([d6d6e7b](https://github.com/dallay/profiletailors.com/commit/d6d6e7b43e9663eb76ee418ce030a889c3295c85))
* add LinkedIn channel avatar support with backend persistence and frontend fallback ([743ae47](https://github.com/dallay/profiletailors.com/commit/743ae47dbf8ff621d4a4cef222dae44155a3e1bb))
* add state management for dashboard engagement and implement type safety in SparklineChart ([39a7ca0](https://github.com/dallay/profiletailors.com/commit/39a7ca08e54729f8fc1d98bffb0db977c4a2f17d))
* Add workspace icon support (Lucide icons, phase 1) ([#81](https://github.com/dallay/profiletailors.com/issues/81)) ([09ed42f](https://github.com/dallay/profiletailors.com/commit/09ed42fb77d4abe522a800645bd94b204a08f93c))
* **app:** add password recovery frontend flow ([#506](https://github.com/dallay/profiletailors.com/issues/506)) ([15d26f7](https://github.com/dallay/profiletailors.com/commit/15d26f77ee1b2a0b3ec749c93bcce5dc4faf94da))
* **app:** fix LinkedIn preview truncation — componentize preview by network ([#134](https://github.com/dallay/profiletailors.com/issues/134)) ([f4fa409](https://github.com/dallay/profiletailors.com/commit/f4fa409a767a3ffdae2b07cd1019875fe5250887))
* **app:** modularize auth, workspace, and settings into modules ([#311](https://github.com/dallay/profiletailors.com/issues/311)) ([35ee333](https://github.com/dallay/profiletailors.com/commit/35ee333048b1c448261444954ae7cd737921180c))
* **app:** modularize dashboard into feature module ([#327](https://github.com/dallay/profiletailors.com/issues/327)) ([0497e88](https://github.com/dallay/profiletailors.com/commit/0497e88d8b08e237fd73c8af34217a5fe634353c))
* **app:** modularize media into feature module (#DALLAY-469) ([c502a03](https://github.com/dallay/profiletailors.com/commit/c502a03815ec16f00ac23a498094739de8b74eb9))
* **app:** modularize media into feature module (#DALLAY-469) ([89dd52d](https://github.com/dallay/profiletailors.com/commit/89dd52d7c9821bdf8065b2f8d0146be1288fb34e))
* **app:** modularize publishing into feature module ([#330](https://github.com/dallay/profiletailors.com/issues/330)) ([4ff5d7d](https://github.com/dallay/profiletailors.com/commit/4ff5d7d075de57ead3f88148da30b42c286d19ac))
* **app:** Phase 5 — Shared, layouts, and cleanup ([#332](https://github.com/dallay/profiletailors.com/issues/332)) ([889f292](https://github.com/dallay/profiletailors.com/commit/889f292761d2a9ccfdf79b2c14cdaec42c634bf4))
* **app:** redesign login/register with responsive UI, capability gates, and accessibility ([#524](https://github.com/dallay/profiletailors.com/issues/524)) ([dfb500f](https://github.com/dallay/profiletailors.com/commit/dfb500f25cbe660578198f00cf0453b9bd11990e))
* **auth:** harden local auth, stabilize register-flow E2E, add coverage ([#111](https://github.com/dallay/profiletailors.com/issues/111)) ([7098431](https://github.com/dallay/profiletailors.com/commit/709843133719a84407ab93355a600d86ad4a9f23))
* **auth:** implement session refresh with HttpOnly cookie and in-memory access token ([#27](https://github.com/dallay/profiletailors.com/issues/27)) ([6ae3db0](https://github.com/dallay/profiletailors.com/commit/6ae3db00d1b8565e610ee4daf5005ab5543ab7fc))
* backend env loading, tenancy workspace, frontend dashboard, security fixes ([#78](https://github.com/dallay/profiletailors.com/issues/78)) ([7be0b4b](https://github.com/dallay/profiletailors.com/commit/7be0b4b48d97db515486574b150608446bed7b2e))
* backend feature entitlements, credential lifecycle, and DevSecOps hardening ([8f94cd5](https://github.com/dallay/profiletailors.com/commit/8f94cd5022d2130ec1e8a18bea03d0be4d5858a3))
* **calendar:** add publication click event and improve accessibility in month view ([#145](https://github.com/dallay/profiletailors.com/issues/145)) ([b6f1d93](https://github.com/dallay/profiletailors.com/commit/b6f1d93ccbbcadcb03ade6a06f5e1aad8ee0b966))
* **calendar:** add thumbnail rendering in week view and improve layout ([f6698fc](https://github.com/dallay/profiletailors.com/commit/f6698fc0a1f5e325f14b5cedf8fcd4ddafce521f))
* **composer:** add media picker shell ([#242](https://github.com/dallay/profiletailors.com/issues/242)) ([dfa5ad4](https://github.com/dallay/profiletailors.com/commit/dfa5ad4cf7d21d263ec82782b1a6206a0fb60eea))
* **composer:** consolidate media attachment flow with compact rail, staged selection, upload reconciliation, and Unsplash provider ([#254](https://github.com/dallay/profiletailors.com/issues/254)) ([45bbc19](https://github.com/dallay/profiletailors.com/commit/45bbc19c94c2235c8f25cae433f04224289aab00))
* connect Vue SPA channels to Spring Boot LinkedIn backend ([#75](https://github.com/dallay/profiletailors.com/issues/75)) ([64e6ccc](https://github.com/dallay/profiletailors.com/commit/64e6ccc65c987147c170cd335f974406953dd123))
* **consent:** implement frontend consent management system ([#480](https://github.com/dallay/profiletailors.com/issues/480)) ([8d71ba7](https://github.com/dallay/profiletailors.com/commit/8d71ba7bf4c0a123a956d9bea56645d2623e1c62))
* dallay 433 media asset deduplication content addressed storage ([#463](https://github.com/dallay/profiletailors.com/issues/463)) ([a99cc59](https://github.com/dallay/profiletailors.com/commit/a99cc590e694b97b891d8d5e2f8ec0941b2f0f4c))
* **dashboard:** implement SMOS social media operations dashboard ([#77](https://github.com/dallay/profiletailors.com/issues/77)) ([a4d1cb4](https://github.com/dallay/profiletailors.com/commit/a4d1cb46a78759d578d94ea8e1f4bbd696faab2d))
* expand app shell, API versioning docs, and backend test coverage ([#29](https://github.com/dallay/profiletailors.com/issues/29)) ([2318f50](https://github.com/dallay/profiletailors.com/commit/2318f50fee6404116bc195da6552d47ac43b559b))
* **governance:** add DMCA takedown report UI and review dashboard (DALLAY-499 Phase 2) ([#417](https://github.com/dallay/profiletailors.com/issues/417)) ([0c3bd68](https://github.com/dallay/profiletailors.com/commit/0c3bd68afa86bd29d6b3150e195f8fa09abb7a5f))
* **identity:** add registration control gate and public capabilities API ([#395](https://github.com/dallay/profiletailors.com/issues/395)) ([e2dc7d9](https://github.com/dallay/profiletailors.com/commit/e2dc7d91aca3b8a1cb26dcfe38d6530d2a0d4fe2))
* **identity:** enforce age eligibility and terms acceptance during registration ([#387](https://github.com/dallay/profiletailors.com/issues/387)) ([2da26fb](https://github.com/dallay/profiletailors.com/commit/2da26fb9ee67a86e7542b2bf08e7c46072d358b3))
* **identity:** implement account closure feature (DALLAY-497) ([#430](https://github.com/dallay/profiletailors.com/issues/430)) ([f5e98be](https://github.com/dallay/profiletailors.com/commit/f5e98beda90a524e200e1a31cf19a510d66998fd))
* Implement workspace switcher in sidebar header ([#76](https://github.com/dallay/profiletailors.com/issues/76)) ([c0f1190](https://github.com/dallay/profiletailors.com/commit/c0f11901d02d371f2d637904d2ae381f9d6eca95))
* Introduce LinkedIn preview panel & truncation ([5c38c71](https://github.com/dallay/profiletailors.com/commit/5c38c71ef48cbbcdb86a0f354596dccb998825ca))
* **marketing:** bootstrap Vue 3 skeleton app ([#19](https://github.com/dallay/profiletailors.com/issues/19)) ([62fb05a](https://github.com/dallay/profiletailors.com/commit/62fb05aa730944e91711ae27bf9d587bd12ced86))
* **media-copyright-takedown:** complete SDD cycle — implement, verify, and archive ([4a53332](https://github.com/dallay/profiletailors.com/commit/4a5333212ba8f309f55aabdda1e57dbabea117f2))
* **media,ci:** workspace-level CAS media dedup + Postgres Testcontainers + detekt v2 + Biome root (size-exception) ([#174](https://github.com/dallay/profiletailors.com/issues/174)) ([baac461](https://github.com/dallay/profiletailors.com/commit/baac461c2e933991f9cbfa8c2493465cea512bbf))
* **media:** add centralized media library MVP ([#110](https://github.com/dallay/profiletailors.com/issues/110)) ([8f07e25](https://github.com/dallay/profiletailors.com/commit/8f07e25cf5ff00cb6959073e39b29dbd039b94b2))
* **media:** add licence schema and attribution display (DALLAY-499 Phase 1) ([#412](https://github.com/dallay/profiletailors.com/issues/412)) ([f53243b](https://github.com/dallay/profiletailors.com/commit/f53243be5e6ec30da5dccc901305cfb45a8465bc))
* **media:** add local auth and media library workflows ([#120](https://github.com/dallay/profiletailors.com/issues/120)) ([6e70486](https://github.com/dallay/profiletailors.com/commit/6e704868f3d1003d58973164f6fcbc0502207951))
* **media:** external asset metadata schema and H2 elimination ([#240](https://github.com/dallay/profiletailors.com/issues/240)) ([b432323](https://github.com/dallay/profiletailors.com/commit/b432323143548da8e1cb4bd70c6749622a1cf5bb))
* **media:** integrate Unsplash as first media provider ([#249](https://github.com/dallay/profiletailors.com/issues/249)) ([d06f833](https://github.com/dallay/profiletailors.com/commit/d06f83391eb2cee0f38263a1a1f99c31f8927a61))
* **privacy:** implement DSAR workflows for GDPR/CCPA compliance ([#392](https://github.com/dallay/profiletailors.com/issues/392)) ([9c46ac1](https://github.com/dallay/profiletailors.com/commit/9c46ac1d458b2b699921e1249d1181b9aa1d6eb9))
* **proxy:** add proxyImageUrl function for media URL handling ([70b2d86](https://github.com/dallay/profiletailors.com/commit/70b2d86dd9d5effe9395fa00e80c2901122d6fd6))
* **publishing:** add failure visibility and retry recovery for MVP core publishing ([#303](https://github.com/dallay/profiletailors.com/issues/303)) ([96efe6a](https://github.com/dallay/profiletailors.com/commit/96efe6a3bab94fe0838e651a3bdedf9827eb1fdb))
* **publishing:** add LinkedIn Publishing MVP with workspace-scoped social media management ([#39](https://github.com/dallay/profiletailors.com/issues/39)) ([3e17d7e](https://github.com/dallay/profiletailors.com/commit/3e17d7ea4e548471dfa0c911b88edf95d5c2cc06))
* **publishing:** add resolved provider catalog ([#464](https://github.com/dallay/profiletailors.com/issues/464)) ([e5e7fc1](https://github.com/dallay/profiletailors.com/commit/e5e7fc18a06666e68848fd1aeb0919a8978dd2a6))
* **publishing:** complete backend-integrated edit/delete for unpublished posts ([#131](https://github.com/dallay/profiletailors.com/issues/131)) ([#143](https://github.com/dallay/profiletailors.com/issues/143)) ([60bc76e](https://github.com/dallay/profiletailors.com/commit/60bc76e5070220036a8bb0508a401a3bd0441371))
* **publishing:** enhance provider catalog response with lowercase provider names ([07219d5](https://github.com/dallay/profiletailors.com/commit/07219d5a7f5b06b051722154e8ab960f52d2a35e))
* **publishing:** improve user-facing error messages for failed post publishing ([#335](https://github.com/dallay/profiletailors.com/issues/335)) ([dbd289a](https://github.com/dallay/profiletailors.com/commit/dbd289acfe793b806a5611a5403a9b1a5cedc52d))
* **publishing:** LinkedIn integration publication ([#99](https://github.com/dallay/profiletailors.com/issues/99)) ([5f15d0a](https://github.com/dallay/profiletailors.com/commit/5f15d0a0c9b2e81b4cfc7ab9eddd5d632a756b0c))
* **publishing:** real delete endpoint + persisted edit for unpublished posts ([#131](https://github.com/dallay/profiletailors.com/issues/131)) ([#138](https://github.com/dallay/profiletailors.com/issues/138)) ([ed06c17](https://github.com/dallay/profiletailors.com/commit/ed06c17e013d5dee68d2c47188b01a8d496a4d56))
* refactor App shell into 10 focused components and add image proxy API ([#88](https://github.com/dallay/profiletailors.com/issues/88)) ([62d313e](https://github.com/dallay/profiletailors.com/commit/62d313ed6fede794e6b1f3e35675a0a69d8a8501))
* refactor post scheduling to single-account selection and fix credential resolution path ([#86](https://github.com/dallay/profiletailors.com/issues/86)) ([961998d](https://github.com/dallay/profiletailors.com/commit/961998df27eec2984c4a9924f266f5c164949915))
* remove username field from registration flow ([#71](https://github.com/dallay/profiletailors.com/issues/71)) ([1d6640c](https://github.com/dallay/profiletailors.com/commit/1d6640cbd0d436574d311469ffd2bdc89b549003))
* restyle scheduler publication cards with provider icons ([#284](https://github.com/dallay/profiletailors.com/issues/284)) ([7973e48](https://github.com/dallay/profiletailors.com/commit/7973e485be5904c38aca27f1f0bed43a0fe4b9fd))
* **scheduler:** add canonical routes and URL codec composable ([#141](https://github.com/dallay/profiletailors.com/issues/141)) ([02c6e8f](https://github.com/dallay/profiletailors.com/commit/02c6e8f99ebed9d3043a77be3e354b3ba5e477d1))
* **scheduler:** E2E tests, CI workflow, and full-stack integration ([#101](https://github.com/dallay/profiletailors.com/issues/101)) ([12fd5a8](https://github.com/dallay/profiletailors.com/commit/12fd5a82fe07855758c8e549bd7c4f9a64c12977))
* **scheduler:** make calendar view URL-addressable with canonical routes and composable codec ([#154](https://github.com/dallay/profiletailors.com/issues/154)) ([eb36dcb](https://github.com/dallay/profiletailors.com/commit/eb36dcbd455d3756674c3b41a83aa64854f145eb))
* **scheduler:** replace calendar header dropdown emojis with Lucide SVG icons ([#301](https://github.com/dallay/profiletailors.com/issues/301)) ([c01cc42](https://github.com/dallay/profiletailors.com/commit/c01cc42263463790fd442154c1e7b853fb10e6fc))
* **scheduler:** standardize URL state and deep-linkable post details ([#292](https://github.com/dallay/profiletailors.com/issues/292)) ([7c3d63a](https://github.com/dallay/profiletailors.com/commit/7c3d63ae988eff4debaf6a1f3bcc0436b05bd2d0))
* **tenancy:** implement concrete R2DBC repositories and missing owner removal handler ([#271](https://github.com/dallay/profiletailors.com/issues/271)) ([5100bdd](https://github.com/dallay/profiletailors.com/commit/5100bdda8bba28cac67b266fca4962956bb5d552))
* **tests:** bootstrap Vitest for Vue app + backend publishing test coverage ([#64](https://github.com/dallay/profiletailors.com/issues/64)) ([5c716e7](https://github.com/dallay/profiletailors.com/commit/5c716e750722e50b9d7b679b675adfd537f64b5b))
* **transactions:** standardize reactive transaction strategy (closes [#195](https://github.com/dallay/profiletailors.com/issues/195)) ([#230](https://github.com/dallay/profiletailors.com/issues/230)) ([3e99e11](https://github.com/dallay/profiletailors.com/commit/3e99e117288130cc12e36596902fde5290e0cf2b))
* **verification:** add BDD scenarios and E2E tests for email verification media gate ([#216](https://github.com/dallay/profiletailors.com/issues/216)) ([fda809a](https://github.com/dallay/profiletailors.com/commit/fda809aec4d483ebb3cde0c5e9b6031318d71255))
* **web:** add visual content calendar with day/week/month views, drag-drop, and store integration ([#67](https://github.com/dallay/profiletailors.com/issues/67)) ([799f525](https://github.com/dallay/profiletailors.com/commit/799f5250fcc811d1aeac9e767bc7fcda2fa5208b)), closes [#52](https://github.com/dallay/profiletailors.com/issues/52)
* **web:** move channels into global sidebar, remove in-page channel manager ([#72](https://github.com/dallay/profiletailors.com/issues/72)) ([cc9bcaa](https://github.com/dallay/profiletailors.com/commit/cc9bcaa8e323ef2fdbe9be0d780918ad39777c2f))
* wrap multi-write operations in transactional boundaries for 3 handlers ([#227](https://github.com/dallay/profiletailors.com/issues/227)) ([af2c8dd](https://github.com/dallay/profiletailors.com/commit/af2c8dd0f4930c850fc2755f02afa66a0920b9fd))


### Bug Fixes

* 13 code-review findings across apps/web/app and openspec/specs ([#156](https://github.com/dallay/profiletailors.com/issues/156)) ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))
* address CodeRabbit review findings for App shell + image proxy ([#90](https://github.com/dallay/profiletailors.com/issues/90)) ([d418d3d](https://github.com/dallay/profiletailors.com/commit/d418d3dc06b88420e0c78d09949245bce7a97c19))
* address Kody AI code review findings — type safety and avatar fallback state reset ([0163975](https://github.com/dallay/profiletailors.com/commit/016397522f3c5988f35d6504b5d43b91c3cc6a82))
* **app:** address media module review feedback ([c046b3c](https://github.com/dallay/profiletailors.com/commit/c046b3c212889735b00b4947beed3565a601a4e8))
* **app:** preserve media polling error handling ([710174a](https://github.com/dallay/profiletailors.com/commit/710174af7a082f2fb0abb3ee09c7b284768c24e5))
* **app:** use distinct icons for navigation and composer controls ([1e64fcc](https://github.com/dallay/profiletailors.com/commit/1e64fcc4a38cbfc061e3845bb1a69ca60da4cc34))
* **auth:** assign WORKSPACE_OWNER role on provisioning, fix settings UX audit, and fix dialog transparency ([#91](https://github.com/dallay/profiletailors.com/issues/91)) ([5b337a0](https://github.com/dallay/profiletailors.com/commit/5b337a084934f382e0170bf4a3a9db06414a7a4c))
* **auth:** improve sign-in form semantics ([#494](https://github.com/dallay/profiletailors.com/issues/494)) ([c090a43](https://github.com/dallay/profiletailors.com/commit/c090a43b7186afcaae11ff5acc562719d892ce95))
* **calendar:** contain scroll to timeline and add thin native scrollbar utility ([#273](https://github.com/dallay/profiletailors.com/issues/273)) ([8457078](https://github.com/dallay/profiletailors.com/commit/8457078acd73d4ef7304348e7dc08b56ebb867c4))
* **chart:** restore missing Vue imports in ChartContainer ([b35d64c](https://github.com/dallay/profiletailors.com/commit/b35d64cd8f181ad5b5b298952ecdf3682c5ac1a7))
* **ci:** update stale workflow to common-actions v2.2.2 ([#269](https://github.com/dallay/profiletailors.com/issues/269)) ([d0a58ae](https://github.com/dallay/profiletailors.com/commit/d0a58ae65e8e71cd2633de344533d6d03740e065))
* CodeRabbit auto-fixes for PR [#498](https://github.com/dallay/profiletailors.com/issues/498) ([#501](https://github.com/dallay/profiletailors.com/issues/501)) ([be36730](https://github.com/dallay/profiletailors.com/commit/be36730cf2ea666abe710c8f0bba127109f1bd22))
* CodeRabbit auto-fixes for PR [#504](https://github.com/dallay/profiletailors.com/issues/504) ([#505](https://github.com/dallay/profiletailors.com/issues/505)) ([7a28f62](https://github.com/dallay/profiletailors.com/commit/7a28f621406e6fa5ddd4a16d61e99a610252da76))
* **composer:** wire selectedChannelId into media picker and fix effectiveAttachmentLimit ([ee509e4](https://github.com/dallay/profiletailors.com/commit/ee509e40a8276ffa41aba61952d2b86807b88ef9))
* **deps:** address ~74 Dependabot alerts - Spring Boot, Jackson, Netty, Astro, vite, undici, hono ([#162](https://github.com/dallay/profiletailors.com/issues/162)) ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* **deps:** update dependency @lucide/vue to v1.27.0 ([#521](https://github.com/dallay/profiletailors.com/issues/521)) ([01d66f3](https://github.com/dallay/profiletailors.com/commit/01d66f3004d5acbe3660a2e307602a17614cf580))
* **deps:** update dependency shadcn-vue to v2.8.0 ([#522](https://github.com/dallay/profiletailors.com/issues/522)) ([17ff4e6](https://github.com/dallay/profiletailors.com/commit/17ff4e6fdad3cb0756eb9893245bf9426c5e1732))
* **devops:** move backend HTTP port to 7638 (SMP_BACKEND_PORT) ([3abccd7](https://github.com/dallay/profiletailors.com/commit/3abccd78fdcb6b7a89278ab336d6d4a8fc1823a7))
* **dialog:** resolve Vue 3 self-recursion in Dialog components and clean E2E consent test suite ([#520](https://github.com/dallay/profiletailors.com/issues/520)) ([e8d09ee](https://github.com/dallay/profiletailors.com/commit/e8d09ee392a09d368daa52064f913c650b392757))
* **e2e:** restore composer media attachments fixtures and add E2E suite ([#272](https://github.com/dallay/profiletailors.com/issues/272)) ([b11b18b](https://github.com/dallay/profiletailors.com/commit/b11b18bf7ac874713c2357886ee069581137a41f))
* **i18n:** update Spanish translation for endpoint ([#74](https://github.com/dallay/profiletailors.com/issues/74)) ([117db77](https://github.com/dallay/profiletailors.com/commit/117db77ba3d9352c89ed2a8905e3bb09d2816927))
* **media:** transactional boundaries, compensation semantics, and CI alignment ([#229](https://github.com/dallay/profiletailors.com/issues/229)) ([405d8bc](https://github.com/dallay/profiletailors.com/commit/405d8bcd0bebe6d25d76aa4603166a9c326cd875))
* **platform:** enable rate limiting by default ([#182](https://github.com/dallay/profiletailors.com/issues/182)) ([0f2adf6](https://github.com/dallay/profiletailors.com/commit/0f2adf64baa1b2e4ae0ef0e4f23c49394054b728))
* **publishing:** reconcile backend publication identity on create and quick-create ([65a5d7c](https://github.com/dallay/profiletailors.com/commit/65a5d7c7315900c9ea619753d60946b52afbd93f))
* **publishing:** tri-state assetIds PATCH with edit hydration ([#223](https://github.com/dallay/profiletailors.com/issues/223)) ([2961147](https://github.com/dallay/profiletailors.com/commit/2961147b150423a48e8735997f9a79164d392fda))
* **quality:** align coverage reporting ([#116](https://github.com/dallay/profiletailors.com/issues/116)) ([62fed0f](https://github.com/dallay/profiletailors.com/commit/62fed0f78229de456186d08632d3b68ccb8c6a48))
* **quality:** resolve 14 SonarCloud issues blocking quality gate ([#436](https://github.com/dallay/profiletailors.com/issues/436)) ([1593ce7](https://github.com/dallay/profiletailors.com/commit/1593ce795490429a4703991422a559debb50923b))
* **quality:** resolve 3 SonarCloud issues blocking quality gate ([#437](https://github.com/dallay/profiletailors.com/issues/437)) ([f3de1a8](https://github.com/dallay/profiletailors.com/commit/f3de1a8f5721ea6447826ea0709b30649011c818))
* **quality:** resolve all SonarQube violations to pass quality gate ([#339](https://github.com/dallay/profiletailors.com/issues/339)) ([50bbf80](https://github.com/dallay/profiletailors.com/commit/50bbf80711f28f3d2eb5f80dddb5e5355291c875))
* resolve 19 SonarQube code smells and align Codecov exclusions ([#159](https://github.com/dallay/profiletailors.com/issues/159)) ([a8abfad](https://github.com/dallay/profiletailors.com/commit/a8abfad3e753c517956d875f00289198b8326779))
* **scheduler:** prevent scheduling publications in the past with 5-minute grace period ([#93](https://github.com/dallay/profiletailors.com/issues/93)) ([d7d866f](https://github.com/dallay/profiletailors.com/commit/d7d866f1ffd8444b9d70dc7935ff86a67eb79741))
* **scheduler:** restore post detail URL state ([d4ace06](https://github.com/dallay/profiletailors.com/commit/d4ace06b85ff01073d0572cf7d10e5271d225850))
* security vuln remediation ([#512](https://github.com/dallay/profiletailors.com/issues/512)) ([ec10d26](https://github.com/dallay/profiletailors.com/commit/ec10d26ba5820bb11eb4e8c9f40167f5401af48e))
* **security:** resolve 3 open code scanning alerts ([#187](https://github.com/dallay/profiletailors.com/issues/187)) ([1a9c580](https://github.com/dallay/profiletailors.com/commit/1a9c5809c616173a8afcde9a5256ac4b220eea0f))
* shell injection in setup-frontend GitHub Action — use env var ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* shell injection in setup-frontend GitHub Action — use env var ([a8abfad](https://github.com/dallay/profiletailors.com/commit/a8abfad3e753c517956d875f00289198b8326779))
* shell injection in setup-frontend GitHub Action — use env var ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))
* shell injection in setup-frontend GitHub Action — use env var ([ae84056](https://github.com/dallay/profiletailors.com/commit/ae8405615b105b9a0443f7262b130205251f4feb))
* Sidebar Responsiveness and Persistence ([#204](https://github.com/dallay/profiletailors.com/issues/204)) ([021b2b3](https://github.com/dallay/profiletailors.com/commit/021b2b302e8fcdf91e708a58c6230999f4f4aabf))
* **sonar:** quality gate - exclude shadcn wrappers, add coverage test, set v0.1.0 baseline ([#151](https://github.com/dallay/profiletailors.com/issues/151)) ([b5b2227](https://github.com/dallay/profiletailors.com/commit/b5b2227b4756b96ff9c3a0f2e5a3f03e622f5bd1))
* **sonar:** resolve SonarQube Quality Gate issues ([#411](https://github.com/dallay/profiletailors.com/issues/411)) ([2ddc7df](https://github.com/dallay/profiletailors.com/commit/2ddc7df2fe7a7611a7bc92cc8969d05332c76223))
* **test:** drop webkit from dashboard e2e matrix ([#331](https://github.com/dallay/profiletailors.com/issues/331)) ([a3909eb](https://github.com/dallay/profiletailors.com/commit/a3909eb63a3425d18112266f3b92c3785b98777f))
* **tests:** update proxyImageUrl test for improved readability ([7320f53](https://github.com/dallay/profiletailors.com/commit/7320f5356c1429e4b3df7613c4c0b79501e66c1c))
* **test:** unskip composer media e2e tests ([#343](https://github.com/dallay/profiletailors.com/issues/343)) ([bd15351](https://github.com/dallay/profiletailors.com/commit/bd153512f002dbbbc8d261110829536535af21a6))
* **test:** update CreatePostModal tests for channel-specific attachment limits ([e8b469d](https://github.com/dallay/profiletailors.com/commit/e8b469d9815595621518ab5cdfb51e2b6806b32a))
* unsplash image flow ([#329](https://github.com/dallay/profiletailors.com/issues/329)) ([e46ef4f](https://github.com/dallay/profiletailors.com/commit/e46ef4f723f9a7ed428042ab6cb85e16ffc26180))
* **ux:** prevent week grid overflow and add toast feedback on post creation ([#280](https://github.com/dallay/profiletailors.com/issues/280)) ([6146bf4](https://github.com/dallay/profiletailors.com/commit/6146bf425e4cbfefd53c4e1468a7fb726498508c))
* **web:** add missing vue imports in ChartContainer (toRefs, useId, computed, HTMLAttributes) ([40b8e90](https://github.com/dallay/profiletailors.com/commit/40b8e900261c62af0d2b306e4da6860c0b71f16e))


### Refactoring

* **composer:** extract media picker orchestration into useComposerMediaPicker composable ([68c05a0](https://github.com/dallay/profiletailors.com/commit/68c05a06e2bf52d6f1a43b0a47ddce7c1133043f))
* **publishing:** extract composer composables for testability ([#498](https://github.com/dallay/profiletailors.com/issues/498)) ([60e74fb](https://github.com/dallay/profiletailors.com/commit/60e74fb7ce7275a99319bdde35962397781bd4ea))
* **publishing:** extract ComposerSchedulePanel and ComposerChannelSelector ([#504](https://github.com/dallay/profiletailors.com/issues/504)) ([dc7cab4](https://github.com/dallay/profiletailors.com/commit/dc7cab4e6ee9e364bb49c347c84024c847a73774))
* Remove unsplashProviderEnabled feature flag — enable Unsplash permanently ([#294](https://github.com/dallay/profiletailors.com/issues/294)) ([16b2e9a](https://github.com/dallay/profiletailors.com/commit/16b2e9a04b1af42120d0a9508f6bf3fcf776c747))
* separate authorization domain interfaces and clean up unused code ([#18](https://github.com/dallay/profiletailors.com/issues/18)) ([9171a17](https://github.com/dallay/profiletailors.com/commit/9171a177f59949fd86611cd3f39dd94ae1f933ba))
* **web:** restructure sidebar into modular components ([#30](https://github.com/dallay/profiletailors.com/issues/30)) ([aa51b4e](https://github.com/dallay/profiletailors.com/commit/aa51b4ec39bdf2f3936038ac4b09e87185997673))


### Documentation

* reconcile documentation with current implementation ([#393](https://github.com/dallay/profiletailors.com/issues/393)) ([9946d90](https://github.com/dallay/profiletailors.com/commit/9946d90323785d392312be9447baab217bdf6edd))

## [0.2.3](https://github.com/dallay/profiletailors.com/compare/app@v0.2.2...app@v0.2.3) (2026-07-30)


### Bug Fixes

* **deps:** update dependency shadcn-vue to v2.8.0 ([#522](https://github.com/dallay/profiletailors.com/issues/522)) ([17ff4e6](https://github.com/dallay/profiletailors.com/commit/17ff4e6fdad3cb0756eb9893245bf9426c5e1732))

## [0.2.2](https://github.com/dallay/profiletailors.com/compare/app@v0.2.1...app@v0.2.2) (2026-07-30)


### Features

* **app:** redesign login/register with responsive UI, capability gates, and accessibility ([#524](https://github.com/dallay/profiletailors.com/issues/524)) ([dfb500f](https://github.com/dallay/profiletailors.com/commit/dfb500f25cbe660578198f00cf0453b9bd11990e))


### Bug Fixes

* **dialog:** resolve Vue 3 self-recursion in Dialog components and clean E2E consent test suite ([#520](https://github.com/dallay/profiletailors.com/issues/520)) ([e8d09ee](https://github.com/dallay/profiletailors.com/commit/e8d09ee392a09d368daa52064f913c650b392757))

## [0.2.1](https://github.com/dallay/profiletailors.com/compare/app@v0.2.0...app@v0.2.1) (2026-07-29)


### Features

* **app:** add password recovery frontend flow ([#506](https://github.com/dallay/profiletailors.com/issues/506)) ([15d26f7](https://github.com/dallay/profiletailors.com/commit/15d26f77ee1b2a0b3ec749c93bcce5dc4faf94da))
* **consent:** implement frontend consent management system ([#480](https://github.com/dallay/profiletailors.com/issues/480)) ([8d71ba7](https://github.com/dallay/profiletailors.com/commit/8d71ba7bf4c0a123a956d9bea56645d2623e1c62))
* dallay 433 media asset deduplication content addressed storage ([#463](https://github.com/dallay/profiletailors.com/issues/463)) ([a99cc59](https://github.com/dallay/profiletailors.com/commit/a99cc590e694b97b891d8d5e2f8ec0941b2f0f4c))
* **publishing:** add resolved provider catalog ([#464](https://github.com/dallay/profiletailors.com/issues/464)) ([e5e7fc1](https://github.com/dallay/profiletailors.com/commit/e5e7fc18a06666e68848fd1aeb0919a8978dd2a6))
* **publishing:** enhance provider catalog response with lowercase provider names ([07219d5](https://github.com/dallay/profiletailors.com/commit/07219d5a7f5b06b051722154e8ab960f52d2a35e))


### Bug Fixes

* **auth:** improve sign-in form semantics ([#494](https://github.com/dallay/profiletailors.com/issues/494)) ([c090a43](https://github.com/dallay/profiletailors.com/commit/c090a43b7186afcaae11ff5acc562719d892ce95))
* CodeRabbit auto-fixes for PR [#498](https://github.com/dallay/profiletailors.com/issues/498) ([#501](https://github.com/dallay/profiletailors.com/issues/501)) ([be36730](https://github.com/dallay/profiletailors.com/commit/be36730cf2ea666abe710c8f0bba127109f1bd22))
* CodeRabbit auto-fixes for PR [#504](https://github.com/dallay/profiletailors.com/issues/504) ([#505](https://github.com/dallay/profiletailors.com/issues/505)) ([7a28f62](https://github.com/dallay/profiletailors.com/commit/7a28f621406e6fa5ddd4a16d61e99a610252da76))
* security vuln remediation ([#512](https://github.com/dallay/profiletailors.com/issues/512)) ([ec10d26](https://github.com/dallay/profiletailors.com/commit/ec10d26ba5820bb11eb4e8c9f40167f5401af48e))


### Refactoring

* **publishing:** extract composer composables for testability ([#498](https://github.com/dallay/profiletailors.com/issues/498)) ([60e74fb](https://github.com/dallay/profiletailors.com/commit/60e74fb7ce7275a99319bdde35962397781bd4ea))
* **publishing:** extract ComposerSchedulePanel and ComposerChannelSelector ([#504](https://github.com/dallay/profiletailors.com/issues/504)) ([dc7cab4](https://github.com/dallay/profiletailors.com/commit/dc7cab4e6ee9e364bb49c347c84024c847a73774))

## [0.2.0](https://github.com/dallay/profiletailors.com/compare/app@v0.1.0...app@v0.2.0) (2026-07-23)


### ⚠ BREAKING CHANGES

* **tests:** RealLinkedInAssetUploader.storage and RealLinkedInPublisher.storage are now Storage? (nullable). The !! call in buildAssetContentEntities is safe because that path is only reached in real mode where storage is always configured.

### Features

* accessibility audit fixes, focus trap composable, and publishing backend improvements ([#139](https://github.com/dallay/profiletailors.com/issues/139)) ([67c9444](https://github.com/dallay/profiletailors.com/commit/67c94447cdc224652788cdc355268fb4ab63f4c0))
* add E2E integration test suite for auth flow with WebKit fixes ([#80](https://github.com/dallay/profiletailors.com/issues/80)) ([11b8190](https://github.com/dallay/profiletailors.com/commit/11b8190136651e5662e89d4daefe5549dad99fe0))
* Add email verification status to user profile and infrastructure ([d6d6e7b](https://github.com/dallay/profiletailors.com/commit/d6d6e7b43e9663eb76ee418ce030a889c3295c85))
* add LinkedIn channel avatar support with backend persistence and frontend fallback ([743ae47](https://github.com/dallay/profiletailors.com/commit/743ae47dbf8ff621d4a4cef222dae44155a3e1bb))
* add state management for dashboard engagement and implement type safety in SparklineChart ([39a7ca0](https://github.com/dallay/profiletailors.com/commit/39a7ca08e54729f8fc1d98bffb0db977c4a2f17d))
* Add workspace icon support (Lucide icons, phase 1) ([#81](https://github.com/dallay/profiletailors.com/issues/81)) ([09ed42f](https://github.com/dallay/profiletailors.com/commit/09ed42fb77d4abe522a800645bd94b204a08f93c))
* **app:** fix LinkedIn preview truncation — componentize preview by network ([#134](https://github.com/dallay/profiletailors.com/issues/134)) ([f4fa409](https://github.com/dallay/profiletailors.com/commit/f4fa409a767a3ffdae2b07cd1019875fe5250887))
* **app:** modularize auth, workspace, and settings into modules ([#311](https://github.com/dallay/profiletailors.com/issues/311)) ([35ee333](https://github.com/dallay/profiletailors.com/commit/35ee333048b1c448261444954ae7cd737921180c))
* **app:** modularize dashboard into feature module ([#327](https://github.com/dallay/profiletailors.com/issues/327)) ([0497e88](https://github.com/dallay/profiletailors.com/commit/0497e88d8b08e237fd73c8af34217a5fe634353c))
* **app:** modularize media into feature module (#DALLAY-469) ([c502a03](https://github.com/dallay/profiletailors.com/commit/c502a03815ec16f00ac23a498094739de8b74eb9))
* **app:** modularize media into feature module (#DALLAY-469) ([89dd52d](https://github.com/dallay/profiletailors.com/commit/89dd52d7c9821bdf8065b2f8d0146be1288fb34e))
* **app:** modularize publishing into feature module ([#330](https://github.com/dallay/profiletailors.com/issues/330)) ([4ff5d7d](https://github.com/dallay/profiletailors.com/commit/4ff5d7d075de57ead3f88148da30b42c286d19ac))
* **app:** Phase 5 — Shared, layouts, and cleanup ([#332](https://github.com/dallay/profiletailors.com/issues/332)) ([889f292](https://github.com/dallay/profiletailors.com/commit/889f292761d2a9ccfdf79b2c14cdaec42c634bf4))
* **auth:** harden local auth, stabilize register-flow E2E, add coverage ([#111](https://github.com/dallay/profiletailors.com/issues/111)) ([7098431](https://github.com/dallay/profiletailors.com/commit/709843133719a84407ab93355a600d86ad4a9f23))
* **auth:** implement session refresh with HttpOnly cookie and in-memory access token ([#27](https://github.com/dallay/profiletailors.com/issues/27)) ([6ae3db0](https://github.com/dallay/profiletailors.com/commit/6ae3db00d1b8565e610ee4daf5005ab5543ab7fc))
* backend env loading, tenancy workspace, frontend dashboard, security fixes ([#78](https://github.com/dallay/profiletailors.com/issues/78)) ([7be0b4b](https://github.com/dallay/profiletailors.com/commit/7be0b4b48d97db515486574b150608446bed7b2e))
* backend feature entitlements, credential lifecycle, and DevSecOps hardening ([8f94cd5](https://github.com/dallay/profiletailors.com/commit/8f94cd5022d2130ec1e8a18bea03d0be4d5858a3))
* **calendar:** add publication click event and improve accessibility in month view ([#145](https://github.com/dallay/profiletailors.com/issues/145)) ([b6f1d93](https://github.com/dallay/profiletailors.com/commit/b6f1d93ccbbcadcb03ade6a06f5e1aad8ee0b966))
* **calendar:** add thumbnail rendering in week view and improve layout ([f6698fc](https://github.com/dallay/profiletailors.com/commit/f6698fc0a1f5e325f14b5cedf8fcd4ddafce521f))
* **composer:** add media picker shell ([#242](https://github.com/dallay/profiletailors.com/issues/242)) ([dfa5ad4](https://github.com/dallay/profiletailors.com/commit/dfa5ad4cf7d21d263ec82782b1a6206a0fb60eea))
* **composer:** consolidate media attachment flow with compact rail, staged selection, upload reconciliation, and Unsplash provider ([#254](https://github.com/dallay/profiletailors.com/issues/254)) ([45bbc19](https://github.com/dallay/profiletailors.com/commit/45bbc19c94c2235c8f25cae433f04224289aab00))
* connect Vue SPA channels to Spring Boot LinkedIn backend ([#75](https://github.com/dallay/profiletailors.com/issues/75)) ([64e6ccc](https://github.com/dallay/profiletailors.com/commit/64e6ccc65c987147c170cd335f974406953dd123))
* **dashboard:** implement SMOS social media operations dashboard ([#77](https://github.com/dallay/profiletailors.com/issues/77)) ([a4d1cb4](https://github.com/dallay/profiletailors.com/commit/a4d1cb46a78759d578d94ea8e1f4bbd696faab2d))
* expand app shell, API versioning docs, and backend test coverage ([#29](https://github.com/dallay/profiletailors.com/issues/29)) ([2318f50](https://github.com/dallay/profiletailors.com/commit/2318f50fee6404116bc195da6552d47ac43b559b))
* **governance:** add DMCA takedown report UI and review dashboard (DALLAY-499 Phase 2) ([#417](https://github.com/dallay/profiletailors.com/issues/417)) ([0c3bd68](https://github.com/dallay/profiletailors.com/commit/0c3bd68afa86bd29d6b3150e195f8fa09abb7a5f))
* **identity:** add registration control gate and public capabilities API ([#395](https://github.com/dallay/profiletailors.com/issues/395)) ([e2dc7d9](https://github.com/dallay/profiletailors.com/commit/e2dc7d91aca3b8a1cb26dcfe38d6530d2a0d4fe2))
* **identity:** enforce age eligibility and terms acceptance during registration ([#387](https://github.com/dallay/profiletailors.com/issues/387)) ([2da26fb](https://github.com/dallay/profiletailors.com/commit/2da26fb9ee67a86e7542b2bf08e7c46072d358b3))
* **identity:** implement account closure feature (DALLAY-497) ([#430](https://github.com/dallay/profiletailors.com/issues/430)) ([f5e98be](https://github.com/dallay/profiletailors.com/commit/f5e98beda90a524e200e1a31cf19a510d66998fd))
* Implement workspace switcher in sidebar header ([#76](https://github.com/dallay/profiletailors.com/issues/76)) ([c0f1190](https://github.com/dallay/profiletailors.com/commit/c0f11901d02d371f2d637904d2ae381f9d6eca95))
* Introduce LinkedIn preview panel & truncation ([5c38c71](https://github.com/dallay/profiletailors.com/commit/5c38c71ef48cbbcdb86a0f354596dccb998825ca))
* **marketing:** bootstrap Vue 3 skeleton app ([#19](https://github.com/dallay/profiletailors.com/issues/19)) ([62fb05a](https://github.com/dallay/profiletailors.com/commit/62fb05aa730944e91711ae27bf9d587bd12ced86))
* **media-copyright-takedown:** complete SDD cycle — implement, verify, and archive ([4a53332](https://github.com/dallay/profiletailors.com/commit/4a5333212ba8f309f55aabdda1e57dbabea117f2))
* **media,ci:** workspace-level CAS media dedup + Postgres Testcontainers + detekt v2 + Biome root (size-exception) ([#174](https://github.com/dallay/profiletailors.com/issues/174)) ([baac461](https://github.com/dallay/profiletailors.com/commit/baac461c2e933991f9cbfa8c2493465cea512bbf))
* **media:** add centralized media library MVP ([#110](https://github.com/dallay/profiletailors.com/issues/110)) ([8f07e25](https://github.com/dallay/profiletailors.com/commit/8f07e25cf5ff00cb6959073e39b29dbd039b94b2))
* **media:** add licence schema and attribution display (DALLAY-499 Phase 1) ([#412](https://github.com/dallay/profiletailors.com/issues/412)) ([f53243b](https://github.com/dallay/profiletailors.com/commit/f53243be5e6ec30da5dccc901305cfb45a8465bc))
* **media:** add local auth and media library workflows ([#120](https://github.com/dallay/profiletailors.com/issues/120)) ([6e70486](https://github.com/dallay/profiletailors.com/commit/6e704868f3d1003d58973164f6fcbc0502207951))
* **media:** external asset metadata schema and H2 elimination ([#240](https://github.com/dallay/profiletailors.com/issues/240)) ([b432323](https://github.com/dallay/profiletailors.com/commit/b432323143548da8e1cb4bd70c6749622a1cf5bb))
* **media:** integrate Unsplash as first media provider ([#249](https://github.com/dallay/profiletailors.com/issues/249)) ([d06f833](https://github.com/dallay/profiletailors.com/commit/d06f83391eb2cee0f38263a1a1f99c31f8927a61))
* **privacy:** implement DSAR workflows for GDPR/CCPA compliance ([#392](https://github.com/dallay/profiletailors.com/issues/392)) ([9c46ac1](https://github.com/dallay/profiletailors.com/commit/9c46ac1d458b2b699921e1249d1181b9aa1d6eb9))
* **proxy:** add proxyImageUrl function for media URL handling ([70b2d86](https://github.com/dallay/profiletailors.com/commit/70b2d86dd9d5effe9395fa00e80c2901122d6fd6))
* **publishing:** add failure visibility and retry recovery for MVP core publishing ([#303](https://github.com/dallay/profiletailors.com/issues/303)) ([96efe6a](https://github.com/dallay/profiletailors.com/commit/96efe6a3bab94fe0838e651a3bdedf9827eb1fdb))
* **publishing:** add LinkedIn Publishing MVP with workspace-scoped social media management ([#39](https://github.com/dallay/profiletailors.com/issues/39)) ([3e17d7e](https://github.com/dallay/profiletailors.com/commit/3e17d7ea4e548471dfa0c911b88edf95d5c2cc06))
* **publishing:** complete backend-integrated edit/delete for unpublished posts ([#131](https://github.com/dallay/profiletailors.com/issues/131)) ([#143](https://github.com/dallay/profiletailors.com/issues/143)) ([60bc76e](https://github.com/dallay/profiletailors.com/commit/60bc76e5070220036a8bb0508a401a3bd0441371))
* **publishing:** improve user-facing error messages for failed post publishing ([#335](https://github.com/dallay/profiletailors.com/issues/335)) ([dbd289a](https://github.com/dallay/profiletailors.com/commit/dbd289acfe793b806a5611a5403a9b1a5cedc52d))
* **publishing:** LinkedIn integration publication ([#99](https://github.com/dallay/profiletailors.com/issues/99)) ([5f15d0a](https://github.com/dallay/profiletailors.com/commit/5f15d0a0c9b2e81b4cfc7ab9eddd5d632a756b0c))
* **publishing:** real delete endpoint + persisted edit for unpublished posts ([#131](https://github.com/dallay/profiletailors.com/issues/131)) ([#138](https://github.com/dallay/profiletailors.com/issues/138)) ([ed06c17](https://github.com/dallay/profiletailors.com/commit/ed06c17e013d5dee68d2c47188b01a8d496a4d56))
* refactor App shell into 10 focused components and add image proxy API ([#88](https://github.com/dallay/profiletailors.com/issues/88)) ([62d313e](https://github.com/dallay/profiletailors.com/commit/62d313ed6fede794e6b1f3e35675a0a69d8a8501))
* refactor post scheduling to single-account selection and fix credential resolution path ([#86](https://github.com/dallay/profiletailors.com/issues/86)) ([961998d](https://github.com/dallay/profiletailors.com/commit/961998df27eec2984c4a9924f266f5c164949915))
* remove username field from registration flow ([#71](https://github.com/dallay/profiletailors.com/issues/71)) ([1d6640c](https://github.com/dallay/profiletailors.com/commit/1d6640cbd0d436574d311469ffd2bdc89b549003))
* restyle scheduler publication cards with provider icons ([#284](https://github.com/dallay/profiletailors.com/issues/284)) ([7973e48](https://github.com/dallay/profiletailors.com/commit/7973e485be5904c38aca27f1f0bed43a0fe4b9fd))
* **scheduler:** add canonical routes and URL codec composable ([#141](https://github.com/dallay/profiletailors.com/issues/141)) ([02c6e8f](https://github.com/dallay/profiletailors.com/commit/02c6e8f99ebed9d3043a77be3e354b3ba5e477d1))
* **scheduler:** E2E tests, CI workflow, and full-stack integration ([#101](https://github.com/dallay/profiletailors.com/issues/101)) ([12fd5a8](https://github.com/dallay/profiletailors.com/commit/12fd5a82fe07855758c8e549bd7c4f9a64c12977))
* **scheduler:** make calendar view URL-addressable with canonical routes and composable codec ([#154](https://github.com/dallay/profiletailors.com/issues/154)) ([eb36dcb](https://github.com/dallay/profiletailors.com/commit/eb36dcbd455d3756674c3b41a83aa64854f145eb))
* **scheduler:** replace calendar header dropdown emojis with Lucide SVG icons ([#301](https://github.com/dallay/profiletailors.com/issues/301)) ([c01cc42](https://github.com/dallay/profiletailors.com/commit/c01cc42263463790fd442154c1e7b853fb10e6fc))
* **scheduler:** standardize URL state and deep-linkable post details ([#292](https://github.com/dallay/profiletailors.com/issues/292)) ([7c3d63a](https://github.com/dallay/profiletailors.com/commit/7c3d63ae988eff4debaf6a1f3bcc0436b05bd2d0))
* **tenancy:** implement concrete R2DBC repositories and missing owner removal handler ([#271](https://github.com/dallay/profiletailors.com/issues/271)) ([5100bdd](https://github.com/dallay/profiletailors.com/commit/5100bdda8bba28cac67b266fca4962956bb5d552))
* **tests:** bootstrap Vitest for Vue app + backend publishing test coverage ([#64](https://github.com/dallay/profiletailors.com/issues/64)) ([5c716e7](https://github.com/dallay/profiletailors.com/commit/5c716e750722e50b9d7b679b675adfd537f64b5b))
* **transactions:** standardize reactive transaction strategy (closes [#195](https://github.com/dallay/profiletailors.com/issues/195)) ([#230](https://github.com/dallay/profiletailors.com/issues/230)) ([3e99e11](https://github.com/dallay/profiletailors.com/commit/3e99e117288130cc12e36596902fde5290e0cf2b))
* **verification:** add BDD scenarios and E2E tests for email verification media gate ([#216](https://github.com/dallay/profiletailors.com/issues/216)) ([fda809a](https://github.com/dallay/profiletailors.com/commit/fda809aec4d483ebb3cde0c5e9b6031318d71255))
* **web:** add visual content calendar with day/week/month views, drag-drop, and store integration ([#67](https://github.com/dallay/profiletailors.com/issues/67)) ([799f525](https://github.com/dallay/profiletailors.com/commit/799f5250fcc811d1aeac9e767bc7fcda2fa5208b)), closes [#52](https://github.com/dallay/profiletailors.com/issues/52)
* **web:** move channels into global sidebar, remove in-page channel manager ([#72](https://github.com/dallay/profiletailors.com/issues/72)) ([cc9bcaa](https://github.com/dallay/profiletailors.com/commit/cc9bcaa8e323ef2fdbe9be0d780918ad39777c2f))
* wrap multi-write operations in transactional boundaries for 3 handlers ([#227](https://github.com/dallay/profiletailors.com/issues/227)) ([af2c8dd](https://github.com/dallay/profiletailors.com/commit/af2c8dd0f4930c850fc2755f02afa66a0920b9fd))


### Bug Fixes

* 13 code-review findings across apps/web/app and openspec/specs ([#156](https://github.com/dallay/profiletailors.com/issues/156)) ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))
* address CodeRabbit review findings for App shell + image proxy ([#90](https://github.com/dallay/profiletailors.com/issues/90)) ([d418d3d](https://github.com/dallay/profiletailors.com/commit/d418d3dc06b88420e0c78d09949245bce7a97c19))
* address Kody AI code review findings — type safety and avatar fallback state reset ([0163975](https://github.com/dallay/profiletailors.com/commit/016397522f3c5988f35d6504b5d43b91c3cc6a82))
* **app:** address media module review feedback ([c046b3c](https://github.com/dallay/profiletailors.com/commit/c046b3c212889735b00b4947beed3565a601a4e8))
* **app:** preserve media polling error handling ([710174a](https://github.com/dallay/profiletailors.com/commit/710174af7a082f2fb0abb3ee09c7b284768c24e5))
* **app:** use distinct icons for navigation and composer controls ([1e64fcc](https://github.com/dallay/profiletailors.com/commit/1e64fcc4a38cbfc061e3845bb1a69ca60da4cc34))
* **auth:** assign WORKSPACE_OWNER role on provisioning, fix settings UX audit, and fix dialog transparency ([#91](https://github.com/dallay/profiletailors.com/issues/91)) ([5b337a0](https://github.com/dallay/profiletailors.com/commit/5b337a084934f382e0170bf4a3a9db06414a7a4c))
* **calendar:** contain scroll to timeline and add thin native scrollbar utility ([#273](https://github.com/dallay/profiletailors.com/issues/273)) ([8457078](https://github.com/dallay/profiletailors.com/commit/8457078acd73d4ef7304348e7dc08b56ebb867c4))
* **chart:** restore missing Vue imports in ChartContainer ([b35d64c](https://github.com/dallay/profiletailors.com/commit/b35d64cd8f181ad5b5b298952ecdf3682c5ac1a7))
* **ci:** update stale workflow to common-actions v2.2.2 ([#269](https://github.com/dallay/profiletailors.com/issues/269)) ([d0a58ae](https://github.com/dallay/profiletailors.com/commit/d0a58ae65e8e71cd2633de344533d6d03740e065))
* **composer:** wire selectedChannelId into media picker and fix effectiveAttachmentLimit ([ee509e4](https://github.com/dallay/profiletailors.com/commit/ee509e40a8276ffa41aba61952d2b86807b88ef9))
* **deps:** address ~74 Dependabot alerts - Spring Boot, Jackson, Netty, Astro, vite, undici, hono ([#162](https://github.com/dallay/profiletailors.com/issues/162)) ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* **devops:** move backend HTTP port to 7638 (SMP_BACKEND_PORT) ([3abccd7](https://github.com/dallay/profiletailors.com/commit/3abccd78fdcb6b7a89278ab336d6d4a8fc1823a7))
* **e2e:** restore composer media attachments fixtures and add E2E suite ([#272](https://github.com/dallay/profiletailors.com/issues/272)) ([b11b18b](https://github.com/dallay/profiletailors.com/commit/b11b18bf7ac874713c2357886ee069581137a41f))
* **i18n:** update Spanish translation for endpoint ([#74](https://github.com/dallay/profiletailors.com/issues/74)) ([117db77](https://github.com/dallay/profiletailors.com/commit/117db77ba3d9352c89ed2a8905e3bb09d2816927))
* **media:** transactional boundaries, compensation semantics, and CI alignment ([#229](https://github.com/dallay/profiletailors.com/issues/229)) ([405d8bc](https://github.com/dallay/profiletailors.com/commit/405d8bcd0bebe6d25d76aa4603166a9c326cd875))
* **platform:** enable rate limiting by default ([#182](https://github.com/dallay/profiletailors.com/issues/182)) ([0f2adf6](https://github.com/dallay/profiletailors.com/commit/0f2adf64baa1b2e4ae0ef0e4f23c49394054b728))
* **publishing:** reconcile backend publication identity on create and quick-create ([65a5d7c](https://github.com/dallay/profiletailors.com/commit/65a5d7c7315900c9ea619753d60946b52afbd93f))
* **publishing:** tri-state assetIds PATCH with edit hydration ([#223](https://github.com/dallay/profiletailors.com/issues/223)) ([2961147](https://github.com/dallay/profiletailors.com/commit/2961147b150423a48e8735997f9a79164d392fda))
* **quality:** align coverage reporting ([#116](https://github.com/dallay/profiletailors.com/issues/116)) ([62fed0f](https://github.com/dallay/profiletailors.com/commit/62fed0f78229de456186d08632d3b68ccb8c6a48))
* **quality:** resolve 14 SonarCloud issues blocking quality gate ([#436](https://github.com/dallay/profiletailors.com/issues/436)) ([1593ce7](https://github.com/dallay/profiletailors.com/commit/1593ce795490429a4703991422a559debb50923b))
* **quality:** resolve 3 SonarCloud issues blocking quality gate ([#437](https://github.com/dallay/profiletailors.com/issues/437)) ([f3de1a8](https://github.com/dallay/profiletailors.com/commit/f3de1a8f5721ea6447826ea0709b30649011c818))
* **quality:** resolve all SonarQube violations to pass quality gate ([#339](https://github.com/dallay/profiletailors.com/issues/339)) ([50bbf80](https://github.com/dallay/profiletailors.com/commit/50bbf80711f28f3d2eb5f80dddb5e5355291c875))
* resolve 19 SonarQube code smells and align Codecov exclusions ([#159](https://github.com/dallay/profiletailors.com/issues/159)) ([a8abfad](https://github.com/dallay/profiletailors.com/commit/a8abfad3e753c517956d875f00289198b8326779))
* **scheduler:** prevent scheduling publications in the past with 5-minute grace period ([#93](https://github.com/dallay/profiletailors.com/issues/93)) ([d7d866f](https://github.com/dallay/profiletailors.com/commit/d7d866f1ffd8444b9d70dc7935ff86a67eb79741))
* **scheduler:** restore post detail URL state ([d4ace06](https://github.com/dallay/profiletailors.com/commit/d4ace06b85ff01073d0572cf7d10e5271d225850))
* **security:** resolve 3 open code scanning alerts ([#187](https://github.com/dallay/profiletailors.com/issues/187)) ([1a9c580](https://github.com/dallay/profiletailors.com/commit/1a9c5809c616173a8afcde9a5256ac4b220eea0f))
* shell injection in setup-frontend GitHub Action — use env var ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* shell injection in setup-frontend GitHub Action — use env var ([a8abfad](https://github.com/dallay/profiletailors.com/commit/a8abfad3e753c517956d875f00289198b8326779))
* shell injection in setup-frontend GitHub Action — use env var ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))
* shell injection in setup-frontend GitHub Action — use env var ([ae84056](https://github.com/dallay/profiletailors.com/commit/ae8405615b105b9a0443f7262b130205251f4feb))
* Sidebar Responsiveness and Persistence ([#204](https://github.com/dallay/profiletailors.com/issues/204)) ([021b2b3](https://github.com/dallay/profiletailors.com/commit/021b2b302e8fcdf91e708a58c6230999f4f4aabf))
* **sonar:** quality gate - exclude shadcn wrappers, add coverage test, set v0.1.0 baseline ([#151](https://github.com/dallay/profiletailors.com/issues/151)) ([b5b2227](https://github.com/dallay/profiletailors.com/commit/b5b2227b4756b96ff9c3a0f2e5a3f03e622f5bd1))
* **sonar:** resolve SonarQube Quality Gate issues ([#411](https://github.com/dallay/profiletailors.com/issues/411)) ([2ddc7df](https://github.com/dallay/profiletailors.com/commit/2ddc7df2fe7a7611a7bc92cc8969d05332c76223))
* **test:** drop webkit from dashboard e2e matrix ([#331](https://github.com/dallay/profiletailors.com/issues/331)) ([a3909eb](https://github.com/dallay/profiletailors.com/commit/a3909eb63a3425d18112266f3b92c3785b98777f))
* **tests:** update proxyImageUrl test for improved readability ([7320f53](https://github.com/dallay/profiletailors.com/commit/7320f5356c1429e4b3df7613c4c0b79501e66c1c))
* **test:** unskip composer media e2e tests ([#343](https://github.com/dallay/profiletailors.com/issues/343)) ([bd15351](https://github.com/dallay/profiletailors.com/commit/bd153512f002dbbbc8d261110829536535af21a6))
* **test:** update CreatePostModal tests for channel-specific attachment limits ([e8b469d](https://github.com/dallay/profiletailors.com/commit/e8b469d9815595621518ab5cdfb51e2b6806b32a))
* unsplash image flow ([#329](https://github.com/dallay/profiletailors.com/issues/329)) ([e46ef4f](https://github.com/dallay/profiletailors.com/commit/e46ef4f723f9a7ed428042ab6cb85e16ffc26180))
* **ux:** prevent week grid overflow and add toast feedback on post creation ([#280](https://github.com/dallay/profiletailors.com/issues/280)) ([6146bf4](https://github.com/dallay/profiletailors.com/commit/6146bf425e4cbfefd53c4e1468a7fb726498508c))
* **web:** add missing vue imports in ChartContainer (toRefs, useId, computed, HTMLAttributes) ([40b8e90](https://github.com/dallay/profiletailors.com/commit/40b8e900261c62af0d2b306e4da6860c0b71f16e))


### Refactoring

* **composer:** extract media picker orchestration into useComposerMediaPicker composable ([68c05a0](https://github.com/dallay/profiletailors.com/commit/68c05a06e2bf52d6f1a43b0a47ddce7c1133043f))
* Remove unsplashProviderEnabled feature flag — enable Unsplash permanently ([#294](https://github.com/dallay/profiletailors.com/issues/294)) ([16b2e9a](https://github.com/dallay/profiletailors.com/commit/16b2e9a04b1af42120d0a9508f6bf3fcf776c747))
* separate authorization domain interfaces and clean up unused code ([#18](https://github.com/dallay/profiletailors.com/issues/18)) ([9171a17](https://github.com/dallay/profiletailors.com/commit/9171a177f59949fd86611cd3f39dd94ae1f933ba))
* **web:** restructure sidebar into modular components ([#30](https://github.com/dallay/profiletailors.com/issues/30)) ([aa51b4e](https://github.com/dallay/profiletailors.com/commit/aa51b4ec39bdf2f3936038ac4b09e87185997673))


### Documentation

* reconcile documentation with current implementation ([#393](https://github.com/dallay/profiletailors.com/issues/393)) ([9946d90](https://github.com/dallay/profiletailors.com/commit/9946d90323785d392312be9447baab217bdf6edd))

## Changelog

All notable changes to the Profile Tailors web application will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).
