<script setup lang="ts">
import { ref } from 'vue'
import { LogOut, Settings } from '@lucide/vue'
import ThemeToggle from '@/components/ThemeToggle.vue'
import { usePopoverDismissal } from '@/composables/usePopoverDismissal'

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
</script>

<template>
  <div
    ref="containerRef"
    class="relative"
  >
    <div
      v-if="open"
      id="sidebar-account-menu"
      class="absolute right-0 bottom-full mb-2 w-full rounded-2xl border border-border-subtle bg-bg-surface p-2 shadow-2xl"
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

        <!-- Theme is the ONLY theme control. The animated sun/moon SVG inside
             ThemeToggle already conveys state — no static "Theme" label row. -->
        <div class="flex items-center gap-3 rounded-xl border border-transparent px-3 py-2.5 text-sm text-text-secondary">
          <ThemeToggle />
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
      class="flex w-full items-center gap-3 rounded-2xl border border-border-subtle bg-bg-surface/80 p-3 text-left transition-all hover:border-border-visible hover:bg-bg-surface"
      type="button"
      aria-haspopup="menu"
      :aria-expanded="open ? 'true' : 'false'"
      aria-controls="sidebar-account-menu"
      @click.stop="toggle"
    >
      <div class="flex size-10 shrink-0 items-center justify-center rounded-xl border border-border-visible bg-bg-primary font-mono text-xs font-bold text-text-display">
        {{ user.initials }}
      </div>

      <div class="min-w-0 flex-1">
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
