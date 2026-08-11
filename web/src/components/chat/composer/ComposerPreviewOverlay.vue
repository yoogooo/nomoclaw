<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { X } from "lucide-vue-next";

defineProps<{
  imageUrl?: string;
  text?: string;
}>();

const emit = defineEmits<{
  (e: "close"): void;
}>();

const { t } = useI18n();
</script>

<template>
  <div class="composer-preview-backdrop" @click="emit('close')">
    <div class="composer-preview-dialog" @click.stop>
      <header class="composer-preview-header">
        <h2 class="composer-preview-title">{{ t('chat.composer.attachmentPreview') }}</h2>
        <button class="composer-preview-close" type="button" :aria-label="t('chat.composer.closePreview')" @click="emit('close')">
          <X :size="22" />
        </button>
      </header>
      <div class="composer-preview-content">
        <div class="composer-preview-surface">
          <img v-if="imageUrl" :src="imageUrl" :alt="t('chat.composer.attachmentPreview')" class="composer-preview-image" />
          <pre v-else class="composer-preview-text">{{ text }}</pre>
        </div>
      </div>
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
  display: flex;
  flex-direction: column;
  width: min(90vw, var(--size-900));
  max-height: 88vh;
  border-radius: var(--radius-xl);
  overflow: hidden;
  background: var(--color-bg-overlay-panel);
  border: var(--size-1) solid var(--color-border-overlay-strong);
  box-shadow: var(--color-shadow-overlay);
}

.composer-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex: none;
  padding: var(--space-4) var(--space-6) var(--space-3_5);
  background: var(--color-bg-overlay-panel);
}

.composer-preview-title {
  margin: 0;
  color: var(--color-text-heading);
  font-size: var(--text-title-md-size);
  line-height: 1.3;
}

.composer-preview-content {
  min-height: 0;
  padding: 0 var(--space-6) var(--space-6);
  background: var(--color-bg-overlay-panel);
}

.composer-preview-surface {
  max-height: 72vh;
  overflow: auto;
  border: var(--size-1) solid var(--color-border-overlay);
  border-radius: var(--radius-xl);
  background: var(--color-bg-surface-soft);
}

.composer-preview-image {
  display: block;
  max-width: 100%;
  max-height: 72vh;
  margin: 0 auto;
  object-fit: contain;
}

.composer-preview-text {
  min-height: 240px;
  margin: 0;
  padding: var(--space-6);
  color: var(--color-text-primary);
  font: inherit;
  line-height: 1;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.composer-preview-close {
  width: var(--size-36);
  height: var(--size-36);
  flex: none;
  padding: 0;
  border-radius: var(--radius-pill);
  background: transparent;
  border: 0;
  color: var(--color-composer-preview-close-text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.composer-preview-close:hover {
  background: var(--color-composer-preview-close-bg-hover);
}
</style>
