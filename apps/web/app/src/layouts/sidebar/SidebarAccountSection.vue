<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { LogOut, Settings } from '@lucide/vue'
import { usePopoverDismissal } from '@shared/composables/usePopoverDismissal'
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
const menuRef = ref<HTMLElement | null>(null)

const { open, toggle, close } = usePopoverDismissal({
  container: containerRef,
  trigger: triggerRef,
})

// Focus management for menu popover
watch(open, async (isOpen) => {
  if (isOpen) {
    await nextTick()
    const firstItem = menuRef.value?.querySelector<HTMLElement>('[role="menuitem"]')
    firstItem?.focus()
  }
})

function handleMenuKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
    e.preventDefault()
    const items = Array.from(
      menuRef.value?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? [],
    )
    if (items.length === 0) return
    const currentIndex = items.indexOf(document.activeElement as HTMLElement)
    let nextIndex: number
    if (currentIndex === -1) {
      // Focus first item — roving tabindex: only this item is tabbable
      items.forEach((item, i) => item.setAttribute('tabindex', i === 0 ? '0' : '-1'))
      nextIndex = 0
    } else {
      nextIndex = e.key === 'ArrowDown'
        ? (currentIndex + 1) % items.length
        : (currentIndex - 1 + items.length) % items.length
    }
    // Update roving tabindex: only the next item is tabbable
    items.forEach((item, i) => item.setAttribute('tabindex', i === nextIndex ? '0' : '-1'))
    items[nextIndex]?.focus()
  }
}

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
    class="relative min-w-0"
  >
      <div
        v-if="open"
        id="sidebar-account-menu"
        ref="menuRef"
        class="absolute right-0 bottom-full mb-2 w-full min-w-56 rounded-lg border border-border-subtle bg-bg-surface p-1.5 shadow-lg group-data-[collapsible=icon]:left-0"
        role="menu"
        @keydown="handleMenuKeydown"
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
          tabindex="-1"
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
          tabindex="-1"
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
      class="flex h-12 w-full min-w-0 items-center gap-2 rounded-md p-2 text-left transition-colors hover:bg-bg-primary/70 group-data-[collapsible=icon]:size-8! group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:p-0!"
      type="button"
      aria-haspopup="menu"
      :aria-expanded="open ? 'true' : 'false'"
      aria-controls="sidebar-account-menu"
      @click.stop="toggle"
    >
      <div class="flex size-8 shrink-0 items-center justify-center rounded-lg bg-bg-primary font-mono text-xs font-bold text-text-display">
        {{ user.initials }}
      </div>
      <span class="sr-only">{{ user.displayName }}</span>

      <div class="min-w-0 flex-1 group-data-[collapsible=icon]:hidden">
        <p class="truncate text-sm font-medium text-text-display">
          {{ user.displayName }}
        </p>
        <p class="truncate text-xs text-text-secondary">
          {{ user.email || 'Session active' }}
        </p>
      </div>
    </button>
  </div>
</template>
