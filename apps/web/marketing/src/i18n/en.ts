// src/i18n/en.ts
export const en = {
  nav: {
    name: 'Profile Tailors',
    langSwitch: 'ES',
  },
  hero: {
    label: 'EARLY ACCESS PREVIEW',
    headline: 'Plan clearly,\npublish deliberately.',
    sub: 'Plan and organise social content from one clean workspace. Publishing integrations are still being validated before early access opens.',
    status: 'Early-access registration is not open yet.',
  },
  features: {
    label: 'WHY PROFILE TAILORS',
    items: [
      {
        tag: '01 — PLAN',
        title: 'Shape content before it ships.',
        desc: 'Draft and organise content in a focused workspace while publishing connections are validated.',
      },
      {
        tag: '02 — OVERVIEW',
        title: 'Review the full pipeline.',
        desc: 'Use a clear calendar and workflow to review what is planned and what still needs attention.',
      },
      {
        tag: '03 — EARLY ACCESS',
        title: 'A product still being validated.',
        desc: 'Features, integrations, markets, and commercial terms will be announced only after they are ready.',
      },
    ],
  },
  footer: {
    copy: 'Profile Tailors — early access preview.',
    tagline: 'A social content workspace in development.',
    legalLinks: [
      { label: 'Privacy Policy', href: '/privacy/' },
      { label: 'Terms of Service', href: '/terms/' },
      { label: 'Cookie Policy', href: '/cookies/' },
      { label: 'Acceptable Use', href: '/acceptable-use/' },
      { label: 'Accessibility', href: '/accessibility/' },
    ],
  },
  meta: {
    title: 'Profile Tailors — Social content planning in development',
    description:
      'Preview Profile Tailors, a social content planning workspace currently in development. Early-access registration is not open yet.',
  },
  legal: {
    publication: {
      unavailableTitle: 'Legal document not yet available',
      unavailableBody:
        'This document is undergoing factual and qualified legal review. It is not in effect and has not been approved for publication. Profile Tailors is not accepting reliance on or agreement to a draft policy.',
      unavailableAction: 'Return to the home page',
      unavailableHref: '/',
    },
    privacy: {
      title: 'Privacy Policy',
      description:
        'How Profile Tailors handles personal data when you use the operator-hosted instance.',
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
        'This policy describes how the operator of this Profile Tailors instance handles personal data when you use the hosted application available at this domain. The Profile Tailors software is distributed under the AGPL-3.0 licence; this policy applies to the instance operated here, not to self-hosted deployments.\n\nIf you operate your own instance, your privacy relationship with your users is governed by your own policies.\n\nThis policy reflects the current configuration of this instance. It will be updated if the services, providers, or data practices change.',
      dataController:
        'This instance is operated by an individual acting as the data controller under applicable data protection law, trading as Profile Tailors. The operator determines the purposes and means of processing for this instance.\n\nThe operator\u2019s identity and contact details are listed in Section 10.\n\n**Processor relationship:** When you use this instance to schedule and publish content to third-party platforms (such as LinkedIn), you determine the content and the publication instruction. The operator processes that content on your instructions as a processor for that specific purpose. For all other processing (account management, security, service operations), the operator acts as controller.',
      dataCollected:
        'When you use this instance, we collect:\n\n- **Account data:** email address, display name, avatar, and password (stored as a salted hash).\n- **Session data:** authentication tokens, refresh tokens, and session activity logs.\n- **Workspace and membership data:** workspace name, member list, roles, and permissions.\n- **Social account connections:** OAuth tokens and account identifiers for connected social platforms (e.g., LinkedIn).\n- **Content and media:** posts, drafts, scheduled content, uploaded media files, and publication metadata.\n- **API keys and credentials:** hashed API key verifiers, credential metadata, and access logs.\n- **Audit and security logs:** IP addresses, user-agent strings, request paths, timestamps, and access patterns.\n- **Support and waitlist data:** waitlist registration email, consent records, and any communications you send us.\n\nWe do not intentionally collect special categories of data (health, biometrics, political opinions, etc.). Do not upload such data to this instance.',
      dataUsage:
        'We process your data for these purposes:\n\n**Account creation, authentication, and security** \u2014 Contract necessity (performance of the service you signed up for).\n\n**Providing the content scheduling and publishing service** \u2014 Contract necessity.\n\n**Processing your publishing instructions to third-party platforms (e.g., LinkedIn)** \u2014 Your instruction (controller-to-processor direction).\n\n**Service security, abuse prevention, and incident response** \u2014 Legitimate interest (protecting the service and its users).\n\n**Communications about service changes, security, or support** \u2014 Contract necessity or legitimate interest.\n\n**Optional waitlist or marketing communications** \u2014 Your consent (separately obtained).\n\n**Service improvement and analytics (non-identifying)** \u2014 Legitimate interest (improving the service).\n\nWhere we rely on legitimate interest, you may object. Where we rely on consent, you may withdraw at any time.',
      dataSharing:
        'We share your data only with these categories of recipients:\n\n**Infrastructure providers:**\n- **Cloudflare, Inc.** \u2014 content delivery network, DNS, and edge security. Cloudflare acts as a processor. Data: IP address, request metadata, cached content. Region: global edge network.\n- **Oracle Cloud** \u2014 application hosting and PostgreSQL database. Acts as a processor. Data: all data stored or processed by the application. Region: Frankfurt, Germany.\n\n**Integrated platforms (when you use them):**\n- **LinkedIn** \u2014 when you connect a LinkedIn account, your content and credentials are shared with LinkedIn as an independent controller or processor depending on the operation. See LinkedIn\u2019s privacy policy for details.\n\nWe do not sell your personal data. We do not share data with advertising networks, data brokers, or analytics providers beyond what is described above.\n\nAll infrastructure providers are bound by data processing agreements that require them to protect your data and process it only on our instructions.',
      dataRetention:
        'We retain your data for these periods:\n\n- **Account data:** until you delete your account or the account is terminated.\n- **Session tokens:** up to 7 days (refresh token) or until logout.\n- **Content and media:** until you delete them or your account is terminated.\n- **Published content:** remains on the target platform according to that platform\u2019s retention.\n- **OAuth credentials:** until you disconnect the social account or delete your Profile Tailors account.\n- **Audit logs:** up to 90 days.\n- **Waitlist data:** until you withdraw consent or the waitlist closes.\n- **Backups:** retained for up to 30 days after deletion, then securely erased.\n\nWhen you delete your account, we initiate deletion of your active data within 30 days, except where we must retain data for legal obligations (e.g., tax records, if applicable).',
      internationalTransfers:
        'Your data is processed in these locations:\n\n- **Application and database:** Frankfurt, Germany.\n- **CDN and edge network:** Cloudflare global network (data centres worldwide).\n- **Integrated platforms:** LinkedIn servers (location depends on your region and LinkedIn\u2019s infrastructure).\n\nWhere data is transferred from your country of residence to another country, we ensure appropriate safeguards are in place:\n- **EU/EEA to third countries:** Standard Contractual Clauses (SCCs) with infrastructure providers.\n- **LinkedIn:** Standard Contractual Clauses (SCCs) or other recognised transfer mechanism as adopted by LinkedIn Ireland.\n- **Other regions:** applicable transfer mechanisms as required by local law.\n\nContact us (Section 10) for a copy of the relevant safeguards.',
      yourRights:
        'Depending on your jurisdiction, you may have the following rights regarding your personal data:\n\n- **Access:** request a copy of the data we hold about you.\n- **Rectification:** correct inaccurate or incomplete data.\n- **Erasure:** request deletion of your data, subject to legal retention obligations.\n- **Restriction:** restrict processing in certain circumstances.\n- **Objection:** object to processing based on legitimate interest.\n- **Data portability:** receive your data in a structured, machine-readable format.\n- **Withdraw consent:** where processing is based on consent.\n- **Lodge a complaint:** with your local data protection authority.\n\nTo exercise any of these rights, contact us using the details in Section 10. We will respond within the timeframe required by applicable law.\n\nIf you are in the EU/EEA, you also have the right to lodge a complaint with your local supervisory authority.',
      cookies:
        'This instance uses minimal browser storage:\n\n**Strictly necessary:**\n- `pt_refresh` \u2014 HttpOnly cookie for session refresh (7-day expiry, Secure, SameSite=Lax). Cleared on logout.\n- `sidebar_state` \u2014 JavaScript-accessible preference cookie for sidebar state (7-day expiry).\n- **Local storage:** theme preference (marketing site), locale and theme settings (dashboard), active workspace, dashboard state, and publication drafts.\n\n**Analytics:**\n- No advertising, tracking, or analytics cookies are used by default. Ahrefs Web Analytics may be loaded conditionally if configured by the operator; its default implementation is cookieless and does not use persistent identifiers.\n\n**Third-party:**\n- When you connect a LinkedIn account, LinkedIn may set its own cookies according to its cookie policy. We do not control LinkedIn\u2019s cookies.\n\nYou can manage cookies through your browser settings. Blocking strictly necessary cookies may affect service functionality.',
      policyChanges:
        'We may update this policy when our data practices change, new features are added, or legal requirements evolve. Material changes will be notified through the application or via email.\n\nThe current version is always available at `/privacy/`. Your continued use after a change constitutes acceptance of the updated policy.',
      contact:
        'For privacy enquiries, data subject requests, or complaints, contact the operator:\n\n**Yuniel Acosta** (trading as Profile Tailors)\nEmail: contact@profiletailors.com\n\nIf you are in the EU/EEA and are not satisfied with our response, you may lodge a complaint with your local data protection authority.',
    },
    terms: {
      title: 'Terms of Service',
      description:
        'Terms governing your use of the operator-hosted Profile Tailors instance.',
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
        '**Software licence.** The Profile Tailors source code is distributed under the GNU Affero General Public License v3.0 (AGPL-3.0). If you download, modify, or self-host the software, your rights and obligations are governed by that licence.\n\n**Hosted instance.** This document governs your use of the Profile Tailors instance operated at this domain (the \u201cService\u201d). The operator provides this instance as a convenience; it is not a commercial SaaS offering.\n\nBy creating an account or using the Service, you accept these terms. If you do not agree, do not use the Service.',
      accountTerms:
        '**Eligibility.** You must be at least 16 years old (or the age of digital consent in your country) to use the Service. By creating an account, you confirm that you meet this requirement.\n\n**Registration.** When you create an account, you must provide accurate and complete information. You are responsible for maintaining the confidentiality of your credentials and for all activity under your account.\n\n**Acceptance.** By clicking \u201cSign up\u201d or equivalent, you enter into these terms. The operator will record the version, timestamp, and locale of the terms you accepted.',
      acceptableUse:
        'You may use the Service only for lawful purposes and in accordance with our Acceptable Use Policy, which is incorporated into these terms by reference.\n\nYou must not:\n- Use the Service to violate any applicable law or regulation.\n- Attempt to disrupt, compromise, or gain unauthorised access to the Service, its infrastructure, or other users\u2019 accounts.\n- Use the Service to send spam, malware, or malicious content.\n- Use the Service to publish content that is illegal, harmful, threatening, abusive, harassing, defamatory, or infringes others\u2019 rights.\n\nViolation of these rules may result in immediate suspension or termination of your access.',
      feesPayment:
        'The Service is currently provided free of charge. The operator reserves the right to introduce fees in the future, with reasonable notice to users.\n\nIf paid plans are introduced, they will be governed by separate terms and you will have the option to stop using the Service rather than accept the new terms.',
      intellectualProperty:
        '**Software.** The Profile Tailors software is licensed under AGPL-3.0. Nothing in these terms restricts rights granted by that licence.\n\n**Your content.** You retain all rights to content you create, upload, or publish through the Service. You grant the operator a limited, non-exclusive licence to process, store, and transmit your content solely to provide the Service to you.\n\n**Service branding.** The \u201cProfile Tailors\u201d name, logo, and domain are the operator\u2019s property. These terms do not grant you any right to use them.',
      thirdPartyServices:
        'The Service integrates with third-party platforms (e.g., LinkedIn) at your direction. When you connect an account or publish content to a third-party platform:\n- Your use of that platform is governed by its own terms.\n- The operator is not responsible for that platform\u2019s actions, availability, or data practices.\n- You represent that you have the right to authorise the connection and publication.\n\nCurrently integrated platforms: LinkedIn. Other platforms may be added or removed at the operator\u2019s discretion.',
      limitationLiability:
        '**Disclaimer of warranties.** THE SERVICE IS PROVIDED \u201cAS IS\u201d, WITHOUT WARRANTIES OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NON-INFRINGEMENT. THIS DOES NOT AFFECT ANY STATUTORY RIGHTS THAT CANNOT BE EXCLUDED UNDER APPLICABLE LAW.\n\n**Limitation of liability.** TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, THE OPERATOR SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, OR PUNITIVE DAMAGES ARISING OUT OF OR RELATED TO YOUR USE OF THE SERVICE.\n\n**No exclusion of non-waivable rights.** Some jurisdictions do not allow the exclusion of certain warranties or limitation of liability. In those jurisdictions, the operator\u2019s liability is limited to the fullest extent permitted by law. Nothing in these terms excludes or limits liability for death, personal injury, fraud, or gross negligence where such exclusion would be unlawful.',
      termination:
        '**By you.** You may delete your account at any time. Your data will be deleted or anonymised in accordance with the Privacy Policy.\n\n**By the operator.** The operator may suspend or terminate your access if you materially breach these terms or the Acceptable Use Policy, or if required by law. Where practicable, the operator will give you notice and an opportunity to remedy the breach.\n\n**Effect of termination.** Upon termination, your right to use the Service ceases immediately. The operator will delete your data in accordance with the Privacy Policy, except where retention is required by law.\n\n**Survival.** Sections 5 (Your Content), 7 (Disclaimers), 9 (Governing Law), and 10 (Contact) survive termination.',
      governingLaw:
        'These terms are governed by the laws of Spain, without regard to its conflict of laws principles.\n\nIf you are a consumer in the EU/EEA, you may also bring proceedings in your country of residence. Nothing in these terms deprives you of the protection of mandatory consumer protection laws in your country.',
      contact:
        'For questions about these terms, contact:\n\n**Profile Tailors**\nEmail: contact@profiletailors.com',
    },
    cookies: {
      title: 'Cookie and Browser Storage Policy',
      description: 'How the Profile Tailors hosted instance uses cookies and browser storage.',
      lastUpdated: '22 July 2026',
      section1: '1. Scope and Definitions',
      section2: '2. Strictly Necessary Storage',
      section3: '3. Analytics and Similar Technologies',
      section4: '4. Third-Party Contexts',
      section5: '5. Choices and Withdrawal',
      section6: '6. Changes and Contact',
      whatAreCookies:
        'This policy describes the HTTP cookies and browser storage used by the Profile Tailors marketing site and dashboard. It applies to the operator-hosted instance only.\n\nThe Privacy Policy explains how we handle personal data collected through these technologies.',
      essentialCookies:
        '**pt_refresh** \u2014 HttpOnly refresh-session cookie. Expires after 7 days. Secure, SameSite=Lax. Path: /api/auth. Cleared on logout.\n\n**sidebar_state** \u2014 JavaScript-accessible preference cookie for the dashboard sidebar. Expires after 7 days.\n\n**Local storage** \u2014 The marketing site stores a theme preference. The dashboard stores locale, theme, active workspace, sidebar state, and publication drafts. These remain until cleared by the user or the application.',
      analyticsCookies:
        'Ahrefs Web Analytics may be loaded if the operator configures an analytics key. Its default implementation is cookieless and does not use persistent identifiers.\n\nNo advertising, behavioural tracking, or other analytics cookies are used.',
      thirdPartyCookies:
        'When you connect a LinkedIn account through the Service, LinkedIn may set its own cookies according to its cookie policy. The operator does not control LinkedIn\u2019s cookies or those of any other third-party platform you interact with through the Service.\n\nNo other third-party cookies are set by this instance.',
      manageCookies:
        'You can manage cookies through your browser settings. Blocking strictly necessary cookies may affect Service functionality.\n\nLocal storage can be cleared through your browser\u2019s developer tools or privacy settings. Deleting your account will remove locally stored data associated with it.',
      contact:
        'For questions about this policy, contact:\n\n**Profile Tailors**\nEmail: contact@profiletailors.com',
    },
    aup: {
      title: 'Acceptable Use Policy',
      description:
        'Rules for acceptable use of the operator-hosted Profile Tailors instance.',
      lastUpdated: '22 July 2026',
      section1: '1. Prohibited Conduct and Content',
      section2: '2. Enforcement',
      section3: '3. Reporting',
      section4: '4. Scope, Changes, and Contact',
      prohibitedActivities:
        'When using the Service (as defined in the Terms of Service), you must not:\n\n- Violate any applicable law or regulation.\n- Access, disrupt, or compromise the Service, its infrastructure, or other users\u2019 accounts without authorisation.\n- Distribute malware, spam, or malicious content.\n- Publish, upload, or share content that is illegal, harmful, threatening, abusive, harassing, defamatory, obscene, or infringes any third party\u2019s rights.\n- Engage in fraud, impersonation, or deceptive practices.\n- Exploit, harm, or endanger minors.\n- Distribute non-consensual intimate images or content.\n- Use the Service to violate the applicable terms of any integrated third-party platform (e.g., LinkedIn).\n\nThis list is not exhaustive. The operator reserves the right to interpret and update these rules as needed to protect the Service and its users.',
      enforcement:
        'The operator will assess reported violations based on available evidence and take proportionate action, which may include:\n- Warning the user.\n- Restricting access to specific features.\n- Suspending or terminating the user\u2019s account.\n- Reporting the activity to relevant authorities where required by law.\n\nEnforcement decisions will consider severity, recurrence, legal obligations, and the need to protect other users. Where practicable, the operator will notify the user of the action and the reasons for it, and provide an opportunity to respond.',
      reporting:
        'To report a violation of this policy, contact the operator at: contact@profiletailors.com\n\nPlease include:\n- The specific content or behaviour you are reporting.\n- The relevant user or account information (if known).\n- Any evidence supporting your report.\n\nThe operator will review reports and take appropriate action. Where required by law, reports may be forwarded to relevant authorities.',
      contact:
        'This policy is incorporated into the Terms of Service. By using the Service, you agree to comply with this policy.\n\nFor questions about this policy or to report a violation, contact:\n\n**Profile Tailors**\nEmail: contact@profiletailors.com',
    },
    accessibility: {
      title: 'Accessibility Statement',
      description: 'Our commitment to WCAG 2.2 AA accessibility and how to report issues.',
      lastUpdated: '31 July 2026',
      intro: 'Profile Tailors is committed to making its web presence accessible to all users, including people with disabilities. This statement describes our current conformance level, known limitations, and how to contact us if you encounter an accessibility barrier.',
      section1: '1. Conformance Status',
      conformance: 'We aim for **WCAG 2.2 Level AA** conformance across the marketing site (profiletailors.com) and the dashboard application. Automated axe-core checks run on every pull request as a first-pass gate. Manual review and screen-reader testing supplement automated checks.\n\nCurrent status: **Partially conforms** \u2014 We have completed an initial automated baseline audit. Manual review is ongoing. Known gaps are listed in Section 3.',
      section2: '2. Technical Specification',
      techSpec: 'This site relies on the following technologies for conformance:\n\n- HTML5\n- CSS (Tailwind CSS 4)\n- JavaScript (Astro 6, Vue 3)\n- WAI-ARIA 1.2\n\nThe site is tested with the following browser and assistive technology combinations:\n\n- Chrome + NVDA (Windows)\n- Firefox + NVDA (Windows)\n- Safari + VoiceOver (macOS, iOS)\n- Keyboard-only navigation (all supported browsers)',
      section3: '3. Known Limitations',
      knownLimitations: 'The following issues are known and are being addressed:\n\n- **Calendar keyboard navigation:** Arrow-key navigation between time slots in the scheduler week view is not yet implemented. Workaround: use Tab to move between interactive controls.\n- **Media picker drag-and-drop:** The drag-and-drop reordering interface does not have a keyboard-accessible alternative in the current release. Workaround: media items can be reordered using the action menu accessible via keyboard.\n- **Third-party embeds:** Some social platform preview embeds may not meet contrast requirements. These are rendered by external providers.\n\nWe are actively working to resolve the calendar and media picker issues in upcoming sprints.',
      section4: '4. Feedback and Contact',
      contact: 'If you experience an accessibility barrier not listed above, or if a listed workaround is not sufficient for your needs, please contact us:\n\n**Email:** accessibility@profiletailors.com\n\nWe aim to respond within **5 business days**. If you are not satisfied with our response, you may contact the relevant national accessibility enforcement body in your country.',
      section5: '5. EAA and Regulatory Applicability',
      eaa: 'The **European Accessibility Act (EAA / Directive 2019/882)** applies to certain digital products and services offered to consumers in the EU from June 2025. Profile Tailors is currently an early-access preview platform, not a publicly sold product. We are monitoring our regulatory obligations as the product approaches general availability and will update this statement accordingly.\n\nThe UK **Public Sector Bodies Accessibility Regulations** do not apply to this service, which is provided by a private entity.',
      section6: '6. Enforcement and Escalation',
      enforcement: 'If you are dissatisfied with our response to an accessibility concern, you may escalate to the relevant national enforcement body:\n\n- **EU:** Your national authority responsible for implementing the EAA.\n- **UK:** Equality and Human Rights Commission (EHRC).\n- **US:** Department of Justice (Section 508 / ADA Title III).',
    },
  },
  waitlist: {
    formAriaLabel: 'Early access waitlist form',
    emailLabel: 'EMAIL',
    emailInput: {
      placeholder: 'Email address',
      ariaLabel: 'Email address',
    },
    consentEarly: {
      label: 'I want early product access.',
      ariaLabel: 'Early access consent',
    },
    consentMarketing: {
      label: 'I agree to receive marketing emails.',
      ariaLabel: 'Marketing consent',
    },
    submit: 'REQUEST ACCESS',
    errorValidEmail: 'Please enter a valid email.',
    errorConsentRequired: 'Early-access consent is required.',
    errorTooManyRequests: 'Too many requests. Try again in a minute.',
    errorGeneric: 'Could not register you. Please try again.',
    success: "You're on the list.",
  },
  consent: {
    banner: {
      heading: 'We use cookies',
      description:
        'We use cookies to improve your experience. You can read our <a href="/privacy/">privacy policy</a> for more details.',
    },
    category: {
      necessary: {
        label: 'Necessary cookies',
        description: 'Required for authentication, security, and basic site functionality.',
      },
      analytics: {
        label: 'Analytics cookies',
        description: 'Help us understand how you use the site to improve your experience.',
      },
    },
    action: {
      acceptAll: 'Accept all',
      rejectAll: 'Reject all',
      customize: 'Customize',
      back: 'Back',
      savePreferences: 'Save preferences',
    },
    footer: {
      cookieSettings: 'Cookie settings',
    },
    privacy: {
      link: 'privacy policy',
    },
  },
} as const
