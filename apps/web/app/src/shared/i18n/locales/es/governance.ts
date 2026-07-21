export default {
  takedown: {
    report: {
      action: 'Reportar problema de derechos de autor',
      title: 'Reportar problema de derechos de autor',
      description:
        'Envía una solicitud de eliminación para este asset multimedia. Nuestro equipo revisará tu reporte y podrá suspender el contenido si viola nuestra política de derechos de autor.',
      reasonLabel: 'Motivo del reporte',
      reasonPlaceholder:
        'Describe por qué este contenido debería ser eliminado (p. ej., uso no autorizado de tu trabajo, violación de marca registrada, etc.)',
      emailLabel: 'Tu correo electrónico',
      emailHint: 'Este correo se usará para contactarte sobre tu reporte.',
      urlLabel: 'URL de referencia (opcional)',
      urlPlaceholder: 'https://example.com/work-original',
      submitAction: 'Enviar reporte',
      submitting: 'Enviando…',
      errors: {
        reasonRequired: 'Por favor proporciona un motivo para tu reporte.',
        submitFailed: 'Error al enviar el reporte. Inténtalo de nuevo.',
      },
    },
    review: {
      title: 'Reportes de eliminación',
      subtitle: 'Revisa y actúa sobre los reportes de derechos de autor enviados por usuarios.',
      statusFilter: 'Filtrar por estado',
      filterAll: 'Todos',
      refresh: 'Actualizar',
      loading: 'Cargando reportes…',
      empty: 'No se encontraron reportes.',
      emptyHint: 'Ningún reporte coincide con el filtro actual.',
      approveAction: 'Aprobar',
      rejectAction: 'Rechazar',
      rejectionReason: 'Motivo de rechazo',
      errors: {
        loadFailed: 'Error al cargar reportes.',
        approveFailed: 'Error al aprobar el reporte.',
        rejectFailed: 'Error al rechazar el reporte.',
      },
      rejectDialog: {
        title: 'Rechazar reporte',
        description:
          'Proporciona un motivo para rechazar este reporte de eliminación. Se notificará al reportador.',
        reasonLabel: 'Motivo de rechazo',
        reasonPlaceholder: 'Explica por qué se rechaza este reporte…',
      },
    },
  },
}
