# Legal Documents — Open Source + Operator-Hosted Instance Model

> **For agentic workers:** Implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite all four legal documents (Privacy, Terms, AUP, Cookies) to reflect Profile
Tailors' current reality: an AGPL-3.0 open-source project with an operator-hosted public instance,
removing all B2B SaaS fiction (DPA, enterprise subprocessors, SLA commitments, etc.).

**Architecture:** Content lives in `apps/web/marketing/src/i18n/{en,es}.ts` under `legal.*` keys,
rendered by Astro page components via `renderLegalText()`. The publication gate in
`apps/web/marketing/src/legal/legal-publication.ts` controls visibility. `docs/compliance/` is the
future compliance baseline, unmodified.

**Tech Stack:** TypeScript, Astro, i18n key-value content.

---

### Task 1: Privacy Policy — rewrite EN content

**Files:**
- Modify: `apps/web/marketing/src/i18n/en.ts` — `legal.privacy.*` keys only

- [ ] **Step 1: Rewrite `en.ts` `legal.privacy` section**

Replace the current 10-section draft with content describing the operator-hosted model:

```typescript
privacy: {
  title: 'Privacy Policy',
  description: 'How Profile Tailors handles personal data when you use the operator-hosted instance.',
  lastUpdated: '22 July 2026',
  section1: '1. Who Operates This Instance',
  section2: '2. What Personal Data We Collect',
  section3: '3. Why We Process Your Data and Our Legal Basis',
  section4: '4. Who We Share Your Data With',
  section5: '5. How Long We Keep Your Data',
  section6: '6. Where Your Data Is Processed',
  section7: '7. Your Rights',
  section8: '8. Cookies and Browser Storage',
  section9: '9. Changes to This Policy',
  section10: '10. Contact',
  intro:
    'This policy describes how the operator of this Profile Tailors instance ('the operator', 'we', 'us') processes personal data when you use the hosted application available at this domain. The Profile Tailors software is distributed under the AGPL-3.0 licence; this policy applies to the instance operated here, not to self-hosted deployments.\n\nIf you operate your own instance, your privacy relationship with your users is governed by your own policies.\n\nThis policy reflects the current configuration of this instance. It will be updated if the services, providers, or data practices change.',
  dataController:
    'This instance is operated by an individual acting as the data controller under applicable data protection law. The operator determines the purposes and means of processing for this instance.\n\nThe operator\'s identity and contact details are listed in Section 10.\n\n**Processor relationship:** When you use this instance to schedule and publish content to third-party platforms (such as LinkedIn), you determine the content and the publication instruction. The operator processes that content on your instructions as a processor for that specific purpose. For all other processing (account management, security, service operations), the operator acts as controller.',
  dataCollected:
    'When you use this instance, we collect:\n\n- **Account data:** email address, display name, avatar, and password (stored as a salted hash).\n- **Session data:** authentication tokens, refresh tokens, and session activity logs.\n- **Workspace and membership data:** workspace name, member list, roles, and permissions.\n- **Social account connections:** OAuth tokens and account identifiers for connected social platforms (e.g., LinkedIn).\n- **Content and media:** posts, drafts, scheduled content, uploaded media files, and publication metadata.\n- **API keys and credentials:** hashed API key verifiers, credential metadata, and access logs.\n- **Audit and security logs:** IP addresses, user-agent strings, request paths, timestamps, and access patterns.\n- **Support and waitlist data:** waitlist registration email, consent records, and any communications you send us.\n\nWe do not intentionally collect special categories of data (health, biometrics, political opinions, etc.). Do not upload such data to this instance.',
  dataUsage:
    'We process your data for these purposes:\n\n| Purpose | Legal basis |\n|---|---|\n| Account creation, authentication, and security | Contract necessity (performance of the service you signed up for) |\n| Providing the content scheduling and publishing service | Contract necessity |\n| Processing your publishing instructions to third-party platforms (e.g., LinkedIn) | Your instruction (controller-to-processor direction) |\n| Service security, abuse prevention, and incident response | Legitimate interest — protecting the service and its users |\n| Communications about service changes, security, or support | Contract necessity or legitimate interest |\n| Optional waitlist/marketing communications | Your consent (separately obtained) |\n| Service improvement and analytics (non-identifying) | Legitimate interest — improving the service |\n\nWhere we rely on legitimate interest, you may object. Where we rely on consent, you may withdraw at any time.',
  dataSharing:
    'We share your data only with these categories of recipients:\n\n**Infrastructure providers:**\n- **Cloudflare, Inc.** — content delivery network, DNS, and edge security. Cloudflare acts as a processor. Data: IP address, request metadata, cached content. Region: global edge network.\n- **[VPS Provider]** — application hosting and PostgreSQL database. Acts as a processor. Data: all data stored or processed by the application. Region: [VPS location].\n\n**Integrated platforms (when you use them):**\n- **LinkedIn** — when you connect a LinkedIn account, your content and credentials are shared with LinkedIn as an independent controller or processor depending on the operation. See LinkedIn\'s privacy policy for details.\n\nWe do not sell your personal data. We do not share data with advertising networks, data brokers, or analytics providers beyond what is described above.\n\nAll infrastructure providers are bound by data processing agreements that require them to protect your data and process it only on our instructions.',
  dataRetention:
    'We retain your data for these periods:\n\n- **Account data:** until you delete your account or the account is terminated.\n- **Session tokens:** up to 7 days (refresh token) or until logout.\n- **Content and media:** until you delete them or your account is terminated.\n- **Published content:** remains on the target platform according to that platform\'s retention.\n- **OAuth credentials:** until you disconnect the social account or delete your Profile Tailors account.\n- **Audit logs:** up to 90 days.\n- **Waitlist data:** until you withdraw consent or the waitlist closes.\n- **Backups:** retained for up to 30 days after deletion, then securely erased.\n\nWhen you delete your account, we initiate deletion of your active data within 30 days, except where we must retain data for legal obligations (e.g., tax records, if applicable).',
  internationalTransfers:
    'Your data is processed in these locations:\n\n- **Application and database:** [VPS location].\n- **CDN and edge network:** Cloudflare global network (data centres worldwide).\n- **Integrated platforms:** LinkedIn servers (location depends on your region and LinkedIn\'s infrastructure).\n\nWhere data is transferred from your country of residence to another country, we ensure appropriate safeguards are in place:\n- **EU/EEA to third countries:** Standard Contractual Clauses (SCCs) with infrastructure providers.\n- **Other regions:** applicable transfer mechanisms as required by local law.\n\nContact us (Section 10) for a copy of the relevant safeguards.',
  yourRights:
    'Depending on your jurisdiction, you may have the following rights regarding your personal data:\n\n- **Access:** request a copy of the data we hold about you.\n- **Rectification:** correct inaccurate or incomplete data.\n- **Erasure:** request deletion of your data, subject to legal retention obligations.\n- **Restriction:** restrict processing in certain circumstances.\n- **Objection:** object to processing based on legitimate interest.\n- **Data portability:** receive your data in a structured, machine-readable format.\n- **Withdraw consent:** where processing is based on consent.\n- **Lodge a complaint:** with your local data protection authority.\n\nTo exercise any of these rights, contact us using the details in Section 10. We will respond within the timeframe required by applicable law.\n\nIf you are in the EU/EEA, you also have the right to lodge a complaint with your local supervisory authority.',
  cookies:
    'This instance uses minimal browser storage:\n\n**Strictly necessary:**\n- `pt_refresh` — HttpOnly cookie for session refresh (7-day expiry, Secure, SameSite=Lax). Cleared on logout.\n- `sidebar_state` — JavaScript-accessible preference cookie for sidebar state (7-day expiry).\n- **Local storage:** theme preference (marketing site), locale and theme settings (dashboard), active workspace, dashboard state, and publication drafts.\n\n**Analytics:**\n- No advertising, tracking, or analytics cookies are used by default. Ahrefs Web Analytics may be loaded conditionally if configured by the operator; its default implementation is cookieless and does not use persistent identifiers.\n\n**Third-party:**\n- When you connect a LinkedIn account, LinkedIn may set its own cookies according to its cookie policy. We do not control LinkedIn\'s cookies.\n\nYou can manage cookies through your browser settings. Blocking strictly necessary cookies may affect service functionality.',
  policyChanges:
    'We may update this policy when our data practices change, new features are added, or legal requirements evolve. Material changes will be notified through the application or via email.\n\nThe current version is always available at `/privacy/`. Your continued use after a change constitutes acceptance of the updated policy.',
  contact:
    'For privacy enquiries, data subject requests, or complaints, contact the operator:\n\n**Yuniel Acosta**\nEmail: [your-email@example.com]\n\nIf you are in the EU/EEA and are not satisfied with our response, you may lodge a complaint with your local data protection authority.',
},
```

