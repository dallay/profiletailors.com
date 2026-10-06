<script setup lang="ts">
import type { HTMLAttributes } from "vue"
import { useAttrs } from "vue"
import { cn } from "../../lib/utils"
import { Input } from '../input'

defineOptions({ inheritAttrs: false })

const props = defineProps<{
  id?: string
  ariaLabel?: string
  class?: HTMLAttributes["class"]
}>()

const attrs = useAttrs()
</script>

<template>
  <Input
    :id="props.id"
    :aria-label="props.ariaLabel"
    v-bind="attrs"
    data-slot="sidebar-input"
    data-sidebar="input"
    :class="cn('bg-background h-8 w-full shadow-none', props.class)"
  >
    <slot />
  </Input>
</template>
