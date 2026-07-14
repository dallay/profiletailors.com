import { describe, expect, it } from 'vitest'

describe('module relocation guard', () => {
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

  it('resolves dashboard files from @modules/dashboard paths', async () => {
    await expect(
      import('@modules/dashboard/presentation/views/HomeView.vue'),
    ).resolves.toBeDefined()
    await expect(
      import('@modules/dashboard/presentation/views/AnalyticsView.vue'),
    ).resolves.toBeDefined()
    await expect(
      import('@modules/dashboard/presentation/components/DashboardLayout.vue'),
    ).resolves.toBeDefined()
    await expect(
      import('@modules/dashboard/infrastructure/dashboard.store'),
    ).resolves.toHaveProperty('useDashboardStore')
    await expect(
      import('@modules/dashboard/infrastructure/analytics.store'),
    ).resolves.toHaveProperty('useAnalyticsStore')
    await expect(
      import('@modules/dashboard/infrastructure/insights.store'),
    ).resolves.toHaveProperty('useInsightsStore')
    await expect(
      import('@modules/dashboard/infrastructure/content-pipeline.store'),
    ).resolves.toHaveProperty('useContentPipelineStore')
    await expect(import('@modules/dashboard/domain/dashboard.types')).resolves.toBeDefined()
  })

  it('resolves media files from @modules/media paths', async () => {
    await expect(
      import('@modules/media/presentation/views/MediaLibraryView.vue'),
    ).resolves.toBeDefined()
    await expect(import('@modules/media/services/media-api')).resolves.toHaveProperty('listAssets')
  })
})
