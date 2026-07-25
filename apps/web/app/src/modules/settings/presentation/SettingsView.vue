<script setup lang="ts">
import { onMounted } from 'vue'
import { useSettingsStore } from '@modules/settings/infrastructure/settings.store'
import { usePublishingStore } from '@modules/publishing/infrastructure/publishing.store'
import AccountClosureSection from '@modules/settings/presentation/AccountClosureSection.vue'
import PrivacySection from '@modules/settings/presentation/PrivacySection.vue'
import ChannelsSettingsSection from '@modules/settings/presentation/ChannelsSettingsSection.vue'
import WorkspaceSettingsSection from '@modules/settings/presentation/WorkspaceSettingsSection.vue'

const settings = useSettingsStore()
const publishing = usePublishingStore()

onMounted(() => {
  publishing.fetchChannels().catch(() => undefined)
  publishing.fetchConfiguredProviders().catch(() => undefined)
})

function segmentedControlClass(active: boolean) {
  return active
    ? 'bg-bg-primary text-text-display shadow-sm'
    : 'text-text-secondary hover:text-text-body'
}
</script>
<template>
  <div data-testid="settings-shell" class="space-y-10">
    <section data-testid="settings-overview" class="space-y-6">
      <div class="flex flex-col gap-6 lg:flex-row lg:items-start lg:justify-between">
        <div class="space-y-1.5">
          <div class="inline-flex items-center gap-2 rounded-full border border-border-visible bg-bg-surface px-3 py-1 font-mono text-[10px] font-bold uppercase tracking-[0.14em] text-text-secondary">
            <span class="size-1.5 rounded-full bg-text-display" />
            {{ $t('settings.overviewBadge') }}
          </div>
          <h1 class="font-mono text-[11px] font-bold uppercase tracking-[0.16em] text-text-display">
            {{ $t('nav.settings') }}
          </h1>
          <p class="max-w-2xl text-sm leading-7 text-text-secondary">
            {{ $t('settings.subtitle') }}
          </p>
        </div>

        <aside data-testid="settings-preferences-panel" class="flex shrink-0 flex-wrap gap-4 lg:justify-end">
          <div class="rounded-2xl border border-border-subtle bg-bg-surface p-4 shadow-[0_0_0_1px_rgba(255,255,255,0.02)]">
            <p class="font-mono text-[10px] font-bold uppercase tracking-[0.14em] text-text-secondary">
              {{ $t('settings.languageLabel') }}
            </p>
            <div class="mt-3">
              <div
                class="inline-flex rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[10px]"
                role="radiogroup"
                :aria-label="$t('settings.languageLabel')"
              >
                <label
                  data-testid="settings-language-en"
                  class="cursor-pointer rounded-full px-3 py-1.5 font-bold uppercase tracking-[0.14em] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-text-display"
                  :class="segmentedControlClass(settings.currentLocale === 'en')"
                >
                  <input
                    type="radio"
                    name="locale"
                    value="en"
                    :checked="settings.currentLocale === 'en'"
                    class="sr-only"
                    @change="settings.setLocale('en')"
                  />
                  EN
                </label>
                <label
                  data-testid="settings-language-es"
                  class="cursor-pointer rounded-full px-3 py-1.5 font-bold uppercase tracking-[0.14em] transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-text-display"
                  :class="segmentedControlClass(settings.currentLocale === 'es')"
                >
                  <input
                    type="radio"
                    name="locale"
                    value="es"
                    :checked="settings.currentLocale === 'es'"
                    class="sr-only"
                    @change="settings.setLocale('es')"
                  />
                  ES
                </label>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </section>

    <div class="grid gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] xl:items-start">
      <ChannelsSettingsSection />
      <WorkspaceSettingsSection />
    </div>
    <PrivacySection />

    <AccountClosureSection />

    <WorkspaceIconModal
      v-model:open="iconModalOpen"
      :current-icon="workspace.activeWorkspace?.icon ?? null"
      :is-updating="updatingIcon"
      :error-message="iconError"
      @select="selectIcon"
    />
  </div>
</template>
