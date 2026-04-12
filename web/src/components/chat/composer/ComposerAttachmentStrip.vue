<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { FileText, X } from "lucide-vue-next";
import type { ConversationAttachment } from "@/types/api";

defineProps<{
  attachments: ConversationAttachment[];
}>();

const emit = defineEmits<{
  (e: "preview", fileUrl: string): void;
  (e: "remove", fileUrl: string): void;
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
      <div v-else class="composer-attachment-file-pill">
        <span class="composer-attachment-icon">
          <FileText :size="14" />
        </span>
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
  background: white;
  color: var(--color-text-primary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  opacity: 1;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.18);
}

.composer-attachment-remove:hover {
  background: color-mix(in srgb, white 90%, var(--color-bg-soft-hover));
}
</style>
