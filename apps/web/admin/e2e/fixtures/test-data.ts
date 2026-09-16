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

let entrySequence = 0

export function makeEntry(
  emailPrefix: string,
  status: WaitlistEntryStatus = 'PENDING',
): MockWaitlistEntry {
  entrySequence += 1
  const id = `e2e-entry-${entrySequence}`
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

export function resetEntrySequence(): void {
  entrySequence = 0
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
  principalId: 'e2e-owner-001',
  email: 'owner@example.com',
  displayName: 'E2E Owner',
  platformRoles: ['PLATFORM_OWNER'],
}

export const SUPPORT_PRINCIPAL = {
  principalId: 'e2e-support-001',
  email: 'support@example.com',
  displayName: 'E2E Support',
  platformRoles: ['SUPPORT_AGENT'],
}

export function mockTokens(email: string) {
  return {
    accessToken: `e2e-access-token-for-${email}`,
    tokenType: 'Bearer',
    expiresIn: 900,
    principalId: `e2e-principal-for-${email}`,
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
