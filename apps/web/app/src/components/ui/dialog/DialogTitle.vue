<script setup lang="ts">
import type { DialogTitleProps } from "reka-ui"
import type { HTMLAttributes } from "vue"
import { reactiveOmit } from "@vueuse/core"
import { DialogTitle as RekaDialogTitle, useForwardProps } from "reka-ui"
import { cn } from "@/lib/utils"

const props = defineProps<DialogTitleProps & { class?: HTMLAttributes["class"] }>()

const delegatedProps = reactiveOmit(props, "class")

const forwardedProps = useForwardProps(delegatedProps)
</script>

<template>
  <RekaDialogTitle
    data-slot="dialog-title"
    v-bind="forwardedProps"
    :class="cn('text-base leading-none font-medium cn-font-heading', props.class)"
  >
    <slot />
  </RekaDialogTitle>
</template>
