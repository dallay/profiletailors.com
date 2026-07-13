<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { LogOut, Settings } from '@lucide/vue'
import { usePopoverDismissal } from '@/composables/usePopoverDismissal'
import { useSettingsStore } from '@modules/settings/infrastructure/settings.store'

defineProps<{
  user: {
    displayName: string
    email: string | null
    initials: string
    isRefreshing: boolean
  }
}>()

const emit = defineEmits<{
  (e: 'openSettings'): void
  (e: 'logout'): void
}>()

const { t } = useI18n()
const settings = useSettingsStore()

const containerRef = ref<HTMLElement | null>(null)
const triggerRef = ref<HTMLElement | null>(null)

const { open, toggle, close } = usePopoverDismissal({
  container: containerRef,
  trigger: triggerRef,
})

function onOpenSettings() {
  emit('openSettings')
  close()
}

function onLogout() {
  emit('logout')
  close()
}

function segmentedControlClass(isActive: boolean) {
  return isActive
    ? 'bg-text-display text-bg-primary'
    : 'text-text-secondary hover:text-text-display'
}
</script>

<template>
  <div
    ref="containerRef"
    class="relative"
  >
    <div
      v-if="open"
      id="sidebar-account-menu"
      class="absolute right-0 bottom-full mb-2 w-full rounded-2xl border border-border-subtle bg-bg-surface p-2 shadow-2xl group-data-[collapsible=icon]:left-0 group-data-[collapsible=icon]:min-w-56"
      role="menu"
    >
      <div class="px-2 py-2">
        <p class="truncate text-sm font-medium text-text-display">
          {{ user.isRefreshing ? 'Refreshing session...' : user.displayName }}
        </p>
        <p class="truncate font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary">
          {{ user.email || 'Session active' }}
        </p>
      </div>

      <div class="my-2 border-t border-border-subtle" />

      <div class="space-y-1">
        <button
          role="menuitem"
          class="flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2.5 text-left text-sm text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display"
          type="button"
          @click="onOpenSettings"
        >
          <Settings class="size-4 shrink-0" />
          <span>Account settings</span>
        </button>

        <div class="rounded-xl border border-border-subtle bg-bg-primary/60 px-3 py-3">
          <div class="flex items-center justify-between gap-3">
            <div>
              <p class="text-sm font-medium text-text-display">
                {{ t('settings.themeLabel') }}
              </p>
              <p class="mt-1 text-xs leading-5 text-text-secondary">
                {{ t('settings.themeDesc') }}
              </p>
            </div>

            <div
              class="inline-flex items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[10px]"
              role="radiogroup"
              :aria-label="t('settings.themeLabel')"
            >
              <!-- biome-ignore lint/a11y/noLabelWithoutControl: label wraps sr-only input, valid association -->
              <label
                class="cursor-pointer rounded-full px-3 py-1.5 font-bold uppercase tracking-[0.14em] transition-colors focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-text-display"
                :class="segmentedControlClass(settings.currentTheme === 'dark')"
              >
                <input
                  type="radio"
                  name="theme"
                  value="dark"
                  :checked="settings.currentTheme === 'dark'"
                  class="sr-only"
                  @change="settings.setTheme('dark')"
                />
                {{ t('settings.themeDark') }}
              </label>
              <!-- biome-ignore lint/a11y/noLabelWithoutControl: label wraps sr-only input, valid association -->
              <label
                class="cursor-pointer rounded-full px-3 py-1.5 font-bold uppercase tracking-[0.14em] transition-colors focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-text-display"
                :class="segmentedControlClass(settings.currentTheme === 'light')"
              >
                <input
                  type="radio"
                  name="theme"
                  value="light"
                  :checked="settings.currentTheme === 'light'"
                  class="sr-only"
                  @change="settings.setTheme('light')"
                />
                {{ t('settings.themeLight') }}
              </label>
            </div>
          </div>
        </div>

        <div class="rounded-xl border border-border-subtle bg-bg-primary/60 px-3 py-3">
          <div class="flex items-center justify-between gap-3">
            <div>
              <p class="text-sm font-medium text-text-display">
                {{ t('settings.languageLabel') }}
              </p>
              <p class="mt-1 text-xs leading-5 text-text-secondary">
                {{ t('settings.languageDesc') }}
              </p>
            </div>

            <div
              class="inline-flex items-center rounded-full border border-border-visible bg-bg-surface p-0.5 font-mono text-[10px]"
              role="radiogroup"
              :aria-label="t('settings.languageLabel')"
            >
              <label
                class="cursor-pointer rounded-full px-3 py-1.5 font-bold uppercase tracking-[0.14em] transition-colors focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-text-display"
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
                class="cursor-pointer rounded-full px-3 py-1.5 font-bold uppercase tracking-[0.14em] transition-colors focus-within:outline focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-text-display"
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

        <button
          role="menuitem"
          class="flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2.5 text-left text-sm text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display"
          type="button"
          @click="onLogout"
        >
          <LogOut class="size-4 shrink-0" />
          <span>Log Out</span>
        </button>
      </div>
    </div>

    <button
      ref="triggerRef"
      class="flex w-full items-center gap-3 rounded-2xl border border-border-subtle bg-bg-surface/80 p-3 text-left transition-all hover:border-border-visible hover:bg-bg-surface group-data-[collapsible=icon]:p-0 group-data-[collapsible=icon]:size-10 group-data-[collapsible=icon]:justify-center"
      type="button"
      aria-haspopup="menu"
      :aria-expanded="open ? 'true' : 'false'"
      aria-controls="sidebar-account-menu"
      @click.stop="toggle"
    >
      <div class="flex size-10 shrink-0 items-center justify-center rounded-xl border border-border-visible bg-bg-primary font-mono text-xs font-bold text-text-display group-data-[collapsible=icon]:size-full">
        {{ user.initials }}
      </div>
      <span class="sr-only">{{ user.displayName }}</span>

      <div class="min-w-0 flex-1 group-data-[collapsible=icon]:hidden">
        <p class="truncate text-sm font-medium text-text-display">
          {{ user.displayName }}
        </p>
        <p class="truncate font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary">
          {{ user.email || 'Session active' }}
        </p>
      </div>
    </button>
  </div>
</template>
