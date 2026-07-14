import { createRouter, createWebHistory } from 'vue-router'
import type { RouteLocationNormalized } from 'vue-router'
import HomeView from '@modules/dashboard/presentation/views/HomeView.vue'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'

function requiresAuth(route: RouteLocationNormalized) {
  return route.meta.requiresAuth === true
}

function isGuestOnly(route: RouteLocationNormalized) {
  return route.meta.guestOnly === true
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@modules/auth/presentation/AuthView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@modules/auth/presentation/AuthView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/verify-email',
      name: 'verify-email',
      component: () => import('../views/VerifyEmailView.vue'),
    },
    {
      path: '/',
      name: 'dashboard',
      component: HomeView,
      meta: { requiresAuth: true },
    },
    {
      path: '/scheduler',
      redirect: (to) => ({
        name: 'scheduler-calendar-week',
        query: to.query,
      }),
      meta: { requiresAuth: true },
    },
    {
      path: '/scheduler/calendar/week',
      name: 'scheduler-calendar-week',
      component: () => import('@modules/publishing/views/SchedulerView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/scheduler/calendar/month',
      name: 'scheduler-calendar-month',
      component: () => import('@modules/publishing/views/SchedulerView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/scheduler/calendar/day',
      name: 'scheduler-calendar-day',
      component: () => import('@modules/publishing/views/SchedulerView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/scheduler/list',
      name: 'scheduler-list',
      component: () => import('@modules/publishing/views/SchedulerView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/analytics',
      name: 'analytics',
      component: () => import('@modules/dashboard/presentation/views/AnalyticsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/media',
      name: 'media',
      component: () => import('@modules/media/presentation/views/MediaLibraryView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('@modules/settings/presentation/SettingsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/integrations/linkedin/callback',
      name: 'linkedin-callback',
      component: () => import('@modules/auth/presentation/LinkedInCallbackView.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (!auth.sessionChecked) {
    await auth.hydrateSession()
  }

  if (requiresAuth(to) && !auth.isAuthenticated) {
    return {
      path: '/login',
      query: { redirect: to.fullPath },
    }
  }

  if (isGuestOnly(to) && auth.isAuthenticated) {
    return '/'
  }

  return true
})

export default router
