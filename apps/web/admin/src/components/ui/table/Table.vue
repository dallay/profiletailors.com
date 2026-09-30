<script setup lang="ts">
import type { HTMLAttributes } from "vue"
import { cn } from "@/lib/utils"

defineOptions({ inheritAttrs: false })

const props = defineProps<{
  class?: HTMLAttributes["class"]
}>()
</script>

<template>
  <div data-slot="table-container" class="relative w-full overflow-x-auto">
    <table v-bind="$attrs" data-slot="table" :class="cn('w-full caption-bottom text-sm', props.class)">
      <thead v-if="$slots.head" class="[&_tr]:border-b">
        <slot name="head" />
      </thead>
      <slot />
    </table>
  </div>
</template>