- [ ] **Step 2: Verify no TS type errors**

Run: `just frontend-lint` (or `npx tsc --noEmit` if available from the marketing app)
Expected: No type errors in the modified file.

---

### Task 2: Privacy Policy — rewrite ES content

**Files:**
- Modify: `apps/web/marketing/src/i18n/es.ts` — `legal.privacy.*` keys only

- [ ] **Step 1: Rewrite Spanish translation matching EN structure**

Use the same structure as the EN version but translated to Spanish. Maintain same section keys and
data categories. Key translation points:

```typescript
privacy: {
  title: 'Política de Privacidad',
  // ... all keys matching EN structure, in Spanish
  // Terms: 'el operador', 'el responsable', 'usted'
  // Providers: Cloudflare, [VPS Provider], LinkedIn
  // Rights: same structure under GDPR + applicable law
  dataController:
    'Esta instancia es operada por una persona física que actúa como responsable del tratamiento...',
  // etc.
}
```

- [ ] **Step 2: Verify key parity with EN**

Run EN/ES key comparison test (already exists in `utils.test.ts`):
Expected: All keys present and identical structure between EN and ES privacy objects.

---

### Task 3: Terms of Service — rewrite EN content

**Files:**
- Modify: `apps/web/marketing/src/i18n/en.ts` — `legal.terms.*` keys only

