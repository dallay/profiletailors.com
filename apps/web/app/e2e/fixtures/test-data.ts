/**
 * Test data constants for E2E login tests.
 *
 * All test user accounts MUST be pre-seeded in the SMP backend or created
 * during test setup and cleaned up during teardown.
 */

export const APP_URL = {
  login: '/login',
  register: '/register',
  dashboard: '/',
  scheduler: '/scheduler',
  analytics: '/analytics',
  settings: '/settings',
  linkedinCallback: '/integrations/linkedin/callback',
} as const

export const E2E_TEST_USER = {
  email: process.env.E2E_TEST_USER_EMAIL || 'dev@profiletailors.com',
  password: process.env.E2E_TEST_USER_PASSWORD ?? (() => { throw new Error('E2E_TEST_USER_PASSWORD env var must be set'); })(),
  /** Auto-derived from email prefix by the backend */
  username: 'dev',
} as const

export const VALID_CREDENTIALS = {
  email: E2E_TEST_USER.email,
  password: E2E_TEST_USER.password,
} as const

export const INVALID_CREDENTIALS = {
  email: 'wrong@email.com',
  password: 'incorrect',
} as const

export const NONEXISTENT_EMAIL_CREDENTIALS = {
  email: 'nonexistent-12345@example.com',
  password: 'SomePass123!',
} as const

export const NEW_USER = {
  email: `e2e-new-${Date.now()}@profiletailors.com`,
  password: 'SecurePass123!',
} as const

export const SHORT_PASSWORD = 'Ab1'

export const WHITESPACE_EMAIL = '  Test@Example.com  '
export const NORMALIZED_EMAIL = 'test@example.com'

export const PROTECTED_ROUTES = [
  { path: APP_URL.scheduler, name: 'scheduler' },
  { path: APP_URL.analytics, name: 'analytics' },
  { path: APP_URL.settings, name: 'settings' },
  { path: APP_URL.linkedinCallback, name: 'linkedin-callback' },
] as const

export const GUEST_ROUTES = [
  { path: APP_URL.login, name: 'login' },
  { path: APP_URL.register, name: 'register' },
] as const

export const I18N_TEXT = {
  en: {
    titleLogin: 'Welcome back',
    subtitleLogin: 'Sign in to continue into your workspace dashboard.',
    submitLogin: 'Sign in',
    submitRegister: 'Create account',
    alternateLabelLogin: 'Need an account?',
    alternateActionLogin: 'Register',
    alternateLabelRegister: 'Already have an account?',
    alternateActionRegister: 'Sign in',
    titleRegister: 'Create account',
    emailPlaceholder: /you@example\.com/,
    badge: 'LOCAL ACCESS',
    heroFeatures: ['SECURITY', 'FOCUS', 'WORKFLOW'],
  },
  es: {
    titleLogin: 'Bienvenido de nuevo',
    subtitleLogin: 'Inicia sesión para continuar en tu panel de trabajo.',
    submitLogin: 'Iniciar sesión',
    submitRegister: 'Crear cuenta',
    alternateLabelLogin: '¿Necesitas una cuenta?',
    alternateActionLogin: 'Crear cuenta',
    alternateLabelRegister: '¿Ya tienes una cuenta?',
    alternateActionRegister: 'Iniciar sesión',
    titleRegister: 'Crear cuenta',
    emailPlaceholder: /tu@ejemplo\.com/,
    badge: 'ACCESO LOCAL',
    heroFeatures: ['SEGURIDAD', 'ENFOQUE', 'FLUJO'],
  },
} as const
