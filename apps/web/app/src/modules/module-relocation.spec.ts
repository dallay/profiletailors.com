import { describe, expect, it } from 'vitest'

describe('DALLAY-468 module relocation guard', () => {
  it('resolves auth, workspace, and settings files from @modules paths', async () => {
    await expect(import('@modules/auth/presentation/AuthView.vue')).resolves.toBeDefined()
    await expect(
      import('@modules/auth/presentation/LinkedInCallbackView.vue'),
    ).resolves.toBeDefined()
    await expect(import('@modules/auth/infrastructure/auth.store')).resolves.toHaveProperty(
      'useAuthStore',
    )
    await expect(import('@modules/auth/infrastructure/auth-api')).resolves.toHaveProperty(
      'createApiFetch',
    )
    await expect(
      import('@modules/workspace/infrastructure/workspace.store'),
    ).resolves.toHaveProperty('useWorkspaceStore')
    await expect(
      import('@modules/workspace/presentation/components/WorkspaceIconModal.vue'),
    ).resolves.toBeDefined()
    await expect(import('@modules/settings/presentation/SettingsView.vue')).resolves.toBeDefined()
    await expect(import('@modules/settings/infrastructure/settings.store')).resolves.toHaveProperty(
      'useSettingsStore',
    )
  })
})
