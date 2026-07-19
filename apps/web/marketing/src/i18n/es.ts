// src/i18n/es.ts
export const es = {
  nav: {
    name: 'Profile Tailors',
    langSwitch: 'EN',
  },
  hero: {
    label: 'VISTA PREVIA DE ACCESO ANTICIPADO',
    headline: 'Planifica con claridad,\npublica con intención.',
    sub: 'Planifica y organiza contenido social desde un espacio limpio. Las integraciones de publicación siguen en validación antes de abrir el acceso anticipado.',
    status: 'La inscripción al acceso anticipado todavía no está abierta.',
  },
  features: {
    label: 'POR QUÉ PROFILE TAILORS',
    items: [
      {
        tag: '01 — PLANIFICA',
        title: 'Da forma al contenido antes de publicarlo.',
        desc: 'Redacta y organiza contenido en un espacio enfocado mientras se validan las conexiones de publicación.',
      },
      {
        tag: '02 — VISIÓN GENERAL',
        title: 'Revisa todo el flujo.',
        desc: 'Usa un calendario y un flujo claros para revisar qué está planificado y qué necesita atención.',
      },
      {
        tag: '03 — ACCESO ANTICIPADO',
        title: 'Un producto todavía en validación.',
        desc: 'Las funciones, integraciones, mercados y condiciones comerciales se anunciarán solo cuando estén preparadas.',
      },
    ],
  },
  footer: {
    copy: 'Profile Tailors — vista previa de acceso anticipado.',
    tagline: 'Un espacio de contenido social en desarrollo.',
    legalLinks: [
      { label: 'Política de Privacidad', href: '/es/privacy/' },
      { label: 'Términos del Servicio', href: '/es/terms/' },
      { label: 'Política de Cookies', href: '/es/cookies/' },
      { label: 'Uso Aceptable', href: '/es/acceptable-use/' },
    ],
  },
  meta: {
    title: 'Profile Tailors — Planificación de contenido social en desarrollo',
    description:
      'Descubre Profile Tailors, un espacio de planificación de contenido social en desarrollo. La inscripción al acceso anticipado todavía no está abierta.',
  },
  legal: {
    publication: {
      unavailableTitle: 'Documento legal todavía no disponible',
      unavailableBody:
        'Este documento está sometido a verificación factual y revisión jurídica cualificada. No está vigente ni ha sido aprobado para su publicación. Profile Tailors no solicita que se acepte ni se confíe en una política borrador.',
      unavailableAction: 'Volver a la página de inicio',
      unavailableHref: '/es/',
    },
  },
  waitlist: {
    formAriaLabel: 'Formulario de lista de espera de acceso anticipado',
    emailLabel: 'CORREO',
    emailInput: {
      placeholder: 'Correo electrónico',
      ariaLabel: 'Correo electrónico',
    },
    consentEarly: {
      label: 'Quiero acceso anticipado al producto.',
      ariaLabel: 'Acceso anticipado',
    },
    consentMarketing: {
      label: 'Acepto recibir correos de marketing.',
      ariaLabel: 'Consentimiento de marketing',
    },
    submit: 'SOLICITAR ACCESO',
    errorValidEmail: 'Introduce un correo válido.',
    errorConsentRequired: 'Debes aceptar el acceso anticipado.',
    errorTooManyRequests: 'Demasiadas solicitudes. Inténtalo en un minuto.',
    errorGeneric: 'No se pudo registrar. Inténtalo de nuevo.',
    success: '¡Estás en la lista!',
  },
} as const
