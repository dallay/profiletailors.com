<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { Button } from '@/components/ui/button'
import CreatePostModal from '@/components/CreatePostModal.vue'
import DashboardLayout from '@/components/dashboard/DashboardLayout.vue'

const auth = useAuthStore()

const isModalOpen = ref(false)

function handleOpenModal() {
  isModalOpen.value = true
}

function handleCreated() {
  isModalOpen.value = false
}
</script>

<template>
  <div class="space-y-8">
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
      <Button @click="handleOpenModal">
        {{ $t('dashboard.newPost') }}
      </Button>
    </div>

    <!-- Dashboard (11 sections) -->
    <DashboardLayout />

    <!-- Create Post Modal Dialog -->
    <CreatePostModal
      :is-open="isModalOpen"
      @close="isModalOpen = false"
      @created="handleCreated"
    />
  </div>
</template>
