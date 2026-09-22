<script setup lang="ts">
import { useTemplateRef } from 'vue'
import { useWaitlistForm } from './useWaitlistForm'

const root = useTemplateRef<HTMLElement>('root')
useWaitlistForm(root)
</script>

<template>
  <div ref="root" class="contents">
    <slot />
  </div>
</template>
