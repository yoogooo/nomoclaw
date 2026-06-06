<script setup lang="ts">
import { computed, useSlots, type Component } from "vue";

const props = withDefaults(defineProps<{
  size?: number | string;
  duration?: string;
  thickness?: number | string;
  color?: string;
  trackColor?: string;
  icon?: Component | null;
  variant?: "ring" | "icon";
}>(), {
  size: 16,
  duration: "1.5s",
  thickness: 2,
  color: "currentColor",
  trackColor: "color-mix(in srgb, currentColor 24%, transparent)",
  icon: null,
  variant: undefined
});

const slots = useSlots();

const resolvedVariant = computed(() => {
  if (props.variant) {
    return props.variant;
  }
  return props.icon || slots.default ? "icon" : "ring";
});

const spinnerStyle = computed<Record<string, string>>(() => ({
  "--ui-spinner-size": toCssUnit(props.size),
  "--ui-spinner-duration": props.duration,
  "--ui-spinner-thickness": toCssUnit(props.thickness),
  "--ui-spinner-color": props.color,
  "--ui-spinner-track-color": props.trackColor,
  "--ui-spinner-highlight-stop": "288deg"
}));

function toCssUnit(value: number | string) {
  return typeof value === "number" ? `${value}px` : value;
}
</script>

<template>
  <span
    class="ui-spinner"
    :class="{
      'ui-spinner--ring': resolvedVariant === 'ring',
      'ui-spinner--icon': resolvedVariant === 'icon'
    }"
    :style="spinnerStyle"
    aria-hidden="true"
  >
    <span v-if="resolvedVariant === 'ring'" class="ui-spinner__ring" />
    <component :is="icon" v-else-if="icon" class="ui-spinner__icon" />
    <span v-else class="ui-spinner__icon">
      <slot />
    </span>
  </span>
</template>

<style scoped>
.ui-spinner {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--ui-spinner-color);
  line-height: 0;
}

.ui-spinner--ring,
.ui-spinner--icon {
  width: var(--ui-spinner-size);
  height: var(--ui-spinner-size);
}

.ui-spinner--icon {
  animation: ui-spinner-spin var(--ui-spinner-duration) linear infinite;
  transform-origin: center;
}

.ui-spinner__ring {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: conic-gradient(
    var(--ui-spinner-color) 0deg var(--ui-spinner-highlight-stop),
    var(--ui-spinner-track-color) var(--ui-spinner-highlight-stop) 360deg
  );
  -webkit-mask: radial-gradient(
    farthest-side,
    transparent calc(100% - var(--ui-spinner-thickness)),
    #000 calc(100% - var(--ui-spinner-thickness))
  );
  mask: radial-gradient(
    farthest-side,
    transparent calc(100% - var(--ui-spinner-thickness)),
    #000 calc(100% - var(--ui-spinner-thickness))
  );
  animation: ui-spinner-spin var(--ui-spinner-duration) linear infinite;
}

.ui-spinner__icon {
  display: inline-flex;
  width: 100%;
  height: 100%;
}

.ui-spinner__icon :deep(svg) {
  width: 100%;
  height: 100%;
}

@keyframes ui-spinner-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
