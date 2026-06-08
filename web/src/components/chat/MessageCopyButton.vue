<script setup lang="ts">
import { Check, Copy } from "lucide-vue-next";
import UiInstantTooltip from "@/components/UiInstantTooltip.vue";

withDefaults(defineProps<{
  copied?: boolean;
  tooltip?: string;
  ariaLabel?: string;
  tone?: "default" | "subtle";
}>(), {
  copied: false,
  tooltip: "",
  ariaLabel: "",
  tone: "default"
});

const emit = defineEmits<{
  click: [];
}>();
</script>

<template>
  <UiInstantTooltip :content="tooltip">
    <button
      class="message-copy-btn"
      :class="{
        copied,
        'message-copy-btn--subtle': tone === 'subtle'
      }"
      type="button"
      :aria-label="ariaLabel"
      @click="emit('click')"
    >
      <Check v-if="copied" :size="14" />
      <Copy v-else :size="14" />
    </button>
  </UiInstantTooltip>
</template>

<style scoped>
.message-copy-btn {
  width: var(--space-6);
  height: var(--space-6);
  padding: 0;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-text-subtle);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.16s ease, color 0.16s ease, opacity 0.16s ease;
}

.message-copy-btn:hover {
  background: var(--color-bg-soft-hover);
  color: var(--color-text-brand-strong);
}

.message-copy-btn:disabled {
  cursor: not-allowed;
  opacity: 0.74;
}

.message-copy-btn.copied {
  color: var(--color-text-brand-strong);
  animation: copied-flash 0.25s ease-out;
}

.message-copy-btn--subtle {
  color: var(--color-text-subtle);
}

.message-copy-btn--subtle:hover {
  background: var(--color-bg-soft-hover);
  color: var(--color-text-primary);
}

@keyframes copied-flash {
  0% {
    transform: scale(0.94);
  }

  100% {
    transform: scale(1);
  }
}
</style>
