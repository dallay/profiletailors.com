<script setup lang="ts">
import { computed } from 'vue'
import LinkedInPostPreview from './LinkedInPostPreview.vue'
import type { LinkedInPreviewModel } from './post-preview.types'

const props = defineProps<{
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
    <div class="border-b border-border-subtle pb-4">
      <h3 class="font-mono text-xs font-bold tracking-widest text-text-display uppercase">
        {{ title }}
      </h3>
    </div>

    <div class="flex-1 min-h-0 flex items-center justify-center p-2">
      <component :is="activePreviewComponent" v-bind="activePreviewProps" />
    </div>

    <slot name="footer" />
  </div>
</template>
