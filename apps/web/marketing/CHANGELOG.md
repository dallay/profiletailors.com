# Changelog

## [0.2.0](https://github.com/dallay/profiletailors.com/compare/landing@v0.1.0...landing@v0.2.0) (2026-07-21)


### ⚠ BREAKING CHANGES

* **tests:** RealLinkedInAssetUploader.storage and RealLinkedInPublisher.storage are now Storage? (nullable). The !! call in buildAssetContentEntities is safe because that path is only reached in real mode where storage is always configured.

### Features

* accessibility audit fixes, focus trap composable, and publishing backend improvements ([#139](https://github.com/dallay/profiletailors.com/issues/139)) ([67c9444](https://github.com/dallay/profiletailors.com/commit/67c94447cdc224652788cdc355268fb4ab63f4c0))
* **animations:** add scroll-reveal CSS with reduced-motion support ([150e161](https://github.com/dallay/profiletailors.com/commit/150e161e5c90c095e0ebb79dc390ec657dae29e6))
* **animations:** add scroll-reveal IntersectionObserver module ([95cf3c1](https://github.com/dallay/profiletailors.com/commit/95cf3c17cb722af5e60843915e2d09fe4f88210e))
* **animations:** add scroll-reveal to features and footer ([5f53229](https://github.com/dallay/profiletailors.com/commit/5f5322900b718002b149adf77b47130636af4e64))
* **animations:** add WAAPI hero on-load animation sequence ([944d3d1](https://github.com/dallay/profiletailors.com/commit/944d3d17a7da62b95302addb573ccbf46b16511e))
* **animations:** wire hero data attributes and script imports ([18d7d0d](https://github.com/dallay/profiletailors.com/commit/18d7d0d8231662383dc2c73f12267f9614ef291e))
* **auth:** harden local auth, stabilize register-flow E2E, add coverage ([#111](https://github.com/dallay/profiletailors.com/issues/111)) ([7098431](https://github.com/dallay/profiletailors.com/commit/709843133719a84407ab93355a600d86ad4a9f23))
* backend feature entitlements, credential lifecycle, and DevSecOps hardening ([8f94cd5](https://github.com/dallay/profiletailors.com/commit/8f94cd5022d2130ec1e8a18bea03d0be4d5858a3))
* **docs:** publish privacy policy, terms, cookie policy and acceptable use policy ([#377](https://github.com/dallay/profiletailors.com/issues/377)) ([0175492](https://github.com/dallay/profiletailors.com/commit/0175492ea4f6da139c37188e0f828bc58e68ecfa))
* expand app shell, API versioning docs, and backend test coverage ([#29](https://github.com/dallay/profiletailors.com/issues/29)) ([2318f50](https://github.com/dallay/profiletailors.com/commit/2318f50fee6404116bc195da6552d47ac43b559b))
* **landing:** migrate to @dallay/astro-icon and shared assets integration ([8086dae](https://github.com/dallay/profiletailors.com/commit/8086dae856c52850eb71b12b67e7e742e434e8a7))
* **layout:** add Analytics component to the main layout ([275c0b0](https://github.com/dallay/profiletailors.com/commit/275c0b052abf89fc774d14c12f8c87e5219ca4ff))
* **lead-capture:** shared domain modules for waitlist MVP ([#340](https://github.com/dallay/profiletailors.com/issues/340)) ([afb53d1](https://github.com/dallay/profiletailors.com/commit/afb53d1428afbc6a977b614a620e1d2d07c80f0a))
* **lead-capture:** wire marketing waitlist form and finalize docs (DALLAY-441, DALLAY-442, DALLAY-443) ([#382](https://github.com/dallay/profiletailors.com/issues/382)) ([0e1501a](https://github.com/dallay/profiletailors.com/commit/0e1501aa5df79231d0c88a8da8d0eb59bb507d78))
* **legal:** migrate policy content to Astro Content Collections and approve publication ([#389](https://github.com/dallay/profiletailors.com/issues/389)) ([cd8aa74](https://github.com/dallay/profiletailors.com/commit/cd8aa74b954630ecfcc6b2ba6b17c648482a01ae))
* **marketing:** accessibility improvements and biome config ([7c6afca](https://github.com/dallay/profiletailors.com/commit/7c6afca8f31c4cb829b8ca3acb551891fd642b41))
* **marketing:** add complete SEO and Open Graph meta tags ([e755957](https://github.com/dallay/profiletailors.com/commit/e755957c44408d86ea2c9fb7a10ac8451f622a8f))
* **marketing:** bootstrap Astro bilingual landing site ([3d66593](https://github.com/dallay/profiletailors.com/commit/3d665936a448cbf38a6d6cf9acfd265dc6c1a5f1))
* **media,ci:** workspace-level CAS media dedup + Postgres Testcontainers + detekt v2 + Biome root (size-exception) ([#174](https://github.com/dallay/profiletailors.com/issues/174)) ([baac461](https://github.com/dallay/profiletailors.com/commit/baac461c2e933991f9cbfa8c2493465cea512bbf))
* **media:** add local auth and media library workflows ([#120](https://github.com/dallay/profiletailors.com/issues/120)) ([6e70486](https://github.com/dallay/profiletailors.com/commit/6e704868f3d1003d58973164f6fcbc0502207951))
* **media:** integrate Unsplash as first media provider ([#249](https://github.com/dallay/profiletailors.com/issues/249)) ([d06f833](https://github.com/dallay/profiletailors.com/commit/d06f83391eb2cee0f38263a1a1f99c31f8927a61))
* **privacy:** implement DSAR workflows for GDPR/CCPA compliance ([#392](https://github.com/dallay/profiletailors.com/issues/392)) ([9c46ac1](https://github.com/dallay/profiletailors.com/commit/9c46ac1d458b2b699921e1249d1181b9aa1d6eb9))
* **scheduler:** E2E tests, CI workflow, and full-stack integration ([#101](https://github.com/dallay/profiletailors.com/issues/101)) ([12fd5a8](https://github.com/dallay/profiletailors.com/commit/12fd5a82fe07855758c8e549bd7c4f9a64c12977))
* **tests:** bootstrap Vitest for Vue app + backend publishing test coverage ([#64](https://github.com/dallay/profiletailors.com/issues/64)) ([5c716e7](https://github.com/dallay/profiletailors.com/commit/5c716e750722e50b9d7b679b675adfd537f64b5b))


### Bug Fixes

* 13 code-review findings across apps/web/app and openspec/specs ([#156](https://github.com/dallay/profiletailors.com/issues/156)) ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))
* apply CodeRabbit auto-fixes ([e55af1a](https://github.com/dallay/profiletailors.com/commit/e55af1a8c6f8e647909b34810aecb278035e48f0))
* **deps:** address ~74 Dependabot alerts - Spring Boot, Jackson, Netty, Astro, vite, undici, hono ([#162](https://github.com/dallay/profiletailors.com/issues/162)) ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* **marketing:** correct shared assets path from apps/web/marketing to repo root ([185c8ba](https://github.com/dallay/profiletailors.com/commit/185c8ba84a0a38a2b386f5ce873d0ab9169923b3))
* **quality:** resolve all SonarQube violations to pass quality gate ([#339](https://github.com/dallay/profiletailors.com/issues/339)) ([50bbf80](https://github.com/dallay/profiletailors.com/commit/50bbf80711f28f3d2eb5f80dddb5e5355291c875))
* resolve code scanning and dependabot security alerts ([0d5fc77](https://github.com/dallay/profiletailors.com/commit/0d5fc779d135b6d24e574b532cc1da55a125add5))
* resolve SonarQube violations across backend and CI/CD pipelines ([#94](https://github.com/dallay/profiletailors.com/issues/94)) ([e6993f7](https://github.com/dallay/profiletailors.com/commit/e6993f7fc9f15da763be869d401a86f1e5321e5e))
* shell injection in setup-frontend GitHub Action — use env var ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* shell injection in setup-frontend GitHub Action — use env var ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))


### Refactoring

* separate authorization domain interfaces and clean up unused code ([#18](https://github.com/dallay/profiletailors.com/issues/18)) ([9171a17](https://github.com/dallay/profiletailors.com/commit/9171a177f59949fd86611cd3f39dd94ae1f933ba))


### Documentation

* reconcile documentation with current implementation ([f9b8682](https://github.com/dallay/profiletailors.com/commit/f9b8682ca8834bf452c11c2495b5ad9897bfab1a))
* reconcile documentation with current implementation ([#393](https://github.com/dallay/profiletailors.com/issues/393)) ([9946d90](https://github.com/dallay/profiletailors.com/commit/9946d90323785d392312be9447baab217bdf6edd))

## [0.1.0](https://github.com/dallay/profiletailors.com/compare/landing@v0.0.1...landing@v0.1.0) (2026-07-21)


### ⚠ BREAKING CHANGES

* **tests:** RealLinkedInAssetUploader.storage and RealLinkedInPublisher.storage are now Storage? (nullable). The !! call in buildAssetContentEntities is safe because that path is only reached in real mode where storage is always configured.

### Features

* accessibility audit fixes, focus trap composable, and publishing backend improvements ([#139](https://github.com/dallay/profiletailors.com/issues/139)) ([67c9444](https://github.com/dallay/profiletailors.com/commit/67c94447cdc224652788cdc355268fb4ab63f4c0))
* **animations:** add scroll-reveal CSS with reduced-motion support ([150e161](https://github.com/dallay/profiletailors.com/commit/150e161e5c90c095e0ebb79dc390ec657dae29e6))
* **animations:** add scroll-reveal IntersectionObserver module ([95cf3c1](https://github.com/dallay/profiletailors.com/commit/95cf3c17cb722af5e60843915e2d09fe4f88210e))
* **animations:** add scroll-reveal to features and footer ([5f53229](https://github.com/dallay/profiletailors.com/commit/5f5322900b718002b149adf77b47130636af4e64))
* **animations:** add WAAPI hero on-load animation sequence ([944d3d1](https://github.com/dallay/profiletailors.com/commit/944d3d17a7da62b95302addb573ccbf46b16511e))
* **animations:** wire hero data attributes and script imports ([18d7d0d](https://github.com/dallay/profiletailors.com/commit/18d7d0d8231662383dc2c73f12267f9614ef291e))
* **auth:** harden local auth, stabilize register-flow E2E, add coverage ([#111](https://github.com/dallay/profiletailors.com/issues/111)) ([7098431](https://github.com/dallay/profiletailors.com/commit/709843133719a84407ab93355a600d86ad4a9f23))
* backend feature entitlements, credential lifecycle, and DevSecOps hardening ([8f94cd5](https://github.com/dallay/profiletailors.com/commit/8f94cd5022d2130ec1e8a18bea03d0be4d5858a3))
* **docs:** publish privacy policy, terms, cookie policy and acceptable use policy ([#377](https://github.com/dallay/profiletailors.com/issues/377)) ([0175492](https://github.com/dallay/profiletailors.com/commit/0175492ea4f6da139c37188e0f828bc58e68ecfa))
* expand app shell, API versioning docs, and backend test coverage ([#29](https://github.com/dallay/profiletailors.com/issues/29)) ([2318f50](https://github.com/dallay/profiletailors.com/commit/2318f50fee6404116bc195da6552d47ac43b559b))
* **landing:** migrate to @dallay/astro-icon and shared assets integration ([8086dae](https://github.com/dallay/profiletailors.com/commit/8086dae856c52850eb71b12b67e7e742e434e8a7))
* **layout:** add Analytics component to the main layout ([275c0b0](https://github.com/dallay/profiletailors.com/commit/275c0b052abf89fc774d14c12f8c87e5219ca4ff))
* **lead-capture:** shared domain modules for waitlist MVP ([#340](https://github.com/dallay/profiletailors.com/issues/340)) ([afb53d1](https://github.com/dallay/profiletailors.com/commit/afb53d1428afbc6a977b614a620e1d2d07c80f0a))
* **lead-capture:** wire marketing waitlist form and finalize docs (DALLAY-441, DALLAY-442, DALLAY-443) ([#382](https://github.com/dallay/profiletailors.com/issues/382)) ([0e1501a](https://github.com/dallay/profiletailors.com/commit/0e1501aa5df79231d0c88a8da8d0eb59bb507d78))
* **legal:** migrate policy content to Astro Content Collections and approve publication ([#389](https://github.com/dallay/profiletailors.com/issues/389)) ([cd8aa74](https://github.com/dallay/profiletailors.com/commit/cd8aa74b954630ecfcc6b2ba6b17c648482a01ae))
* **marketing:** accessibility improvements and biome config ([7c6afca](https://github.com/dallay/profiletailors.com/commit/7c6afca8f31c4cb829b8ca3acb551891fd642b41))
* **marketing:** add complete SEO and Open Graph meta tags ([e755957](https://github.com/dallay/profiletailors.com/commit/e755957c44408d86ea2c9fb7a10ac8451f622a8f))
* **marketing:** bootstrap Astro bilingual landing site ([3d66593](https://github.com/dallay/profiletailors.com/commit/3d665936a448cbf38a6d6cf9acfd265dc6c1a5f1))
* **media,ci:** workspace-level CAS media dedup + Postgres Testcontainers + detekt v2 + Biome root (size-exception) ([#174](https://github.com/dallay/profiletailors.com/issues/174)) ([baac461](https://github.com/dallay/profiletailors.com/commit/baac461c2e933991f9cbfa8c2493465cea512bbf))
* **media:** add local auth and media library workflows ([#120](https://github.com/dallay/profiletailors.com/issues/120)) ([6e70486](https://github.com/dallay/profiletailors.com/commit/6e704868f3d1003d58973164f6fcbc0502207951))
* **media:** integrate Unsplash as first media provider ([#249](https://github.com/dallay/profiletailors.com/issues/249)) ([d06f833](https://github.com/dallay/profiletailors.com/commit/d06f83391eb2cee0f38263a1a1f99c31f8927a61))
* **privacy:** implement DSAR workflows for GDPR/CCPA compliance ([#392](https://github.com/dallay/profiletailors.com/issues/392)) ([9c46ac1](https://github.com/dallay/profiletailors.com/commit/9c46ac1d458b2b699921e1249d1181b9aa1d6eb9))
* **scheduler:** E2E tests, CI workflow, and full-stack integration ([#101](https://github.com/dallay/profiletailors.com/issues/101)) ([12fd5a8](https://github.com/dallay/profiletailors.com/commit/12fd5a82fe07855758c8e549bd7c4f9a64c12977))
* **tests:** bootstrap Vitest for Vue app + backend publishing test coverage ([#64](https://github.com/dallay/profiletailors.com/issues/64)) ([5c716e7](https://github.com/dallay/profiletailors.com/commit/5c716e750722e50b9d7b679b675adfd537f64b5b))


### Bug Fixes

* 13 code-review findings across apps/web/app and openspec/specs ([#156](https://github.com/dallay/profiletailors.com/issues/156)) ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))
* apply CodeRabbit auto-fixes ([e55af1a](https://github.com/dallay/profiletailors.com/commit/e55af1a8c6f8e647909b34810aecb278035e48f0))
* **deps:** address ~74 Dependabot alerts - Spring Boot, Jackson, Netty, Astro, vite, undici, hono ([#162](https://github.com/dallay/profiletailors.com/issues/162)) ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* **marketing:** correct shared assets path from apps/web/marketing to repo root ([185c8ba](https://github.com/dallay/profiletailors.com/commit/185c8ba84a0a38a2b386f5ce873d0ab9169923b3))
* **quality:** resolve all SonarQube violations to pass quality gate ([#339](https://github.com/dallay/profiletailors.com/issues/339)) ([50bbf80](https://github.com/dallay/profiletailors.com/commit/50bbf80711f28f3d2eb5f80dddb5e5355291c875))
* resolve code scanning and dependabot security alerts ([0d5fc77](https://github.com/dallay/profiletailors.com/commit/0d5fc779d135b6d24e574b532cc1da55a125add5))
* resolve SonarQube violations across backend and CI/CD pipelines ([#94](https://github.com/dallay/profiletailors.com/issues/94)) ([e6993f7](https://github.com/dallay/profiletailors.com/commit/e6993f7fc9f15da763be869d401a86f1e5321e5e))
* shell injection in setup-frontend GitHub Action — use env var ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* shell injection in setup-frontend GitHub Action — use env var ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))


### Refactoring

* separate authorization domain interfaces and clean up unused code ([#18](https://github.com/dallay/profiletailors.com/issues/18)) ([9171a17](https://github.com/dallay/profiletailors.com/commit/9171a177f59949fd86611cd3f39dd94ae1f933ba))


### Documentation

* reconcile documentation with current implementation ([f9b8682](https://github.com/dallay/profiletailors.com/commit/f9b8682ca8834bf452c11c2495b5ad9897bfab1a))
* reconcile documentation with current implementation ([#393](https://github.com/dallay/profiletailors.com/issues/393)) ([9946d90](https://github.com/dallay/profiletailors.com/commit/9946d90323785d392312be9447baab217bdf6edd))

## [0.1.0](https://github.com/dallay/profiletailors.com/compare/landing@v0.0.1...landing@v0.1.0) (2026-07-18)


### ⚠ BREAKING CHANGES

* **tests:** RealLinkedInAssetUploader.storage and RealLinkedInPublisher.storage are now Storage? (nullable). The !! call in buildAssetContentEntities is safe because that path is only reached in real mode where storage is always configured.

### Features

* accessibility audit fixes, focus trap composable, and publishing backend improvements ([#139](https://github.com/dallay/profiletailors.com/issues/139)) ([67c9444](https://github.com/dallay/profiletailors.com/commit/67c94447cdc224652788cdc355268fb4ab63f4c0))
* **animations:** add scroll-reveal CSS with reduced-motion support ([150e161](https://github.com/dallay/profiletailors.com/commit/150e161e5c90c095e0ebb79dc390ec657dae29e6))
* **animations:** add scroll-reveal IntersectionObserver module ([95cf3c1](https://github.com/dallay/profiletailors.com/commit/95cf3c17cb722af5e60843915e2d09fe4f88210e))
* **animations:** add scroll-reveal to features and footer ([5f53229](https://github.com/dallay/profiletailors.com/commit/5f5322900b718002b149adf77b47130636af4e64))
* **animations:** add WAAPI hero on-load animation sequence ([944d3d1](https://github.com/dallay/profiletailors.com/commit/944d3d17a7da62b95302addb573ccbf46b16511e))
* **animations:** wire hero data attributes and script imports ([18d7d0d](https://github.com/dallay/profiletailors.com/commit/18d7d0d8231662383dc2c73f12267f9614ef291e))
* **auth:** harden local auth, stabilize register-flow E2E, add coverage ([#111](https://github.com/dallay/profiletailors.com/issues/111)) ([7098431](https://github.com/dallay/profiletailors.com/commit/709843133719a84407ab93355a600d86ad4a9f23))
* backend feature entitlements, credential lifecycle, and DevSecOps hardening ([8f94cd5](https://github.com/dallay/profiletailors.com/commit/8f94cd5022d2130ec1e8a18bea03d0be4d5858a3))
* **docs:** publish privacy policy, terms, cookie policy and acceptable use policy ([#377](https://github.com/dallay/profiletailors.com/issues/377)) ([0175492](https://github.com/dallay/profiletailors.com/commit/0175492ea4f6da139c37188e0f828bc58e68ecfa))
* expand app shell, API versioning docs, and backend test coverage ([#29](https://github.com/dallay/profiletailors.com/issues/29)) ([2318f50](https://github.com/dallay/profiletailors.com/commit/2318f50fee6404116bc195da6552d47ac43b559b))
* **landing:** migrate to @dallay/astro-icon and shared assets integration ([8086dae](https://github.com/dallay/profiletailors.com/commit/8086dae856c52850eb71b12b67e7e742e434e8a7))
* **layout:** add Analytics component to the main layout ([275c0b0](https://github.com/dallay/profiletailors.com/commit/275c0b052abf89fc774d14c12f8c87e5219ca4ff))
* **lead-capture:** shared domain modules for waitlist MVP ([#340](https://github.com/dallay/profiletailors.com/issues/340)) ([afb53d1](https://github.com/dallay/profiletailors.com/commit/afb53d1428afbc6a977b614a620e1d2d07c80f0a))
* **lead-capture:** wire marketing waitlist form and finalize docs (DALLAY-441, DALLAY-442, DALLAY-443) ([#382](https://github.com/dallay/profiletailors.com/issues/382)) ([0e1501a](https://github.com/dallay/profiletailors.com/commit/0e1501aa5df79231d0c88a8da8d0eb59bb507d78))
* **marketing:** accessibility improvements and biome config ([7c6afca](https://github.com/dallay/profiletailors.com/commit/7c6afca8f31c4cb829b8ca3acb551891fd642b41))
* **marketing:** add complete SEO and Open Graph meta tags ([e755957](https://github.com/dallay/profiletailors.com/commit/e755957c44408d86ea2c9fb7a10ac8451f622a8f))
* **marketing:** bootstrap Astro bilingual landing site ([3d66593](https://github.com/dallay/profiletailors.com/commit/3d665936a448cbf38a6d6cf9acfd265dc6c1a5f1))
* **media,ci:** workspace-level CAS media dedup + Postgres Testcontainers + detekt v2 + Biome root (size-exception) ([#174](https://github.com/dallay/profiletailors.com/issues/174)) ([baac461](https://github.com/dallay/profiletailors.com/commit/baac461c2e933991f9cbfa8c2493465cea512bbf))
* **media:** add local auth and media library workflows ([#120](https://github.com/dallay/profiletailors.com/issues/120)) ([6e70486](https://github.com/dallay/profiletailors.com/commit/6e704868f3d1003d58973164f6fcbc0502207951))
* **media:** integrate Unsplash as first media provider ([#249](https://github.com/dallay/profiletailors.com/issues/249)) ([d06f833](https://github.com/dallay/profiletailors.com/commit/d06f83391eb2cee0f38263a1a1f99c31f8927a61))
* **scheduler:** E2E tests, CI workflow, and full-stack integration ([#101](https://github.com/dallay/profiletailors.com/issues/101)) ([12fd5a8](https://github.com/dallay/profiletailors.com/commit/12fd5a82fe07855758c8e549bd7c4f9a64c12977))
* **tests:** bootstrap Vitest for Vue app + backend publishing test coverage ([#64](https://github.com/dallay/profiletailors.com/issues/64)) ([5c716e7](https://github.com/dallay/profiletailors.com/commit/5c716e750722e50b9d7b679b675adfd537f64b5b))


### Bug Fixes

* 13 code-review findings across apps/web/app and openspec/specs ([#156](https://github.com/dallay/profiletailors.com/issues/156)) ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))
* apply CodeRabbit auto-fixes ([e55af1a](https://github.com/dallay/profiletailors.com/commit/e55af1a8c6f8e647909b34810aecb278035e48f0))
* **deps:** address ~74 Dependabot alerts - Spring Boot, Jackson, Netty, Astro, vite, undici, hono ([#162](https://github.com/dallay/profiletailors.com/issues/162)) ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* **marketing:** correct shared assets path from apps/web/marketing to repo root ([185c8ba](https://github.com/dallay/profiletailors.com/commit/185c8ba84a0a38a2b386f5ce873d0ab9169923b3))
* **quality:** resolve all SonarQube violations to pass quality gate ([#339](https://github.com/dallay/profiletailors.com/issues/339)) ([50bbf80](https://github.com/dallay/profiletailors.com/commit/50bbf80711f28f3d2eb5f80dddb5e5355291c875))
* resolve code scanning and dependabot security alerts ([0d5fc77](https://github.com/dallay/profiletailors.com/commit/0d5fc779d135b6d24e574b532cc1da55a125add5))
* resolve SonarQube violations across backend and CI/CD pipelines ([#94](https://github.com/dallay/profiletailors.com/issues/94)) ([e6993f7](https://github.com/dallay/profiletailors.com/commit/e6993f7fc9f15da763be869d401a86f1e5321e5e))
* shell injection in setup-frontend GitHub Action — use env var ([2935ea6](https://github.com/dallay/profiletailors.com/commit/2935ea644863af36b16d3531c707f846f40e5630))
* shell injection in setup-frontend GitHub Action — use env var ([5a14905](https://github.com/dallay/profiletailors.com/commit/5a14905ecb8098798fd324f8d4d64548cf2611ef))


### Refactoring

* separate authorization domain interfaces and clean up unused code ([#18](https://github.com/dallay/profiletailors.com/issues/18)) ([9171a17](https://github.com/dallay/profiletailors.com/commit/9171a177f59949fd86611cd3f39dd94ae1f933ba))


### Documentation

* reconcile documentation with current implementation ([f9b8682](https://github.com/dallay/profiletailors.com/commit/f9b8682ca8834bf452c11c2495b5ad9897bfab1a))
