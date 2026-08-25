export default {
  title: 'Acepta tu invitación',
  description:
    'Haz clic abajo para aceptar la invitación y unirte a tu espacio de trabajo de la beta privada.',
  submit: 'Aceptar invitación',
  submitting: 'Aceptando…',
  accepted: 'Invitación aceptada.',
  workspaceReady: 'Cargando tu espacio de trabajo…',
  checkingAvailability: 'Comprobando disponibilidad de aceptación de invitaciones…',
  unavailableTitle: 'La aceptación de invitaciones no está disponible',
  unavailableMessage:
    'No podemos aceptar invitaciones en este momento. Inténtalo de nuevo más tarde.',
  errors: {
    notAcceptable: 'Este enlace de invitación ya no es válido o ya fue utilizado.',
    notFound: 'No encontramos esta invitación. Puede haber sido revocada o ya aceptada.',
    requiresLogin: 'Inicia sesión en tu cuenta para aceptar esta invitación.',
    rateLimited: 'Demasiados intentos. Espera un momento antes de volver a intentarlo.',
    missingToken: 'Al enlace de invitación le falta el token. Usa el enlace original del correo.',
    generic: 'No pudimos aceptar la invitación en este momento. Inténtalo de nuevo.',
  },
} as const
