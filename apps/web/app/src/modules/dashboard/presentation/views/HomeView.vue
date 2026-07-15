<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { Button } from '@/components/ui/button'
import CreatePostModal from '@modules/publishing/presentation/components/CreatePostModal.vue'
import DashboardLayout from '@modules/dashboard/presentation/components/DashboardLayout.vue'
import { toast } from 'vue-sonner'

const auth = useAuthStore()
const { t } = useI18n()

const isModalOpen = ref(false)

// ---------------------------------------------------------------------------
// Feature flag — localStorage toggle to restore old HomeView during development.
// Key: "pt-dashboard-new" | Values: "true" (default) → new layout, "false" → legacy
// ---------------------------------------------------------------------------
const STORAGE_KEY = 'pt-dashboard-new'

function readFlag(): boolean {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === null) return true // new dashboard by default
    return stored !== 'false'
  } catch {
    return true
  }
}

const showNewDashboard = ref(readFlag())

function toggleDashboardVersion() {
  showNewDashboard.value = !showNewDashboard.value
  try {
    localStorage.setItem(STORAGE_KEY, String(showNewDashboard.value))
  } catch {
    // localStorage unavailable — flag is ephemeral only
  }
}

function handleOpenModal() {
  isModalOpen.value = true
}

function handleCreated() {
  isModalOpen.value = false
  toast.success(t('composer.scheduleSuccessToast'))
}
</script>

<template>
  <div class="mx-auto w-full max-w-7xl space-y-8">
    <div class="flex items-center justify-between">
      <div class="space-y-1">
        <h2 class="text-3xl font-light tracking-tight text-text-display">
          {{ $t('dashboard.welcome') }}, {{ auth.displayName }}
        </h2>
        <p class="text-sm text-text-secondary">
          {{ $t('dashboard.subtitle') }}
        </p>
      </div>
      <div class="flex items-center gap-2">
        <span
          class="text-[10px] font-[var(--font-space-mono)] uppercase tracking-wider text-[var(--text-secondary)] select-none"
        >
          {{ showNewDashboard ? 'New' : 'Legacy' }}
        </span>
        <Button
          variant="ghost"
          size="sm"
          class="text-[10px] font-[var(--font-space-mono)] uppercase tracking-wider h-7"
          @click="toggleDashboardVersion"
        >
          {{ showNewDashboard ? 'Switch to Legacy' : 'Switch to New' }}
        </Button>
        <Button @click="handleOpenModal">
          {{ $t('dashboard.newPost') }}
        </Button>
      </div>
    </div>

    <DashboardLayout v-if="showNewDashboard" />

    <div
      v-else
      class="rounded-xl border border-[var(--border-color)] bg-[var(--background-surface)] p-8 text-center"
    >
      <p class="text-sm text-[var(--text-secondary)]">
        Legacy dashboard is disabled. Toggle back to the new dashboard.
      </p>
    </div>

    <CreatePostModal
      :is-open="isModalOpen"
      provider="unsplash"
      @close="isModalOpen = false"
      @created="handleCreated"
    />
  </div>
</template>
