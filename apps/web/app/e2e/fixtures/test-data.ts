/**
 * Test data constants for E2E login tests.
 *
 * Replay mode is intentionally backend-free: credentials must match the
 * login payload recorded in hars/auth-flow.har so Playwright can serve the
 * mocked responses from HAR.
 *
 * Record mode (UPDATE_HAR=true) may override these values via env vars to
 * refresh the HAR against a live backend.
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

/**
 * E2E credentials — sourced from environment variables.
 * Email falls back to a default dev account; the password is required and
 * tests fail fast if E2E_TEST_USER_PASSWORD is missing.
 */
const E2E_EMAIL = process.env.E2E_TEST_USER_EMAIL || 'dev@profiletailors.com'
const E2E_PASSWORD = process.env.E2E_TEST_USER_PASSWORD

if (!E2E_PASSWORD) {
  throw new Error(
    'E2E_TEST_USER_PASSWORD environment variable is required. ' +
    'Set it in your shell or CI pipeline before running E2E tests.',
  )
}

export const E2E_TEST_USER = {
  email: E2E_EMAIL,
  password: E2E_PASSWORD,
  /** Must match the HAR payload in replay mode */
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

export const LONG_PASSWORD = 'a'.repeat(129)

export const WHITESPACE_EMAIL = '  Test@Example.com  '
export const NORMALIZED_EMAIL = 'test@example.com'

export const INVALID_EMAIL_FORMATS = [
  'not-an-email',
  'missing@domain',
  '@nodomain.com',
  'spaces in@email.com',
] as const

export const DUPLICATE_EMAIL = {
  email: 'existing@profiletailors.com',
  password: 'SecurePass123!',
} as const

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
