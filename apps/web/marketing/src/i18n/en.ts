// src/i18n/en.ts
export const en = {
  nav: {
    name: 'Profile Tailors',
    langSwitch: 'ES',
  },
  hero: {
    label: 'NOW IN EARLY ACCESS',
    headline: 'Schedule smarter,\npost everywhere.',
    sub: 'Create and schedule posts across Instagram, Twitter/X, LinkedIn, and Facebook — from one clean interface.',
    input: 'your@email.com',
    cta: 'Join waitlist',
    waiting: '847 people already waiting',
    successSuffix: "— you're on the list. 🙌",
  },
  features: {
    label: 'WHY PROFILE TAILORS',
    items: [
      {
        tag: '01 — SCHEDULE',
        title: 'One post, all platforms.',
        desc: 'Write once, customize per network if needed, and schedule to all your accounts in seconds.',
      },
      {
        tag: '02 — OVERVIEW',
        title: 'See your full pipeline.',
        desc: 'A clean content calendar that shows exactly what goes out, when, and where. No surprises.',
      },
      {
        tag: '03 — SIMPLICITY',
        title: 'No bloat. No learning curve.',
        desc: 'Built for people who want to ship content — not manage software. Open it, post it, done.',
      },
    ],
  },
  footer: {
    copy: '© 2026 Profile Tailors. All rights reserved.',
    tagline: 'Built for creators who move fast.',
    legalLinks: [
      { label: 'Privacy Policy', href: '/privacy/' },
      { label: 'Terms of Service', href: '/terms/' },
      { label: 'Cookie Policy', href: '/cookies/' },
      { label: 'Acceptable Use', href: '/acceptable-use/' },
    ],
  },
  meta: {
    title: 'Profile Tailors — Schedule smarter, post everywhere',
    description:
      'Create and schedule posts across Instagram, Twitter/X, LinkedIn and Facebook from one place. Join the waitlist for early access.',
  },
  // ===================================================================
  // [LEGAL REVIEW] All legal content below is DRAFT and requires
  // qualified legal review before production publication. It has been
  // sourced from compliance documentation at docs/compliance/ but may
  // contain errors, omissions, or inaccuracies. DO NOT publish without
  // a lawyer's sign-off.
  // ===================================================================
  legal: {
    // ── Privacy Policy ──────────────────────────────────────────────
    privacy: {
      title: 'Privacy Policy',
      description:
        'Privacy Policy for Profile Tailors (Dallay). Learn how we collect, use, and protect your personal data when you use our social media scheduling and publishing platform.',
      lastUpdated: 'v1.0 — Effective July 17, 2026',
      section1: '1. Data Controller',
      section2: '2. Personal Data We Collect',
      section3: '3. How We Use Your Data',
      section4: '4. Data Sharing and Third-Party Recipients',
      section5: '5. Data Retention',
      section6: '6. International Data Transfers',
      section7: '7. Your Rights',
      section8: '8. Cookies',
      section9: '9. Changes to This Policy',
      section10: '10. Contact Us',
      intro:
        "This Privacy Policy explains how Dallay (Profile Tailors) ('we', 'us', or 'our') collects, uses, discloses, and protects your personal data when you visit our website or use our social media scheduling and publishing platform. We are committed to protecting your privacy and handling your personal data in accordance with applicable data protection laws, including the General Data Protection Regulation (GDPR) and the California Consumer Privacy Act (CCPA).\n\nThis policy applies to all visitors, registered users, and API consumers of our platform. Please read it carefully.",
      dataController:
        "Dallay (Profile Tailors) is the data controller for the processing activities described in this policy, except where we expressly act as a processor on behalf of our customers (see the 'Data Sharing' section for details).\n\n**Controller Identity:**\nEntity: Dallay (Profile Tailors)\nContact Email: privacy@profiletailors.com\nAddress: [Registered Business Address — TBD]\n\n**Data Protection Officer:**\nWe have not yet appointed a Data Protection Officer (DPO). Until a DPO is appointed, all data protection inquiries should be directed to privacy@profiletailors.com. We will update this policy once a DPO is designated.",
      dataCollected:
        'We process personal data for the following activities. Each activity specifies what data we collect, the legal basis, and our role.\n\n' +
        '**1. Account Registration and Management (pa-001)**\n' +
        'Role: Controller | Legal Basis: Contract (Art. 6(1)(b))\n' +
        'Data: Email address, username, password hash, principal identifier, display identity.\n' +
        'Purpose: To create and manage your account, authenticate you, and maintain your session.\n\n' +
        '**2. Social Media Publishing and Scheduling (pa-002)**\n' +
        'Role: Processor (on customer instructions) | Legal Basis: Controller instruction\n' +
        'Data: Social media account identifiers, published content text and media, publication metadata, delivery attempt records.\n' +
        'Purpose: To allow you to schedule, compose, and publish content to your connected social media accounts.\n\n' +
        '**3. Web Application Hosting and Delivery (pa-003)**\n' +
        'Role: Controller | Legal Basis: Contract (Art. 6(1)(b))\n' +
        'Data: IP address, browser user agent, usage analytics, request metadata (paths, referrers, timestamps).\n' +
        'Purpose: To host and deliver the Profile Tailors web application, API, and marketing site.\n\n' +
        '**4. Lead Capture and Waitlist Management (pa-004)**\n' +
        'Role: Controller | Legal Basis: Consent (Art. 6(1)(a))\n' +
        'Data: Email address, marketing preferences, consent status and version, locale and source information.\n' +
        'Purpose: To manage early-access waitlist signups and send marketing communications where you have consented.\n\n' +
        '**5. Workspace and Membership Management (pa-005)**\n' +
        'Role: Controller | Legal Basis: Contract (Art. 6(1)(b))\n' +
        'Data: Workspace membership records, ownership records, role assignments, workspace metadata (name, icon).\n' +
        'Purpose: To manage team workspaces, memberships, roles, and ownership.\n\n' +
        '**6. OAuth Authentication and Social Account Connections (pa-006)**\n' +
        'Role: Controller (authentication) / Processor (social connections) | Legal Basis: Contract (Art. 6(1)(b)) / Controller instruction\n' +
        'Data: OAuth provider subject identifier, provider connection references, encrypted OAuth tokens, connection status.\n' +
        'Purpose: To authenticate you and connect your social media accounts for cross-publishing.\n\n' +
        '**7. API Key and Service Credential Management (pa-007)**\n' +
        'Role: Controller | Legal Basis: Contract (Art. 6(1)(b))\n' +
        'Data: API key lookup hash, secret verifier, principal association, credential rotation history.\n' +
        'Purpose: To issue and manage API keys for programmatic access to our platform.\n\n' +
        '**8. Media Asset Storage and Management (pa-008)**\n' +
        'Role: Processor (on customer instructions) | Legal Basis: Controller instruction\n' +
        'Data: Original filenames, media content (images, videos, documents), upload metadata, external asset references.\n' +
        'Purpose: To upload, store, and serve media assets for your social media content.\n\n' +
        '**9. Content Publishing and Delivery Operations (pa-009)**\n' +
        'Role: Processor (on customer instructions) | Legal Basis: Controller instruction\n' +
        'Data: Publication content, author identifier, publication job metadata, delivery error logs.\n' +
        'Purpose: To execute scheduled content publishing and manage the delivery lifecycle.\n\n' +
        '**10. Audit and Governance Logging (pa-010)**\n' +
        'Role: Controller | Legal Basis: Legal obligation (Art. 5(2), Art. 24)\n' +
        'Data: Actor principal ID, workspace ID, request details, role information, event details.\n' +
        'Purpose: To record security-relevant events for compliance, incident response, and operational oversight.\n\n' +
        '**11. Analytics, Observability, and Error Monitoring (pa-011)**\n' +
        'Role: Controller | Legal Basis: Legitimate interest (Art. 6(1)(f))\n' +
        'Data: IP address, request metadata, error stack traces, feature usage events, performance metrics.\n' +
        'Purpose: To monitor application performance, track errors, and analyze product usage for service improvement.',
      dataUsage:
        'We use the personal data we collect for the following purposes:\n\n' +
        '• To provide, maintain, and improve our social media scheduling and publishing platform.\n' +
        '• To process and fulfill your requests, including publishing content to your connected social media accounts.\n' +
        '• To manage your account, workspace, and API credentials.\n' +
        '• To communicate with you about your account, service updates, and (with your consent) marketing information.\n' +
        '• To monitor and analyze usage trends and improve the user experience.\n' +
        '• To detect, prevent, and address technical issues, fraud, and security incidents.\n' +
        '• To comply with legal obligations and maintain audit records.\n\n' +
        'We only process personal data for purposes that are compatible with those described in this policy. If we need to use your data for a new purpose, we will seek your consent where required by applicable law.',
      dataSharing:
        'We share your personal data with third parties only as described in this policy. We distinguish between two categories of recipients:\n\n' +
        '**Processors** — Third parties that process personal data on our behalf and under our instructions. We have Data Processing Agreements (DPAs) in place with all our processors, incorporating Standard Contractual Clauses where required for international transfers.\n\n' +
        '**Independent Controllers** — Third parties that determine their own purposes and means of processing your personal data. Their use of your data is governed by their own privacy policies and terms.\n\n' +
        '**Processors (with DPA):**\n' +
        '• Vercel Inc. — Hosting, CDN, serverless functions (US — DPA in place, SCCs)\n' +
        '• Database hosting provider (Neon / AWS RDS / GCP Cloud SQL) — PostgreSQL database hosting (EEA/US — DPA required, SCCs)\n' +
        '• Cloudflare R2 / AWS S3 — Object storage for media assets (EEA/US — DPA required, SCCs)\n' +
        '• Sentry (planned) — Error tracking and monitoring (US — DPA planned, SCCs)\n' +
        '• Grafana / Prometheus (managed) — Metrics and monitoring (EEA — DPA required)\n\n' +
        '**Independent Controllers:**\n' +
        '• Auth0 / Clerk — Identity provider for OAuth2/OIDC authentication (US)\n' +
        '• LinkedIn — Social media platform (US)\n' +
        '• Twitter/X — Social media platform (US)\n' +
        '• Facebook — Social media platform (US)\n' +
        '• Instagram — Social media platform (US)\n' +
        '• TikTok — Social media platform (US)\n\n' +
        'When you publish content to social media platforms, those platforms act as independent controllers over the data they receive. We encourage you to review their privacy policies.',
      dataRetention:
        'We retain your personal data only as long as necessary to fulfill the purposes described in this policy, or as required by law. Our retention schedules are as follows:\n\n' +
        '• **Account data:** Retained for the duration of your account plus 30 days after deletion (soft delete), then permanently erased. Invoicing records retained for 6 years per statutory obligation. Aggregated analytics retained after de-identification.\n' +
        '• **Social media publishing data:** 90 days after account deletion (archive). Delivery attempt logs retained for 180 days. Published content persists on social media platforms per their policies.\n' +
        '• **Access logs:** 90 days (session end), then deleted.\n' +
        '• **Aggregated analytics:** 26 months, then deleted.\n' +
        '• **Waitlist data:** 30 days after consent withdrawal (anonymized), or indefinitely if converted to a full account.\n' +
        '• **Workspace data:** 30 days after removal or deletion (soft reference retained for audit).\n' +
        '• **OAuth tokens:** Deleted immediately upon connection revocation. Connection metadata retained for 30 days.\n' +
        '• **API key data:** 90 days after revocation (rotation audit trail), then deleted.\n' +
        '• **Media assets:** 7 days grace period after deletion, then permanently erased.\n' +
        '• **Delivery logs:** 90 days after deletion. Publication jobs: 7 days.\n' +
        '• **Audit logs:** 1 year (standard), 5 years (security incidents), indefinite for aggregated statistics.\n' +
        '• **Observability data:** 30 days (detailed logs), 13 months (aggregated metrics), 90 days (error events).\n\n' +
        'These retention periods may be extended if required by applicable law, legal holds, or ongoing investigations.',
      internationalTransfers:
        'Your personal data may be transferred to and processed in countries outside the European Economic Area (EEA), including the United States, where our service providers and social media platforms are located.\n\n' +
        'When we transfer personal data from the EEA to countries that have not received an adequacy decision from the European Commission, we rely on appropriate safeguards, including:\n\n' +
        '• **Standard Contractual Clauses (SCCs)** adopted by the European Commission, which contractually obligate the recipient to protect your personal data to EEA standards.\n' +
        '• Data Processing Agreements that incorporate SCCs with our processors.\n\n' +
        'Our primary service providers with transfers outside the EEA include:\n' +
        '• Vercel Inc. (US) — Hosting and CDN (SCCs via Vercel DPA)\n' +
        '• Social media platforms (US) — Independent controllers for published content\n' +
        '• Auth0 / Clerk (US) — Identity provider\n' +
        '• Object storage providers (US/EEA) — Media asset storage (SCCs)\n\n' +
        'If you are located in the EEA, UK, or Switzerland, your data is primarily processed within the EEA and transferred to the US only where necessary for service delivery, with appropriate safeguards in place.',
      yourRights:
        'Under applicable data protection law, you have the following rights regarding your personal data:\n\n' +
        '**1. Right of Access (Art. 15 GDPR)**\n' +
        'You have the right to request confirmation of whether we process your personal data and, if so, to access that data and obtain a copy.\n\n' +
        '**2. Right to Rectification (Art. 16 GDPR)**\n' +
        'You have the right to request correction of inaccurate or incomplete personal data.\n\n' +
        '**3. Right to Erasure — "Right to be Forgotten" (Art. 17 GDPR)**\n' +
        'You have the right to request deletion of your personal data, subject to certain exceptions (e.g., legal obligations, establishment of legal claims).\n\n' +
        '**4. Right to Restriction of Processing (Art. 18 GDPR)**\n' +
        'You have the right to request restriction of processing in certain circumstances, such as when you contest the accuracy of the data or object to processing.\n\n' +
        '**5. Right to Data Portability (Art. 20 GDPR)**\n' +
        'You have the right to receive your personal data in a structured, commonly used, machine-readable format and to transmit that data to another controller, where technically feasible.\n\n' +
        '**6. Right to Object (Art. 21 GDPR)**\n' +
        'You have the right to object to processing based on legitimate interests, including profiling. Where we process data for direct marketing purposes, you have an absolute right to opt out at any time.\n\n' +
        '**7. Rights in Relation to Automated Decision-Making (Art. 22 GDPR)**\n' +
        'You have the right not to be subject to decisions based solely on automated processing, including profiling, which produce legal effects concerning you. We do not currently engage in automated decision-making of this nature. If this changes, we will update this policy and implement appropriate safeguards.\n\n' +
        '**How to Exercise Your Rights:**\n' +
        'To exercise any of these rights, please contact us at privacy@profiletailors.com. We will respond to your request within one month (extendable by two months for complex or multiple requests). We may need to verify your identity before processing your request.\n\n' +
        '**CCPA Rights (California Residents):**\n' +
        'If you are a California resident, you also have the right to:\n' +
        '• Know what personal information we collect, use, and share.\n' +
        '• Request deletion of your personal information.\n' +
        '• Opt out of the sale of your personal information (we do not sell personal data).\n' +
        '• Non-discrimination for exercising your CCPA rights.\n' +
        'To exercise your CCPA rights, please contact us at privacy@profiletailors.com.\n\n' +
        '**Complaints:**\n' +
        'If you believe we have violated your data protection rights, you have the right to lodge a complaint with your local data protection supervisory authority. We encourage you to contact us first so we can attempt to resolve your concern.',
      cookies:
        'We use cookies and similar tracking technologies on our website. For detailed information about the cookies we use, their purposes, and how to manage them, please see our separate [Cookie Policy](/cookies/).\n\n' +
        'In summary, we use:\n' +
        '• Essential cookies for platform operation (Vercel platform, Auth0 authentication, Cloudflare security)\n' +
        '• Non-essential analytics cookies (Ahrefs Analytics — with consent mechanism planned for future release)\n\n' +
        'See the full Cookie Policy for complete details.',
      policyChanges:
        'We may update this Privacy Policy from time to time to reflect changes in our practices, legal requirements, or operational needs. When we make material changes:\n\n' +
        '• We will update the "Last updated" date at the top of this policy.\n' +
        '• We will notify you via email (if you have a registered account) or through a prominent notice on our website.\n' +
        '• Where required by law, we will seek your consent to the changes.\n\n' +
        'We encourage you to review this policy periodically. Your continued use of the platform after changes take effect constitutes your acceptance of the updated policy.',
      contact:
        'If you have any questions, concerns, or requests regarding this Privacy Policy or our data processing practices, please contact us:\n\n' +
        '• Email: privacy@profiletailors.com\n' +
        '• Subject line: "Data Privacy Request"\n\n' +
        'When submitting a data subject request, please include:\n' +
        '• Your full name and email address associated with your account.\n' +
        '• A clear description of the right you wish to exercise.\n' +
        '• Any specific information you are requesting or action you want us to take.\n\n' +
        'We will verify your identity before processing your request. We may ask for additional information to confirm your identity.',
    },

    // ── Terms of Service ────────────────────────────────────────────
    terms: {
      title: 'Terms of Service',
      description:
        'Terms of Service for Profile Tailors (Dallay). These terms govern your use of our social media scheduling and publishing platform.',
      lastUpdated: 'v1.0 — Effective July 17, 2026',
      section1: '1. Service Description and Acceptance of Terms',
      section2: '2. Eligibility and Account Registration',
      section3: '3. Acceptable Use',
      section4: '4. Fees and Payment',
      section5: '5. Intellectual Property',
      section6: '6. Third-Party Services',
      section7: '7. Disclaimers and Limitation of Liability',
      section8: '8. Suspension and Termination',
      section9: '9. Governing Law',
      section10: '10. Contact',
      serviceDescription:
        'Profile Tailors (Dallay) provides a social media scheduling and publishing platform that allows users to create, schedule, and publish content across multiple social media accounts (including Instagram, Twitter/X, LinkedIn, Facebook, and TikTok) from a single interface. Our platform includes content calendar management, team collaboration features, and analytics capabilities.\n\nThese Terms of Service ("Terms") govern your access to and use of our website, API, and all related services (collectively, the "Service"). By creating an account or using the Service, you agree to be bound by these Terms.',
      accountTerms:
        '**Eligibility**\n' +
        'You must be at least 18 years old to create an account and use the Service. By creating an account, you represent and warrant that you are at least 18 years of age and have the legal capacity to enter into these Terms.\n\n' +
        '**Account Registration**\n' +
        'When you create an account, you must provide accurate, current, and complete information. You are responsible for maintaining the confidentiality of your login credentials and for all activities that occur under your account. You must notify us immediately of any unauthorized use of your account.\n\n' +
        '**One Person Per Account**\n' +
        'Each account is for a single user. You may not share your account credentials with others or allow multiple users to access the Service through a single account, except through our designated workspace/multi-user features.\n\n' +
        '**Account Security**\n' +
        'You are responsible for:\n' +
        '• Maintaining the security of your account credentials.\n' +
        '• Ensuring that any information you provide remains accurate and up to date.\n' +
        '• All activity conducted through your account, whether authorized by you or not.\n' +
        'We implement industry-standard security measures, but we cannot guarantee against unauthorized access. You must use strong, unique passwords and enable two-factor authentication where available.',
      acceptableUse:
        'You agree to use the Service in compliance with all applicable laws and regulations. Your use of the Service is subject to our Acceptable Use Policy ("AUP"), which is incorporated by reference into these Terms. The AUP defines prohibited activities, including but not limited to:\n\n' +
        '• Posting illegal content or engaging in illegal activities.\n' +
        '• Sending spam or unsolicited bulk communications.\n' +
        '• Harassing, threatening, or bullying others.\n' +
        '• Posting hate speech or discriminatory content.\n' +
        '• Posting misleading, deceptive, or fraudulent content.\n' +
        '• Infringing on intellectual property rights.\n' +
        '• Distributing malware or engaging in hacking.\n' +
        '• Automated scraping of our platform without permission.\n\n' +
        'By using the Service, you agree to comply with the AUP. Violation of the AUP is a violation of these Terms and may result in suspension or termination of your account. [Read the full Acceptable Use Policy](/acceptable-use/).',
      feesPayment:
        '**Access During Early Access**\n' +
        'During the early access / waitlist period, the Service is provided free of charge. We reserve the right to introduce paid tiers and subscription fees in the future. If and when we introduce paid features, we will provide notice and an opportunity to review and accept the applicable pricing terms before being charged.\n\n' +
        '**Future Paid Services**\n' +
        'If you subscribe to a paid tier of the Service:\n' +
        '• Fees will be disclosed at the time of subscription and billed on the schedule disclosed (e.g., monthly or annually).\n' +
        '• All fees are non-refundable except as expressly stated in our refund policy.\n' +
        '• We may change our fees with 30 days notice. Continued use after the fee change constitutes acceptance of the new fees.',
      intellectualProperty:
        '**Our Rights**\n' +
        'The Service, including its code, design, branding, and proprietary technology, is owned by Profile Tailors and protected by intellectual property laws. You may not copy, modify, reverse engineer, or create derivative works of the Service without our express written permission.\n\n' +
        '**Your Content**\n' +
        'You retain all ownership rights to the content you submit, post, or display through the Service ("Your Content"). By submitting content through the Service, you grant Profile Tailors a non-exclusive, worldwide, royalty-free license to access, store, transmit, and display Your Content solely for the purpose of providing and improving the Service.\n\n' +
        'This license:\n' +
        '• Is limited to the purpose of operating and improving the Service.\n' +
        '• Does not grant us the right to sell or sublicense Your Content.\n' +
        '• Does not grant us ownership of Your Content.\n' +
        '• Continues only as long as necessary to provide the Service (the license expires when you delete your content or account, subject to technical limitations like cached copies).\n\n' +
        '**Feedback**\n' +
        'If you provide us with feedback, suggestions, or ideas about the Service, you grant us the right to use that feedback without restriction or compensation.',
      thirdPartyServices:
        'The Service integrates with third-party platforms and services, including social media platforms (Instagram, Twitter/X, LinkedIn, Facebook, TikTok), identity providers (Auth0/Clerk), and infrastructure providers (Vercel, Cloudflare).\n\n' +
        'We are not responsible for the practices, privacy policies, or content of these third-party services. Your use of integrated services is subject to their respective terms and policies. We encourage you to review the terms and privacy policies of any third-party service you connect to our platform.\n\n' +
        'We do not warrant that the Service will be compatible with all third-party platforms or that integrations will continue uninterrupted if a third party changes its API or terms of access.',
      limitationLiability:
        '**IMPORTANT — PLEASE READ THIS SECTION CAREFULLY. IT LIMITS OUR LIABILITY TO YOU.**\n\n' +
        '**Limitation of Liability**\n' +
        'To the maximum extent permitted by applicable law, Profile Tailors and its officers, directors, employees, and agents shall not be liable for any indirect, incidental, special, consequential, or punitive damages, including but not limited to:\n' +
        '• Loss of profits, data, use, or goodwill.\n' +
        '• Service interruption or computer damage.\n' +
        '• Cost of procurement of substitute services.\n' +
        '• Any damages arising from your use of or inability to use the Service.\n\n' +
        'Our total liability to you for any claim arising out of or relating to these Terms or the Service shall not exceed the greater of:\n' +
        '(a) the amount you have paid us in the twelve (12) months preceding the claim, or\n' +
        '(b) one hundred US dollars ($100).\n\n' +
        '**Disclaimer of Warranties**\n' +
        'THE SERVICE IS PROVIDED "AS IS" AND "AS AVAILABLE" WITHOUT WARRANTIES OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO:\n' +
        '• Implied warranties of merchantability, fitness for a particular purpose, and non-infringement.\n' +
        '• Warranties that the Service will be uninterrupted, error-free, secure, or free from viruses or other harmful components.\n' +
        '• Warranties regarding the accuracy, reliability, or completeness of any content or information provided through the Service.\n\n' +
        '**Exceptions**\n' +
        'Some jurisdictions do not allow the exclusion of certain warranties or limitation of liability for certain types of damages. If you reside in such a jurisdiction, some of the above limitations may not apply to you. In such cases, our liability will be limited to the fullest extent permitted by applicable law.',
      termination:
        '**Termination by You**\n' +
        'You may terminate your account at any time by deleting your account through the platform settings or by contacting us. Upon termination, your content and data will be handled in accordance with our Privacy Policy and applicable retention schedules.\n\n' +
        '**Termination by Us**\n' +
        'We reserve the right to suspend or terminate your access to the Service at any time, with or without notice, for any of the following reasons:\n' +
        '• Violation of these Terms, including the Acceptable Use Policy.\n' +
        '• Violation of applicable laws or regulations.\n' +
        '• Extended period of inactivity (as determined by us in our discretion).\n' +
        '• Upon your request to delete your account.\n' +
        '• If we are required to do so by law.\n' +
        '• If continued provision of the Service to you could result in legal or regulatory liability.\n\n' +
        '**Effect of Termination**\n' +
        'Upon termination:\n' +
        '• Your right to access and use the Service ceases immediately.\n' +
        '• We will delete or anonymize your personal data in accordance with our Privacy Policy and retention schedules.\n' +
        '• Any provisions of these Terms that by their nature should survive termination will survive, including but not limited to intellectual property provisions, warranty disclaimers, limitations of liability, and governing law.',
      governingLaw:
        '[GOVERNING LAW TBD — PLACEHOLDER FOR LEGAL REVIEW]\n\n' +
        'These Terms and any disputes arising out of or relating to them shall be governed by and construed in accordance with the laws of [Jurisdiction to be determined by legal counsel].\n\n' +
        'Any legal action or proceeding arising out of or relating to these Terms shall be brought exclusively in the courts of [Jurisdiction to be determined]. Each party submits to the personal jurisdiction of such courts.\n\n' +
        'The United Nations Convention on Contracts for the International Sale of Goods (CISG) does not apply to these Terms.\n\n' +
        '[NOTE: Governing law and jurisdiction MUST be determined by qualified legal counsel before production publication.]',
      contact:
        'If you have any questions about these Terms, please contact us:\n\n' +
        '• Email: legal@profiletailors.com\n' +
        '• Subject line: "Terms of Service Inquiry"\n\n' +
        'For legal notices, please use the following address:\n' +
        'Profile Tailors (Dallay)\n' +
        '[Registered Business Address — TBD]\n' +
        '[City, Country, Postal Code]\n\n' +
        '**Entire Agreement**\n' +
        'These Terms, together with our Privacy Policy and Acceptable Use Policy, constitute the entire agreement between you and Profile Tailors regarding your use of the Service and supersede any prior agreements or understandings.',
    },

    // ── Cookie Policy ───────────────────────────────────────────────
    cookies: {
      title: 'Cookie Policy',
      description:
        'Cookie Policy for Profile Tailors (Dallay). Learn about the cookies we use and how to manage your cookie preferences.',
      lastUpdated: 'v1.0 — Effective July 17, 2026',
      section1: '1. What Are Cookies',
      section2: '2. Essential Cookies',
      section3: '3. Analytics and Performance Cookies',
      section4: '4. Third-Party Cookies',
      section5: '5. How to Manage Cookies',
      section6: '6. Contact',
      whatAreCookies:
        "Cookies are small text files that are stored on your device (computer, tablet, or mobile) when you visit a website. They are widely used to make websites work more efficiently, enhance user experience, and provide information to website owners.\n\nWe use cookies and similar tracking technologies (such as local storage and web beacons) on our website and platform. This policy explains what cookies we use, why we use them, and how you can control them.\n\n**Types of cookies we use:**\n• Essential (strictly necessary) cookies — required for the platform to function.\n• Analytics cookies — help us understand how visitors use our site (non-essential, consent-dependent).",
      essentialCookies:
        'Essential cookies are necessary for the platform to function properly and cannot be disabled in our systems. They are set in response to actions you take, such as logging in, navigating between pages, or filling in forms.\n\n' +
        '**Vercel Platform Cookies**\n' +
        'Provider: Vercel Inc. (US)\n' +
        'Purpose: CDN routing, session management, and deployment preview.\n' +
        'Cookies: `_vercel_live`, `_vc_cookie`, `vfpx`\n' +
        'Classification: Essential\n' +
        'Duration: Session to persistent (varies by cookie)\n\n' +
        '**Auth0 Session Cookies**\n' +
        'Provider: Auth0 (US)\n' +
        'Purpose: Authentication and session management. These cookies are set when you log in to your account and are necessary for maintaining your authenticated session.\n' +
        'Cookies: `auth0.*` (multiple cookies)\n' +
        'Classification: Essential\n' +
        'Duration: Session (deleted when you close your browser) to persistent (varies by configuration)\n\n' +
        '**Cloudflare Security Cookies**\n' +
        'Provider: Cloudflare Inc. (US)\n' +
        'Purpose: Security and bot detection. These cookies are used to distinguish legitimate users from automated traffic and to protect the platform from DDoS attacks and other security threats.\n' +
        'Cookies: `__cf_bm`, `cf_clearance`\n' +
        'Classification: Essential\n' +
        'Duration: 30 minutes to 12 months (varies by cookie type)\n\n' +
        '**Ahrefs Analytics Cookies (Essential subset)**\n' +
        'Provider: Ahrefs Pte. Ltd.\n' +
        'Purpose: Basic operational analytics for website performance monitoring.\n' +
        'Cookies: `ahrefs*` (varies)\n' +
        'Classification: Essential / Non-essential (see analytics section below)',
      analyticsCookies:
        '**Ahrefs Analytics (Non-Essential)**\n' +
        'We use Ahrefs Analytics to understand how visitors interact with our website, which pages are most popular, and how users find us. This helps us improve the user experience and optimize our content.\n\n' +
        'Provider: Ahrefs Pte. Ltd.\n' +
        'Purpose: Website traffic analysis, user behavior tracking, and referral source identification.\n' +
        'Cookies: `ahrefs*` (analytics-specific cookies)\n' +
        'Classification: Non-essential\n' +
        'Duration: Up to 24 months (varies by cookie)\n\n' +
        '**Consent Status**\n' +
        'We do NOT currently have a cookie consent banner implemented on our website. Non-essential analytics cookies may be set when you visit our site. We are working on implementing a cookie consent mechanism in a future release. In the meantime:\n' +
        '• You can control cookies through your browser settings (see "How to Control Cookies" below).\n' +
        '• We recommend that you review your browser cookie settings and disable third-party or analytics cookies if you prefer not to be tracked.',
      thirdPartyCookies:
        'In addition to the cookies set directly by our platform, third-party services we integrate with may set their own cookies when you interact with their features. These include:\n\n' +
        '• **Social Media Platforms** — When you connect your social media accounts or publish content, the social platform (LinkedIn, Twitter/X, Facebook, Instagram, TikTok) may set cookies governed by their own cookie policies.\n' +
        '• **Identity Providers** — Auth0/Clerk sets cookies for authentication purposes when you log in.\n\n' +
        'We do not control these third-party cookies. We recommend reviewing the cookie policies of each third-party service you interact with through our platform.',
      manageCookies:
        'You can control and manage cookies in several ways:\n\n' +
        '**Browser Settings**\n' +
        'Most web browsers allow you to control cookies through their settings. You can:\n' +
        '• View and delete cookies stored on your device.\n' +
        '• Block all or third-party cookies.\n' +
        '• Set preferences for specific websites.\n' +
        '• Enable private/incognito browsing modes that limit cookie persistence.\n\n' +
        'Please note that disabling essential cookies may affect the functionality of the platform. For example, you may not be able to log in or use certain features.\n\n' +
        '**Browser-Specific Instructions:**\n' +
        '• [Google Chrome](https://support.google.com/chrome/answer/95647)\n' +
        '• [Mozilla Firefox](https://support.mozilla.org/en-US/kb/cookies-information-websites-store-on-your-computer)\n' +
        '• [Apple Safari](https://support.apple.com/guide/safari/manage-cookies-sfri11471/)\n' +
        '• [Microsoft Edge](https://support.microsoft.com/en-us/microsoft-edge/delete-cookies-in-microsoft-edge-63947406-40ac-c3b8-57b9-2a946a29ae09)\n\n' +
        '**Future Consent Mechanism**\n' +
        'We are actively working on implementing a cookie consent banner that will allow you to manage your cookie preferences directly on our website. This feature is planned for a future release. Until then, please use your browser settings to manage cookies.',
      contact:
        'If you have any questions about our use of cookies or this Cookie Policy, please contact us:\n\n' +
        '• Email: privacy@profiletailors.com\n' +
        '• Subject line: "Cookie Policy Inquiry"\n\n' +
        'We will update this policy as we implement new features or change our use of cookies.',
    },

    // ── Acceptable Use Policy ───────────────────────────────────────
    aup: {
      title: 'Acceptable Use Policy',
      description:
        'Acceptable Use Policy for Profile Tailors (Dallay). This policy defines what is and is not permitted when using our platform and API.',
      lastUpdated: 'v1.0 — Effective July 17, 2026',
      section1: '1. Prohibited Uses',
      section2: '2. Enforcement',
      section3: '3. Reporting Violations',
      section4: '4. Contact',
      prohibitedActivities:
        'You may not use the Service to engage in any of the following prohibited activities. These prohibitions apply to all content you publish, schedule, or transmit through the platform, as well as your use of the API and website.\n\n' +
        '**1. Illegal Content and Activities**\n' +
        'You may not use the Service to publish, share, or promote any content that violates applicable laws or regulations, including but not limited to content that facilitates illegal activity, promotes unlawful acts, or violates export control laws.\n\n' +
        '**2. Spam and Unsolicited Communications**\n' +
        'You may not use the Service to send spam, unsolicited bulk communications, or repetitive messages. This includes mass posting of identical or substantially similar content across platforms, posting misleading links, or engaging in any practice that could be classified as spam by social media platforms or applicable law.\n\n' +
        '**3. Harassment, Threats, and Bullying**\n' +
        'You may not use the Service to harass, threaten, intimidate, or bully any individual or group. This includes posting content that incites violence, promotes self-harm, or engages in sustained targeted abuse.\n\n' +
        '**4. Hate Speech and Discriminatory Content**\n' +
        'You may not use the Service to publish hate speech or content that promotes discrimination, hostility, or violence against individuals or groups based on race, ethnicity, religion, gender, gender identity, sexual orientation, disability, age, nationality, or any other protected characteristic.\n\n' +
        '**5. Misleading, Deceptive, or Fraudulent Content**\n' +
        'You may not use the Service to publish false, misleading, or deceptive content. This includes impersonation, phishing, financial scams, disinformation, manipulated media (deepfakes) presented as authentic, and any content intended to deceive others.\n\n' +
        '**6. Copyright and Intellectual Property Infringement**\n' +
        'You may not use the Service to publish content that infringes on the copyright, trademark, patent, trade secret, or other intellectual property rights of others. You represent and warrant that you own or have obtained all necessary rights and licenses for any content you publish through the Service.\n\n' +
        '**7. Malware, Hacking, and Security Violations**\n' +
        'You may not use the Service to distribute malware, viruses, ransomware, or other harmful code. You may not attempt to gain unauthorized access to our systems, probe for vulnerabilities, or engage in any activity that could compromise the security or integrity of the platform.\n\n' +
        '**8. API Abuse and Automated Scraping**\n' +
        'You may not use automated methods (including bots, scrapers, crawlers, or scripts) to extract data from our platform without our express written permission. This includes scraping user profiles, content, analytics data, or any other information accessible through the Service.',
      enforcement:
        '**Monitoring and Enforcement**\n' +
        'We reserve the right, but do not assume the obligation, to monitor the Service for violations of this AUP. We may investigate any reported or suspected violation and take appropriate action in our sole discretion.\n\n' +
        '**Consequences of Violation**\n' +
        'Violation of this Acceptable Use Policy may result in any or all of the following actions, at our sole discretion:\n' +
        '• Removal of prohibited content or restriction of access to specific features.\n' +
        '• Suspension of your account (temporary or permanent).\n' +
        '• Termination of your account without notice.\n' +
        '• Reporting to relevant law enforcement authorities where illegal activity is suspected.\n' +
        '• Legal action to recover damages or seek injunctive relief.\n\n' +
        '**No License for Automated Scraping**\n' +
        'Nothing in this AUP or these Terms grants you any license to scrape, crawl, or otherwise extract data from our platform through automated means. Any such activity is strictly prohibited unless expressly authorized in writing.\n\n' +
        '**API Fair Use**\n' +
        'If you access our API, you agree to use it responsibly and in accordance with fair use principles. While we do not currently publish specific numerical rate limits, we reserve the right to:\n' +
        '• Set and adjust rate limits at any time to ensure platform stability.\n' +
        '• Throttle or block API access that we determine, in our reasonable judgment, to be excessive or abusive.\n' +
        '• Require API keys for access and revoke keys that violate this policy.\n\n' +
        'Fair use of the API means:\n' +
        '• Making only the number of requests necessary for your legitimate use case.\n' +
        '• Not using the API in a way that degrades service for other users.\n' +
        '• Caching responses where appropriate to minimize redundant requests.',
      reporting:
        '**How to Report Violations**\n' +
        'If you become aware of any content or activity that violates this Acceptable Use Policy, please report it to us:\n\n' +
        '• Email: abuse@profiletailors.com\n' +
        '• Subject line: "AUP Violation Report"\n\n' +
        'Please include in your report:\n' +
        '• The specific content or behavior you believe violates this policy.\n' +
        '• The URL or account name associated with the violation.\n' +
        '• The date and time of the violation.\n' +
        '• Any additional information that would help us investigate.\n\n' +
        'We will review all reports and take appropriate action. We may not be able to disclose the outcome of our investigation to the reporter.\n\n' +
        '**Good Faith Reports**\n' +
        'We encourage good faith reporting of suspected violations. Knowingly submitting false or frivolous reports may itself be considered a violation of this policy.',
      contact:
        'If you have questions about this Acceptable Use Policy or need clarification on whether specific conduct is permitted, please contact us:\n\n' +
        '• Email: legal@profiletailors.com\n' +
        '• Subject line: "AUP Question"\n\n' +
        '**Disclaimer**\n' +
        'This Acceptable Use Policy is for informational purposes and does not create any legal obligation for us to monitor, enforce, or take action regarding content or activity on the Service. We reserve the right to update this policy at any time.',
    },
  },
} as const
