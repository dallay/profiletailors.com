<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AppShell from '@layouts/AppShell.vue'

const route = useRoute()

const isAuthRoute = computed(
  () => route.name === 'login' || route.name === 'register' || route.name === 'verify-email',
)
</script>

<template>
  <RouterView v-if="isAuthRoute" />
  <AppShell v-else />
</template>
