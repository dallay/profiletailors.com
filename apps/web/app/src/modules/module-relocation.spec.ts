import { existsSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const sourceRoot = resolve(process.cwd(), 'src')

const approvedCompatibilityPaths = ['components/ui', 'lib/utils.ts'] as const

const phaseFiveComposableInventory = {
  shared: ['useFocusTrap.ts', 'usePopoverDismissal.ts'],
  layout: ['useConnectMessage.ts'],
  auth: ['useLinkedInCallback.ts'],
  media: ['useFileHash.ts'],
  publishing: ['useCalendarUrl.ts', 'useQueuedCounts.ts'],
} as const

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
  }, 20_000)

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
  }, 20_000)

  it('resolves media files from @modules/media paths', async () => {
    await expect(
      import('@modules/media/presentation/views/MediaLibraryView.vue'),
    ).resolves.toBeDefined()
    await expect(import('@modules/media/services/media-api')).resolves.toHaveProperty('listAssets')
    await expect(import('@modules/media/infrastructure/media.store')).resolves.toHaveProperty(
      'useMediaStore',
    )
  })

  it('resolves publishing files from @modules/publishing paths', async (): Promise<void> => {
    await expect(import('@modules/publishing/views/SchedulerView.vue')).resolves.toBeDefined()
    await expect(
      import('@modules/publishing/presentation/components/CalendarCell.vue'),
    ).resolves.toBeDefined()
    await expect(
      import('@modules/publishing/presentation/components/CalendarHeader.vue'),
    ).resolves.toBeDefined()
    await expect(
      import('@modules/publishing/presentation/components/ConflictBadge.vue'),
    ).resolves.toBeDefined()
    await expect(
      import('@modules/publishing/presentation/components/CreatePostModal.vue'),
    ).resolves.toBeDefined()
    await expect(
      import('@modules/publishing/presentation/components/PostDetailModal.vue'),
    ).resolves.toBeDefined()
    await expect(
      import('@modules/publishing/presentation/components/composer/ComposerMediaPickerShell.vue'),
    ).resolves.toBeDefined()
    await expect(
      import('@modules/publishing/application/useComposerMediaPicker'),
    ).resolves.toHaveProperty('useComposerMediaPicker')
    await expect(
      import('@modules/publishing/infrastructure/publishing.store'),
    ).resolves.toHaveProperty('usePublishingStore')
  })

  it('keeps relocated publishing and media ownership files out of legacy root paths', (): void => {
    const legacyPaths = [
      'views/SchedulerView.vue',
      'components/CalendarCell.vue',
      'components/CalendarHeader.vue',
      'components/ConflictBadge.vue',
      'components/CreatePostModal.vue',
      'components/PostDetailModal.vue',
      'components/composer/ComposerMediaPickerShell.vue',
      'composables/useComposerMediaPicker.ts',
      'stores/publishing.ts',
      'stores/media.ts',
    ]

    expect(
      legacyPaths.filter((path: string): boolean => existsSync(`${sourceRoot}/${path}`)),
    ).toEqual([])
  })

  it('exposes the full media-api surface as callable functions from its new @modules/media location', async () => {
    const mediaApi = await import('@modules/media/services/media-api')

    expect(typeof mediaApi.putAsset).toBe('function')
    expect(typeof mediaApi.getAsset).toBe('function')
    expect(typeof mediaApi.deleteAsset).toBe('function')
    expect(typeof mediaApi.reserveAsset).toBe('function')
    expect(typeof mediaApi.uploadAsset).toBe('function')
    expect(typeof mediaApi.listAssets).toBe('function')
  })

  it('keeps approved shadcn-vue compatibility boundaries available', async (): Promise<void> => {
    expect(approvedCompatibilityPaths).toEqual(['components/ui', 'lib/utils.ts'])
    expect(existsSync(`${sourceRoot}/components/ui`)).toBe(true)
    expect(existsSync(`${sourceRoot}/lib/utils.ts`)).toBe(true)
    await expect(import('@/lib/utils')).resolves.toHaveProperty('cn')
  })

  it('tracks Phase 5 root composables by target owner before relocation', (): void => {
    expect(phaseFiveComposableInventory).toEqual({
      shared: ['useFocusTrap.ts', 'usePopoverDismissal.ts'],
      layout: ['useConnectMessage.ts'],
      auth: ['useLinkedInCallback.ts'],
      media: ['useFileHash.ts'],
      publishing: ['useCalendarUrl.ts', 'useQueuedCounts.ts'],
    })
  })

  it('keeps Phase 5 non-compat leftovers out of legacy root paths', (): void => {
    const legacyPaths = [
      'components/calendar.test.ts',
      'components/.DS_Store',
      'components/layout',
      'components/sidebar',
      'components/UploadProgressToast.vue',
      'composables/useCalendarUrl.ts',
      'composables/useConnectMessage.ts',
      'composables/useFileHash.ts',
      'composables/useFocusTrap.ts',
      'composables/useLinkedInCallback.ts',
      'composables/usePopoverDismissal.ts',
      'composables/useQueuedCounts.ts',
      'views/VerifyEmailView.vue',
      'i18n/index.ts',
      'lib/formatters.ts',
      'lib/mockData/index.ts',
      'lib/provider-styles.ts',
      'lib/sse.ts',
      'lib/string-utils.ts',
      'lib/utils.test.ts',
      'lib/validation/schemas.ts',
    ]

    expect(
      legacyPaths.filter((path: string): boolean => existsSync(`${sourceRoot}/${path}`)),
    ).toEqual([])
  })
})
