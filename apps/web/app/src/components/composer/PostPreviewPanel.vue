<script setup lang="ts">
import { computed } from 'vue'
import { Info } from '@lucide/vue'
import SocialProviderIcon from '@/components/SocialProviderIcon.vue'
import LinkedInPostPreview from './LinkedInPostPreview.vue'
import type { LinkedInPreviewModel, PreviewProvider } from './post-preview.types'

const props = defineProps<{
  provider: PreviewProvider
  title: string
  linkedinPreview: LinkedInPreviewModel
}>()

const activePreviewComponent = computed(() => {
  return LinkedInPostPreview
})

const activePreviewProps = computed(() => {
  return { preview: props.linkedinPreview }
})
</script>

<template>
  <div class="w-full lg:w-[420px] bg-bg-primary p-6 flex flex-col justify-between overflow-y-auto min-h-0 space-y-6">
    <div class="flex items-center justify-between gap-2 border-b border-border-subtle pb-4">
      <div class="flex items-center gap-2 min-w-0">
        <span class="flex size-4 shrink-0 items-center justify-center text-text-display">
          <SocialProviderIcon :provider="provider" />
        </span>
        <h3 class="truncate text-sm font-semibold text-text-display">
          {{ title }}
        </h3>
      </div>
      <Info class="size-4 shrink-0 text-text-secondary" :aria-label="`About the ${title}`" />
    </div>

    <div class="flex-1 min-h-0 flex items-center justify-center p-2">
      <component :is="activePreviewComponent" v-bind="activePreviewProps" />
    </div>

    <slot name="footer" />
  </div>
</template>
