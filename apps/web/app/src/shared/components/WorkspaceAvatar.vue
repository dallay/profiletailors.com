<script setup lang="ts">
import { computed } from 'vue'
import * as LucideIcons from '@lucide/vue'
import type { Component } from 'vue'
import { toPascalCase } from '@shared/lib/string-utils'

const props = withDefaults(
  defineProps<{
    name: string
    icon?: string | null
    size?: 'sm' | 'md'
  }>(),
  { icon: null, size: 'md' },
)

const sizeClasses: Record<string, string> = {
  sm: 'size-8 rounded-lg text-[10px]',
  md: 'size-10 rounded-xl text-xs',
}

const iconSizes: Record<string, number> = { sm: 14, md: 18 }

const iconComponent = computed<Component | null>(() => {
  if (!props.icon) return null
  const iconName = toPascalCase(props.icon)
  // biome-ignore lint/performance/noDynamicNamespaceImportAccess: icon name is user-provided at runtime, static imports are not feasible
  return LucideIcons[iconName as keyof typeof LucideIcons] as Component ?? null
})

</script>

<template>
  <div
    class="flex shrink-0 items-center justify-center bg-text-display text-bg-primary shadow-lg"
    :class="sizeClasses[size]"
  >
    <component
      :is="iconComponent"
      v-if="iconComponent"
      :size="iconSizes[size]"
    />
    <span v-else class="font-mono font-bold">
      {{ name.charAt(0).toUpperCase() || 'W' }}
    </span>
  </div>
</template>
