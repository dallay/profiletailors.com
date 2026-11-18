import { createRouter, createWebHistory } from 'vue-router'
import { useAdminAuthStore } from '@/stores/auth.store'
import type { PlatformPermission } from '@/stores/auth.store'
import { plannedNavEntries } from './nav-registry'

declare module 'vue-router' {
  interface RouteMeta {
    public?: boolean
    requiresAuth?: boolean
    permission?: PlatformPermission
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/bootstrap',
      name: 'bootstrap',
      component: () => import('@/views/BootstrapView.vue'),
      meta: { public: true },
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/access-denied',
      name: 'access-denied',
      component: () => import('@/views/AccessDeniedView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AdminLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { permission: 'platform.dashboard.read' },
        },
        {
          path: 'waitlist',
          name: 'waitlist',
          component: () => import('@/views/WaitlistView.vue'),
          meta: { permission: 'platform.waitlist.read' },
        },
        {
          path: 'waitlist/:entryId',
          name: 'waitlist-entry',
          component: () => import('@/views/WaitlistEntryView.vue'),
          meta: { permission: 'platform.waitlist.read' },
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { permission: 'platform.users.read' },
        },
        {
          path: 'users/:principalId',
          name: 'user-detail',
          component: () => import('@/views/UserDetailView.vue'),
          meta: { permission: 'platform.users.read' },
        },
        {
          path: 'audit',
          name: 'audit',
          component: () => import('@/views/AuditView.vue'),
          meta: { permission: 'platform.audit.read' },
        },
        {
          path: 'direct-invitations',
          name: 'direct-invitations',
          component: () => import('@/views/DirectInvitationsView.vue'),
          meta: { permission: 'platform.invitations.read' },
        },
        {
          path: 'configuration',
          name: 'configuration',
          component: () => import('@/views/ConfigurationView.vue'),
          meta: { permission: 'platform.configuration.read' },
        },
        {
          path: 'notifications',
          name: 'notifications',
          component: () => import('@/views/NotificationsView.vue'),
          meta: { permission: 'platform.notifications.read' },
        },
        {
          path: 'governance',
          name: 'governance',
          component: () => import('@/views/GovernanceView.vue'),
          meta: { permission: 'platform.governance.read' },
        },
        {
          path: 'governance/:reportId',
          name: 'governance-detail',
          component: () => import('@/views/GovernanceView.vue'),
          meta: { permission: 'platform.governance.read' },
        },
        ...plannedNavEntries().map((entry) => ({
          path: entry.path,
          name: entry.routeName,
          component: () => import('@/views/PlannedAreaView.vue'),
          meta: { permission: entry.permission },
        })),
      ],
    },
  ],
})

let sessionHydration: Promise<void> | null = null

router.beforeEach(async (to) => {
  const authStore = useAdminAuthStore()

  if (to.meta.public) return true

  if (authStore.accessDenied) return { name: 'access-denied' }

  sessionHydration ??= authStore.hydrateSession()
  await sessionHydration

  if (!authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: safeReturnPath(to.fullPath) } }
  }

  if (!authStore.hasPlatformAccess) {
    return { name: 'access-denied' }
  }

  if (to.meta.permission && !authStore.hasPermission(to.meta.permission)) {
    return { name: 'access-denied' }
  }

  return true
})

export default router

function safeReturnPath(value: string): string {
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\')) return '/'
  const destination = new URL(value, window.location.origin)
  return destination.origin === window.location.origin
    ? `${destination.pathname}${destination.search}${destination.hash}`
    : '/'
}
