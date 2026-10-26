import type { Page } from '@playwright/test'

export type WaitlistEntryStatus = 'PENDING' | 'INVITED' | 'CONVERTED' | 'CANCELLED'

export interface MockWaitlistEntry {
  id: string
  email: string
  status: WaitlistEntryStatus
  joinedAt: string
  invitedAt: string | null
  waitlistKey: string
  source: string
  version: number
}

export interface BulkResultEntry {
  entryId: string
  outcome: 'invited' | 'skipped' | 'failed'
  invitationId?: string
  code?: string
}

export function makeEntry(
  emailPrefix: string,
  status: WaitlistEntryStatus = 'PENDING',
): MockWaitlistEntry {
  const id = crypto.randomUUID()
  const email = `${emailPrefix}@example.com`
  return {
    id,
    email,
    status,
    joinedAt: '2026-09-01T10:00:00.000Z',
    invitedAt: status === 'PENDING' ? null : '2026-09-02T10:00:00.000Z',
    waitlistKey: 'profile-tailors-launch',
    source: 'web',
    version: 1,
  }
}

export function toListItem(entry: MockWaitlistEntry) {
  return {
    id: entry.id,
    email: entry.email,
    normalizedEmail: entry.email.toLowerCase(),
    status: entry.status,
    joinedAt: entry.joinedAt,
    invitedAt: entry.invitedAt,
    waitlistKey: entry.waitlistKey,
    source: entry.source,
    version: entry.version,
  }
}

export function toSummary(entries: MockWaitlistEntry[]) {
  const count = (status: WaitlistEntryStatus) => entries.filter((e) => e.status === status).length
  return {
    PENDING: count('PENDING'),
    INVITED: count('INVITED'),
    CONVERTED: count('CONVERTED'),
    CANCELLED: count('CANCELLED'),
  }
}

export const OWNER_PRINCIPAL = {
  principalId: '7eea372c-238e-5460-89ae-357e719c39da',
  email: 'owner@example.com',
  displayName: 'E2E Owner',
  platformRoles: ['PLATFORM_OWNER'],
}

export const SUPPORT_PRINCIPAL = {
  principalId: '88f72487-9ea0-5439-b38e-cb2a044bd8da',
  email: 'support@example.com',
  displayName: 'E2E Support',
  platformRoles: ['SUPPORT_AGENT'],
}

export function mockTokens(email: string) {
  return {
    accessToken: `e2e-access-token-for-${email}`,
    tokenType: 'Bearer',
    expiresIn: 900,
    principalId:
      email === OWNER_PRINCIPAL.email ? OWNER_PRINCIPAL.principalId : SUPPORT_PRINCIPAL.principalId,
    email,
    username: email.split('@')[0] ?? email,
    emailStatus: 'VERIFIED',
    workspaceId: null,
  }
}

export async function loginAs(
  page: Page,
  email: string,
  password = 'E2eTestPass123!',
): Promise<void> {
  await page.goto('/login')
  await page.getByTestId('admin-login-email').fill(email)
  await page.getByTestId('admin-login-password').fill(password)
  await Promise.all([
    page.waitForResponse(
      (response) =>
        response.request().method() === 'POST' && response.url().includes('/api/auth/login'),
    ),
    page.getByRole('button', { name: /sign in|iniciar sesión/i }).click(),
  ])
  await page.waitForURL((url) => !url.pathname.startsWith('/login'))
}
