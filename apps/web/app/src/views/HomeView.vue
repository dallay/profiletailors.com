<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'
import CreatePostModal from '@/components/CreatePostModal.vue'
import DashboardLayout from '@/components/dashboard/DashboardLayout.vue'

const auth = useAuthStore()

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
}
</script>

<template>
  <div class="mx-auto w-full max-w-7xl space-y-8">
    <!-- Welcome Header + Create Post -->
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

    <!-- Feature flag: new dashboard vs legacy -->
    <DashboardLayout v-if="showNewDashboard" />

    <!-- Legacy fallback (placeholder — restore old 4-card grid here if needed) -->
    <div
      v-else
      class="rounded-xl border border-[var(--border-color)] bg-[var(--background-surface)] p-8 text-center"
    >
      <p class="text-sm text-[var(--text-secondary)]">
        Legacy dashboard is disabled. Toggle back to the new dashboard.
      </p>
    </div>

    <!-- Create Post Modal Dialog -->
    <CreatePostModal
      :is-open="isModalOpen"
      @close="isModalOpen = false"
      @created="handleCreated"
    />
  </div>
</template>
