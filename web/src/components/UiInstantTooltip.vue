<script setup lang="ts">
import { NTooltip } from "naive-ui";

withDefaults(defineProps<{
  content: string;
  placement?: "top" | "right" | "bottom" | "left";
  showDelay?: number;
  hideDelay?: number;
  disabled?: boolean;
}>(), {
  placement: "top",
  showDelay: 0,
  hideDelay: 100,
  disabled: false
});
</script>

<template>
  <n-tooltip
    v-if="!disabled && content"
    trigger="hover"
    :placement="placement"
    to="body"
    keep-alive-on-hover
    :show-delay="showDelay"
    :hide-delay="hideDelay"
  >
    <template #trigger>
      <span class="ui-instant-tooltip-target">
        <slot />
      </span>
    </template>
    {{ content }}
  </n-tooltip>
  <span v-else class="ui-instant-tooltip-target">
    <slot />
  </span>
</template>

<style scoped>
.ui-instant-tooltip-target {
  display: inline-flex;
}
</style>