- [ ] **Step 1: Rewrite `en.ts` `legal.terms` section**

Replace the current 10-section draft with content distinguishing AGPL software from the hosted
instance:

```typescript
terms: {
  title: 'Terms of Service',
  description: 'Terms governing your use of the operator-hosted Profile Tailors instance.',
  lastUpdated: '22 July 2026',
  section1: '1. Software Licence vs. Hosted Service',
  section2: '2. Accounts and Eligibility',
  section3: '3. Acceptable Use',
  section4: '4. Service Availability and Support',
  section5: '5. Your Content and Intellectual Property',
  section6: '6. Third-Party Integrations',
  section7: '7. Disclaimers and Limitation of Liability',
  section8: '8. Suspension and Termination',
  section9: '9. Governing Law',
  section10: '10. Contact',
  serviceDescription:
    '**Software licence.** The Profile Tailors source code is distributed under the GNU Affero General Public License v3.0 (AGPL-3.0). If you download, modify, or self-host the software, your rights and obligations are governed by that licence.\n\n**Hosted instance.** This document governs your use of the Profile Tailors instance operated at this domain (the "Service"). The operator provides this instance as a convenience; it is not a commercial SaaS offering.\n\nBy creating an account or using the Service, you accept these terms. If you do not agree, do not use the Service.',
  accountTerms:
    '**Eligibility.** You must be at least 16 years old (or the age of digital consent in your country) to use the Service. By creating an account, you confirm that you meet this requirement.\n\n**Registration.** When you create an account, you must provide accurate and complete information. You are responsible for maintaining the confidentiality of your credentials and for all activity under your account.\n\n**Acceptance.** By clicking "Sign up" or equivalent, you enter into these terms. The operator will record the version, timestamp, and locale of the terms you accepted.',
  acceptableUse:
    'You may use the Service only for lawful purposes and in accordance with our Acceptable Use Policy, which is incorporated into these terms by reference.\n\nYou must not:\n- Use the Service to violate any applicable law or regulation.\n- Attempt to disrupt, compromise, or gain unauthorised access to the Service, its infrastructure, or other users\' accounts.\n- Use the Service to send spam, malware, or malicious content.\n- Use the Service to publish content that is illegal, harmful, threatening, abusive, harassing, defamatory, or infringes others\' rights.\n\nViolation of these rules may result in immediate suspension or termination of your access.',
  feesPayment:
    'The Service is currently provided free of charge. The operator reserves the right to introduce fees in the future, with reasonable notice to users.\n\nIf paid plans are introduced, they will be governed by separate terms and you will have the option to stop using the Service rather than accept the new terms.',
  intellectualProperty:
    '**Software.** The Profile Tailors software is licensed under AGPL-3.0. Nothing in these terms restricts rights granted by that licence.\n\n**Your content.** You retain all rights to content you create, upload, or publish through the Service. You grant the operator a limited, non-exclusive licence to process, store, and transmit your content solely to provide the Service to you.\n\n**Service branding.** The "Profile Tailors" name, logo, and domain are the operator\'s property. These terms do not grant you any right to use them.',
  thirdPartyServices:
    'The Service integrates with third-party platforms (e.g., LinkedIn) at your direction. When you connect an account or publish content to a third-party platform:\n- Your use of that platform is governed by its own terms.\n- The operator is not responsible for that platform\'s actions, availability, or data practices.\n- You represent that you have the right to authorise the connection and publication.\n\nCurrently integrated platforms: LinkedIn. Other platforms may be added or removed at the operator\'s discretion.',
  limitationLiability:
    '**Disclaimer of warranties.** THE SERVICE IS PROVIDED "AS IS", WITHOUT WARRANTIES OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT. THIS DOES NOT AFFECT ANY STATUTORY RIGHTS THAT CANNOT BE EXCLUDED UNDER APPLICABLE LAW.\n\n**Limitation of liability.** TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, THE OPERATOR SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES ARISING OUT OF OR RELATED TO YOUR USE OF THE SERVICE.\n\n**No exclusion of non-waivable rights.** Some jurisdictions do not allow the exclusion of certain warranties or limitation of liability. In those jurisdictions, the operator\'s liability is limited to the fullest extent permitted by law. Nothing in these terms excludes or limits liability for death, personal injury, fraud, or gross negligence where such exclusion would be unlawful.',
  termination:
    '**By you.** You may delete your account at any time. Your data will be deleted or anonymised in accordance with the Privacy Policy.\n\n**By the operator.** The operator may suspend or terminate your access if you materially breach these terms or the Acceptable Use Policy, or if required by law. Where practicable, the operator will give you notice and an opportunity to remedy the breach.\n\n**Effect of termination.** Upon termination, your right to use the Service ceases immediately. The operator will delete your data in accordance with the Privacy Policy, except where retention is required by law.\n\n**Survival.** Sections 5 (Your Content), 7 (Disclaimers), 9 (Governing Law), and 10 (Contact) survive termination.',
  governingLaw:
    'These terms are governed by the laws of [Spain / your jurisdiction], without regard to its conflict of laws principles.\n\nIf you are a consumer in the EU/EEA, you may also bring proceedings in your country of residence. Nothing in these terms deprives you of the protection of mandatory consumer protection laws in your country.',
  contact:
    'For questions about these terms, contact:\n\n**Yuniel Acosta**\nEmail: [your-email@example.com]',
},
```

