import { createRouter, createWebHistory } from 'vue-router'
import type { RouteLocationNormalized } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import { useAuthStore } from '@/stores/auth'

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
      component: () => import('../views/AuthView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/AuthView.vue'),
      meta: { guestOnly: true },
    },
    {
      path: '/',
      name: 'dashboard',
      component: HomeView,
      meta: { requiresAuth: true },
    },
    {
      path: '/scheduler',
      name: 'scheduler',
      component: () => import('../views/SchedulerView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/analytics',
      name: 'analytics',
      component: () => import('../views/AnalyticsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/settings',
      name: 'settings',
      component: () => import('../views/SettingsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/integrations/linkedin/callback',
      name: 'linkedin-callback',
      component: () => import('../views/LinkedInCallbackView.vue'),
      meta: { requiresAuth: true },
    },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (!auth.sessionChecked && auth.accessToken) {
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
