<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { FileText, ScanText, X } from "lucide-vue-next";
import type { ConversationAttachment } from "@/types/api";

const emit = defineEmits<{
  (e: "preview", fileUrl: string): void;
  (e: "remove", fileUrl: string): void;
  (e: "show-text", fileUrl: string): void;
}>();
const { t } = useI18n();

function formatFileSize(sizeBytes: number) {
  if (sizeBytes >= 1024 * 1024) {
    return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  if (sizeBytes >= 1024) {
    return `${Math.round(sizeBytes / 1024)} KB`;
  }
  return `${sizeBytes} B`;
}

function isImageAttachment(attachment: ConversationAttachment) {
  return attachment.mimeGroup === "image" && attachment.previewable;
}

const props = defineProps<{
  attachments: ConversationAttachment[];
  textContents: Record<string, string>;
}>();
</script>

<template>
  <div v-if="attachments.length" class="composer-attachments">
    <div
      v-for="attachment in attachments"
      :key="attachment.fileUrl"
      class="composer-attachment-item"
      :title="`${attachment.name} · ${attachment.mimeGroup} · ${formatFileSize(attachment.sizeBytes)}`"
    >
      <button
        v-if="isImageAttachment(attachment)"
        class="composer-attachment-thumb-btn"
        type="button"
        :aria-label="t('chat.composer.previewImageAttachment', { name: attachment.name })"
        @click="emit('preview', attachment.fileUrl)"
      >
        <img
          :src="attachment.fileUrl"
          :alt="attachment.name"
          class="composer-attachment-preview"
        />
      </button>
      <div v-else-if="props.textContents[attachment.fileUrl] !== undefined" class="composer-text-attachment-card">
        <span class="composer-text-attachment-icon"><ScanText :size="25" /></span>
        <div class="composer-text-attachment-content">
          <span class="composer-text-attachment-title">{{ props.textContents[attachment.fileUrl].slice(0, 18) || attachment.name }}...</span>
          <button class="composer-text-attachment-show" type="button" @click="emit('show-text', attachment.fileUrl)">
            {{ t('chat.composer.showInTextField') }} <span aria-hidden="true">›</span>
          </button>
        </div>
      </div>
      <div v-else class="composer-attachment-file-pill">
        <span class="composer-attachment-icon"><FileText :size="14" /></span>
        <span class="composer-attachment-name">{{ attachment.name }}</span>
      </div>
      <button class="composer-attachment-remove" type="button" :aria-label="t('chat.composer.removeAttachment', { name: attachment.name })" @click="emit('remove', attachment.fileUrl)">
        <X :size="16" :stroke-width="2.75" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.composer-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
}

.composer-attachment-item {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.composer-attachment-preview,
.composer-attachment-icon {
  width: 46px;
  height: 46px;
  border-radius: var(--radius-sm);
  flex: none;
}

.composer-attachment-thumb-btn {
  border: var(--size-1) solid var(--color-border-soft);
  background: transparent;
  padding: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
  cursor: zoom-in;
}

.composer-attachment-preview {
  object-fit: cover;
  background: var(--color-bg-surface);
  display: block;
}

.composer-attachment-file-pill {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1_5);
  max-width: 168px;
  height: 40px;
  padding: 0 var(--space-2_5) 0 var(--space-2);
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-pill);
  background: var(--color-bg-surface-soft);
}

.composer-text-attachment-card {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2_5);
  width: min(450px, calc(100vw - 96px));
  min-height: 82px;
  padding: var(--space-2_5) var(--space-3);
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--color-bg-surface-soft) 86%, var(--color-bg-surface));
}

.composer-text-attachment-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  flex: none;
  border-radius: var(--radius-md);
  background: var(--color-bg-surface-mute);
  color: var(--color-text-subtle);
}

.composer-text-attachment-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.composer-text-attachment-title {
  overflow: hidden;
  color: var(--color-text-primary);
  font-size: var(--text-body-size);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer-text-attachment-show {
  width: fit-content;
  padding: 0;
  border: 0;
  border-bottom: var(--size-1) solid currentColor;
  background: transparent;
  color: var(--color-text-subtle);
  font: inherit;
  cursor: pointer;
}

.composer-attachment-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--color-bg-brand-soft) 70%, white);
  color: var(--color-text-brand);
}

.composer-attachment-name {
  max-width: 122px;
  font-size: var(--text-caption-size);
  font-weight: 500;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer-attachment-remove {
  position: absolute;
  top: -9px;
  right: -9px;
  width: 24px;
  height: 24px;
  border: var(--size-1) solid color-mix(in srgb, var(--color-border-strong) 78%, transparent);
  border-radius: var(--radius-pill);
  background: var(--color-bg-surface);
  color: var(--color-text-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 1;
  box-shadow: 0 2px 8px color-mix(in srgb, black 24%, transparent);
}

.composer-attachment-remove:hover {
  background: var(--color-bg-surface-soft);
}
</style>
