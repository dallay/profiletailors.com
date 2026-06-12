<script setup lang="ts">
import { useEventListener, useMediaQuery } from '@vueuse/core'
import { computed, provide, ref } from 'vue'
import { cn } from '@/lib/utils'
import {
  SIDEBAR_COOKIE_MAX_AGE,
  SIDEBAR_COOKIE_NAME,
  SIDEBAR_KEYBOARD_SHORTCUT,
  SIDEBAR_WIDTH,
  SIDEBAR_WIDTH_ICON,
  SIDEBAR_WIDTH_MOBILE,
  sidebarContextKey,
} from './utils'

const props = withDefaults(defineProps<{
  defaultOpen?: boolean
  class?: string
}>(), {
  defaultOpen: true,
})

const isMobile = useMediaQuery('(max-width: 767px)')
const open = ref(props.defaultOpen)
const openMobile = ref(false)

const state = computed(() => (open.value ? 'expanded' : 'collapsed'))

function setOpen(value: boolean) {
  open.value = value
  // biome-ignore lint/suspicious/noDocumentCookie: shadcn-vue uses cookie persistence for sidebar state across page loads
  document.cookie = `${SIDEBAR_COOKIE_NAME}=${value}; path=/; max-age=${SIDEBAR_COOKIE_MAX_AGE}`
}

function setOpenMobile(value: boolean) {
  openMobile.value = value
}

function toggleSidebar() {
  if (isMobile.value) {
    setOpenMobile(!openMobile.value)
    return
  }

  setOpen(!open.value)
}

provide(sidebarContextKey, {
  state,
  open,
  openMobile,
  isMobile,
  setOpen,
  setOpenMobile,
  toggleSidebar,
})

useEventListener('keydown', (event: KeyboardEvent) => {
  const target = event.target as HTMLElement
  if (
    target?.tagName === 'INPUT' ||
    target?.tagName === 'TEXTAREA' ||
    target?.isContentEditable ||
    target?.closest('[contenteditable="true"]')
  ) {
    return
  }

  if (event.key.toLowerCase() === SIDEBAR_KEYBOARD_SHORTCUT && (event.metaKey || event.ctrlKey)) {
    event.preventDefault()
    toggleSidebar()
  }
})
</script>

<template>
  <div
    data-slot="sidebar-wrapper"
    :style="{
      '--sidebar-width': SIDEBAR_WIDTH,
      '--sidebar-width-icon': SIDEBAR_WIDTH_ICON,
      '--sidebar-width-mobile': SIDEBAR_WIDTH_MOBILE,
    }"
    :class="cn('group/sidebar-wrapper flex min-h-screen w-full', props.class)"
  >
    <slot />
  </div>
</template>
