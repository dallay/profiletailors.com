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
        <Button @click="handleOpenModal">
          {{ $t('dashboard.newPost') }}
        </Button>
      </div>
    </div>

    <DashboardLayout />

    <CreatePostModal
      :is-open="isModalOpen"
      provider="unsplash"
      @close="isModalOpen = false"
      @created="handleCreated"
    />
  </div>
</template>
