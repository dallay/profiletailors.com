import { createI18n } from 'vue-i18n'

const messages = {
  en: {
    nav: {
      dashboard: 'Dashboard',
      scheduler: 'Scheduler',
      analytics: 'Analytics',
      settings: 'Settings',
      logout: 'Log Out',
    },
    dashboard: {
      welcome: 'Welcome back',
      subtitle: 'Here is what is happening with your channels today.',
      newPost: 'New Post',
      scheduled: 'Scheduled Posts',
      platforms: 'Connected Platforms',
      audience: 'Audience Reach',
      engagement: 'Avg Engagement',
      noPosts: 'No scheduled posts for today.',
      recentActivity: 'Recent Activity',
      viewAll: 'View All',
      toggleTheme: 'Toggle Theme',
    },
    composer: {
      title: 'Compose Post',
      placeholder: 'What would you like to share?',
      scheduleBtn: 'Schedule Post',
      cancelBtn: 'Cancel',
      successMsg: 'Post scheduled successfully!',
    },
  },
  es: {
    nav: {
      dashboard: 'Panel de Control',
      scheduler: 'Programador',
      analytics: 'Analítica',
      settings: 'Ajustes',
      logout: 'Cerrar Sesión',
    },
    dashboard: {
      welcome: 'Bienvenido de nuevo',
      subtitle: 'Esto es lo que está pasando en tus canales hoy.',
      newPost: 'Nueva Publicación',
      scheduled: 'Publicaciones Programadas',
      platforms: 'Plataformas Conectadas',
      audience: 'Alcance de Audiencia',
      engagement: 'Interacción Promedio',
      noPosts: 'No hay publicaciones programadas para hoy.',
      recentActivity: 'Actividad Reciente',
      viewAll: 'Ver Todo',
      toggleTheme: 'Cambiar Tema',
    },
    composer: {
      title: 'Crear Publicación',
      placeholder: '¿Qué te gustaría compartir?',
      scheduleBtn: 'Programar Publicación',
      cancelBtn: 'Cancelar',
      successMsg: '¡Publicación programada con éxito!',
    },
  },
}

const i18n = createI18n({
  legacy: false, // Use Composition API
  locale: 'en', // default locale
  fallbackLocale: 'en',
  messages,
})

export default i18n
