<script setup lang="ts">
import {
  ChevronsUpDown,
  LogOut,
  Settings,
} from '@lucide/vue'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import ThemeToggle from '@/components/ThemeToggle.vue'
import {
  SidebarMenu,
  SidebarMenuItem,
} from '@/components/ui/sidebar'
import { useAuthStore } from '@/stores/auth'
import { useSettingsStore } from '@/stores/settings'

const auth = useAuthStore()
const router = useRouter()
const settings = useSettingsStore()
const { t } = useI18n()

const accountMenuOpen = ref(false)
const accountMenuRef = ref<HTMLElement | null>(null)

function toggleAccountMenu() {
  accountMenuOpen.value = !accountMenuOpen.value
}

function closeAccountMenu() {
  accountMenuOpen.value = false
}

async function handleLogout() {
  closeAccountMenu()
  await auth.logout()
  await router.replace('/login')
}

function handleDocumentClick(event: MouseEvent) {
  if (!accountMenuOpen.value) {
    return
  }

  const target = event.target
  if (!(target instanceof Node)) {
    return
  }

  if (!accountMenuRef.value?.contains(target)) {
    closeAccountMenu()
  }
}

function handleEscapeKey(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    closeAccountMenu()
  }
}

onMounted(() => {
  document.addEventListener('click', handleDocumentClick)
  document.addEventListener('keydown', handleEscapeKey)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleDocumentClick)
  document.removeEventListener('keydown', handleEscapeKey)
})
</script>

<template>
  <SidebarMenu>
    <SidebarMenuItem>
      <div ref="accountMenuRef" class="relative">
        <div
          v-if="accountMenuOpen"
          class="absolute right-0 bottom-full mb-2 w-full rounded-2xl border border-border-subtle bg-bg-surface p-2 shadow-2xl group-data-[collapsible=icon]:w-72"
        >
          <div class="px-2 py-2 group-data-[collapsible=icon]:hidden">
            <p class="truncate text-sm font-medium text-text-display">
              {{ auth.isRefreshingProfile ? 'Refreshing session...' : auth.displayName }}
            </p>
            <p class="truncate font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary">
              {{ auth.user?.email || 'Session active' }}
            </p>
          </div>

          <div class="my-2 border-t border-border-subtle" />

          <div class="space-y-1">
            <button
              class="flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2.5 text-left text-sm text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display"
              type="button"
              @click="router.push('/settings'); closeAccountMenu()"
            >
              <Settings class="size-4 shrink-0" />
              <span>Account settings</span>
            </button>

            <div class="flex items-center gap-3 rounded-xl border border-transparent px-3 py-2.5 text-sm text-text-secondary">
              <ThemeToggle />
              <span>Theme</span>
              <span class="ml-auto font-mono text-[10px] uppercase tracking-[0.12em] text-text-secondary">
                {{ settings.currentTheme }}
              </span>
            </div>

            <button
              class="flex w-full items-center gap-3 rounded-xl border border-transparent px-3 py-2.5 text-left text-sm text-text-secondary transition-colors hover:border-border-subtle hover:bg-bg-primary/70 hover:text-text-display"
              type="button"
              @click="handleLogout"
            >
              <LogOut class="size-4 shrink-0" />
              <span>{{ t('nav.logout') }}</span>
            </button>
          </div>
        </div>

        <button
          class="flex w-full items-center gap-3 rounded-2xl border border-border-subtle bg-bg-surface/80 p-3 text-left transition-all hover:border-border-visible hover:bg-bg-surface group-data-[collapsible=icon]:justify-center group-data-[collapsible=icon]:px-2 group-data-[collapsible=icon]:py-2"
          type="button"
          @click.stop="toggleAccountMenu"
        >
          <div class="flex size-10 shrink-0 items-center justify-center rounded-xl border border-border-visible bg-bg-primary font-mono text-xs font-bold text-text-display">
            {{ auth.userInitials }}
          </div>

          <div class="min-w-0 flex-1 group-data-[collapsible=icon]:hidden">
            <p class="truncate text-sm font-medium text-text-display">
              {{ auth.displayName }}
            </p>
            <p class="truncate font-mono text-[10px] uppercase tracking-[0.14em] text-text-secondary">
              {{ auth.user?.email || 'Active' }}
            </p>
          </div>

          <ChevronsUpDown class="hidden size-4 text-text-secondary group-data-[collapsible=icon]:hidden md:block" />
        </button>
      </div>
    </SidebarMenuItem>
  </SidebarMenu>
</template>
