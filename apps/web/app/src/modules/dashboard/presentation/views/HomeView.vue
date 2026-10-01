<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@modules/auth/infrastructure/auth.store'
import { Button } from '@/components/ui/button'
import CreatePostModal from '@modules/publishing/presentation/components/CreatePostModal.vue'
import DashboardLayout from '@modules/dashboard/presentation/components/DashboardLayout.vue'
import { toast } from 'vue-sonner'
import { useSidebar } from '@/components/ui/sidebar'

const auth = useAuthStore()
const { t } = useI18n()
const sidebar = useSidebar()

const isModalOpen = ref(false)

function handleOpenModal() {
  isModalOpen.value = true
}

function handleCreated(options: { keepOpen?: boolean } = {}) {
  if (!options.keepOpen) isModalOpen.value = false
  toast.success(t('composer.scheduleSuccessToast'))
}

function openChannelConnections() {
  if (sidebar.isMobile.value) {
    sidebar.setOpenMobile(true)
    return
  }
  sidebar.setOpen(true)
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
        <Button @click="handleOpenModal">
          {{ $t('dashboard.newPost') }}
        </Button>
      </div>
    </div>

    <div class="rounded-xl border border-border-visible bg-bg-surface px-4 py-3">
      <p class="text-sm font-medium text-text-display">{{ $t('dashboard.previewDataTitle') }}</p>
      <p class="mt-1 text-xs leading-5 text-text-secondary">
        {{ $t('dashboard.previewDataDescription') }}
      </p>
    </div>

    <DashboardLayout />

    <CreatePostModal
      :is-open="isModalOpen"
      provider="unsplash"
      @close="isModalOpen = false"
      @connect-channels="openChannelConnections"
      @created="handleCreated"
    />
  </div>
</template>
