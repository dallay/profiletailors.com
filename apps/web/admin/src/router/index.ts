import { createRouter, createWebHistory } from 'vue-router'
import { useAdminAuthStore } from '@/stores/auth.store'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
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
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAdminAuthStore()

  if (to.meta.public) return true

  if (!authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (!authStore.hasPlatformAccess) {
    return { name: 'access-denied' }
  }

  if (to.meta.permission && !authStore.hasPermission(to.meta.permission as string)) {
    return { name: 'access-denied' }
  }

  return true
})

export default router
