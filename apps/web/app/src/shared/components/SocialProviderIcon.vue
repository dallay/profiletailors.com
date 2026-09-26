<script setup lang="ts">
import { getProviderPresentation } from '@shared/lib/provider-presentation'

defineProps<{
  provider: string
}>()
</script>

<template>
  <svg
    v-if="getProviderPresentation(provider).icon === 'linkedin'"
    viewBox="0 0 24 24"
    fill="currentColor"
    aria-hidden="true"
    class="size-full"
  >
    <path d="M20.45 20.45h-3.56v-5.57c0-1.33-.03-3.04-1.85-3.04-1.85 0-2.14 1.45-2.14 2.94v5.67H9.35V9h3.41v1.56h.05c.48-.9 1.64-1.85 3.37-1.85 3.6 0 4.27 2.37 4.27 5.46v6.28ZM5.34 7.43a2.06 2.06 0 1 1 0-4.13 2.06 2.06 0 0 1 0 4.13Zm1.78 13.02H3.56V9h3.56v11.45ZM22.23 0H1.77C.79 0 0 .77 0 1.73v20.54C0 23.23.79 24 1.77 24h20.45c.98 0 1.78-.77 1.78-1.73V1.73C24 .77 23.2 0 22.22 0h.01Z" />
  </svg>
  <svg
    v-else-if="getProviderPresentation(provider).icon === 'threads'"
    data-testid="provider-icon-threads"
    viewBox="0 0 24 24"
    fill="currentColor"
    aria-hidden="true"
    class="size-full"
  >
    <path d="M12.02 2C6.08 2 2.4 5.7 2.4 11.95c0 6.3 3.77 10.05 9.92 10.05 5.78 0 9.28-3.16 9.28-8.05 0-4.45-2.92-7.24-7.7-7.24-3.16 0-5.53 1.54-5.53 3.84 0 1.8 1.48 3.1 3.56 3.1 2.07 0 3.47-.9 4.15-2.34 1.28.68 1.96 1.83 1.96 3.44 0 2.54-1.75 4.18-5.55 4.18-4.01 0-6.24-2.3-6.24-6.9 0-4.47 2.15-6.57 5.77-6.57 2.57 0 4.29.98 5.24 2.96l1.87-.9C18.05 4.08 15.65 2 12.02 2Zm.63 9.85c-.94 0-1.6-.47-1.6-1.3 0-1.02 1.16-1.84 2.86-1.84 1.02 0 1.92.15 2.7.45-.47 1.65-1.8 2.69-3.96 2.69Z" />
  </svg>
  <span v-else aria-hidden="true" class="size-full flex items-center justify-center text-[10px] font-bold leading-none uppercase">
    {{ getProviderPresentation(provider).badge }}
  </span>
</template>