- [ ] **Step 2: Verify no TS type errors**

---

### Task 4: Terms of Service — rewrite ES content

**Files:**
- Modify: `apps/web/marketing/src/i18n/es.ts` — `legal.terms.*` keys only

- [ ] **Step 1: Rewrite Spanish translation matching EN structure**

Same structure as EN, translated to Spanish. Key terms: "software", "instancia alojada",
"operador", "responsabilidad limitada", "licencia AGPL", etc.

- [ ] **Step 2: Verify key parity with EN**

---

### Task 5: Acceptable Use Policy — review and adjust EN/ES

**Files:**
- Modify: `apps/web/marketing/src/i18n/en.ts` — `legal.aup.*` keys
- Modify: `apps/web/marketing/src/i18n/es.ts` — `legal.aup.*` keys

- [ ] **Step 1: Review EN AUP content**

Current EN AUP content is already reasonable and instance-appropriate. Verify it does not contain
B2B/SaaS fiction. The prohibited activities, enforcement proportionality, and reporting sections
are structurally sound. Likely minor edits only (e.g., ensure it references "this instance" not
"the Service").

- [ ] **Step 2: Review ES AUP content**

Same review as EN. Verify parity.

- [ ] **Step 3: Verify key parity between EN/ES**

---

### Task 6: Cookie Policy — review against real implementation EN/ES

