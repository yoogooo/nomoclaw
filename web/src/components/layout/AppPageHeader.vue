<script setup lang="ts">
defineProps<{
  title: string;
  kicker?: string;
  subtitle?: string;
}>();
</script>

<template>
  <header class="app-page-header">
    <div class="app-page-header-main">
      <div v-if="kicker" class="ui-kicker">{{ kicker }}</div>
      <div class="page-title">{{ title }}</div>
      <div v-if="$slots.subtitle" class="page-subtitle">
        <slot name="subtitle" />
      </div>
      <div v-else-if="subtitle" class="page-subtitle">{{ subtitle }}</div>
    </div>
    <div v-if="$slots.actions" class="app-page-header-actions">
      <slot name="actions" />
    </div>
  </header>
</template>
