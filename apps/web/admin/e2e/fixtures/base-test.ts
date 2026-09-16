import { expect, test as base, type Page } from '@playwright/test'

import { registerAdminMocks, type AdminMockOptions, type AdminMockState } from './admin-mocks'
import { OWNER_PRINCIPAL, SUPPORT_PRINCIPAL, loginAs } from './test-data'

export { expect }
export const test = base

export interface OwnerSession {
  state: AdminMockState
}

export async function setupOwnerSession(
  page: Page,
  options: AdminMockOptions = {},
  heading: RegExp = /waitlist/i,
): Promise<OwnerSession> {
  const state = await registerAdminMocks(page, {
    ...options,
    roles: options.roles ?? [...OWNER_PRINCIPAL.platformRoles],
  })
  await loginAs(page, OWNER_PRINCIPAL.email)
  await page.goto('/waitlist')
  await expect(page.getByRole('heading', { name: heading })).toBeVisible()
  return { state }
}

export async function setupSupportSession(page: Page): Promise<void> {
  await registerAdminMocks(page, { roles: [...SUPPORT_PRINCIPAL.platformRoles] })
  await loginAs(page, SUPPORT_PRINCIPAL.email)
}