**Files:**
- Modify: `apps/web/marketing/src/i18n/en.ts` — `legal.cookies.*` keys
- Modify: `apps/web/marketing/src/i18n/es.ts` — `legal.cookies.*` keys

- [ ] **Step 1: Review EN Cookie Policy content**

Current EN content is already well-researched and evidence-based (it references actual cookies,
local storage items, and Ahrefs conditional behaviour). Verify it does not invent categories or
technologies not actually used. Likely minor adjustments only:
- Remove any "Draft" or "Not in effect" language.
- Ensure `lastUpdated` reflects real date.
- Add reference to the Privacy Policy for more detail.
- Simplify language where it reads like an evidence register rather than a policy.

- [ ] **Step 2: Review ES Cookie Policy content**

Same review as EN. Verify parity.

- [ ] **Step 3: Verify key parity between EN/ES**

---

### Task 7: Legal publication gate — change to APPROVED

**Files:**
- Modify: `apps/web/marketing/src/legal/legal-publication.ts`

- [ ] **Step 1: Change `legalPublicationStatus` to `APPROVED`**

```typescript
export const legalPublicationStatus: LegalPublicationStatus =
  LEGAL_PUBLICATION_STATUS.APPROVED
```

This is the minimal change — a single line. It will cause all four policy pages (privacy, terms,
cookies, acceptable-use) to render their content instead of the `LegalPolicyUnavailable` fallback.

- [ ] **Step 2: Verify no type errors**

Run: `just frontend-lint`
Expected: PASS

---

### Task 8: Update `docs/compliance/` boundary marker

**Files:**
- Modify: `docs/compliance/global-legal-readiness.md` or `docs/compliance/_index.md`
- Or possibly create: `docs/compliance/README.md`

- [ ] **Step 1: Add clear boundary note to compliance docs**

Add a front-matter or header note to one of the compliance docs (or create a README) marking them
as future compliance baseline, not current operational controls. Example header to add at the top of
`docs/compliance/global-legal-readiness.md` (the most top-level doc):

```markdown
> **Boundary:** This document describes a future-state compliance architecture for Profile Tailors
> as a potential commercial SaaS. It does NOT represent currently implemented controls, approved
> providers, or operational policies. The current operational documents are the Privacy Policy,
> Terms of Service, Cookie Policy, and Acceptable Use Policy published on the hosted instance.
```

Or create `docs/compliance/README.md` with a similar note.

---

### Task 9: Verification — end-to-end checklist

**Files:**
- Check: all modified files

- [ ] **Step 1: Verify publication gate renders all pages**

Run the marketing dev server (or build) and confirm:
- `/privacy/` shows content (not "not available")
- `/terms/` shows content
- `/cookies/` shows content
- `/acceptable-use/` shows content
- All ES equivalents show equivalent content

- [ ] **Step 2: Verify no broken links**

Check that `/privacy/`, `/terms/`, `/cookies/`, `/acceptable-use/` are referenced correctly in
footer `legalLinks` and that canonical paths are correct.

- [ ] **Step 3: Run frontend tests**

Run: `just frontend-test`
Expected: All tests pass (including the key parity test `utils.test.ts`)

- [ ] **Step 4: Run frontend lint**

Run: `just frontend-lint`
Expected: PASS

---

## Verification Summary

After all tasks complete, run these commands:
```bash
just frontend-lint
just frontend-test
```

All must pass.

## Post-Implementation

- Update Linear issue DALLAY-500 status to Done.
- Commit all changes with message: `feat(legal): align legal docs with open-source + hosted-instance model`
