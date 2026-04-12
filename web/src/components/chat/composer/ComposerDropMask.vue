<script setup lang="ts">
import { useI18n } from "vue-i18n";

const { t } = useI18n();
</script>

<template>
  <div class="composer-drop-mask">
    <div class="composer-drop-title">{{ t("chat.composer.dropToUpload") }}</div>
    <div class="composer-drop-subtitle"><slot /></div>
  </div>
</template>

<style scoped>
.composer-drop-mask {
  position: absolute;
  inset: var(--space-2);
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  border: var(--size-2) dashed var(--color-border-brand-hover);
  border-radius: var(--radius-2xl);
  background: color-mix(in srgb, var(--color-bg-surface) 82%, white);
  pointer-events: none;
}

.composer-drop-title {
  font-size: var(--text-title-sm-size);
  font-weight: 800;
  color: var(--color-text-brand);
}

.composer-drop-subtitle {
  max-width: var(--size-420);
  text-align: center;
  font-size: var(--text-body-size);
  color: var(--color-text-secondary);
  line-height: 1.6;
}
</style>
