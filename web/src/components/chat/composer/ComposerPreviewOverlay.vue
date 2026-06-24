<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { X } from "lucide-vue-next";

defineProps<{
  imageUrl: string;
}>();

const emit = defineEmits<{
  (e: "close"): void;
}>();

const { t } = useI18n();
</script>

<template>
  <div class="composer-preview-backdrop" @click="emit('close')">
    <div class="composer-preview-dialog" @click.stop>
      <button class="composer-preview-close" type="button" :aria-label="t('chat.composer.closePreview')" @click="emit('close')">
        <X :size="16" />
      </button>
      <img :src="imageUrl" :alt="t('chat.composer.attachmentPreview')" class="composer-preview-image" />
    </div>
  </div>
</template>

<style scoped>
.composer-preview-backdrop {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-6);
  background: var(--color-bg-overlay-mask);
}

.composer-preview-dialog {
  position: relative;
  max-width: min(88vw, var(--size-900));
  max-height: 84vh;
  border-radius: var(--radius-xl);
  overflow: hidden;
  background: var(--color-bg-overlay-panel);
  border: var(--size-1) solid var(--color-border-overlay-strong);
  box-shadow: var(--color-shadow-overlay);
}

.composer-preview-image {
  display: block;
  max-width: 100%;
  max-height: 84vh;
  object-fit: contain;
}

.composer-preview-close {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  z-index: 1;
  width: var(--size-28);
  height: var(--size-28);
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--color-bg-overlay-control);
  border: var(--size-1) solid var(--color-border-overlay);
  color: var(--color-text-inverse);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.composer-preview-close:hover {
  background: var(--color-bg-overlay-control-hover);
}
</style>
