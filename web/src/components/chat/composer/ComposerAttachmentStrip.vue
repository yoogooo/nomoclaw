<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { File, FileArchive, FileAudio, FileImage, FileText, FileType2, FileVideo, X } from "lucide-vue-next";
import type { ConversationAttachment } from "@/types/api";

const emit = defineEmits<{
  (e: "preview", fileUrl: string): void;
  (e: "preview-text", fileUrl: string): void;
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

function attachmentIcon(attachment: ConversationAttachment) {
  switch (attachment.mimeGroup) {
    case "image":
      return FileImage;
    case "audio":
      return FileAudio;
    case "video":
      return FileVideo;
    case "pdf":
      return FileType2;
    case "text":
      return FileText;
    case "application":
      return FileArchive;
    default:
      return File;
  }
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
      <div
        v-else-if="props.textContents[attachment.fileUrl] !== undefined"
        class="composer-text-attachment-card"
        role="button"
        tabindex="0"
        @click="emit('preview-text', attachment.fileUrl)"
        @keydown.enter="emit('preview-text', attachment.fileUrl)"
        @keydown.space.prevent="emit('preview-text', attachment.fileUrl)"
      >
        <span class="composer-text-attachment-icon"><FileText :size="25" /></span>
        <div class="composer-text-attachment-content">
          <span class="composer-text-attachment-title">{{ props.textContents[attachment.fileUrl].slice(0, 18) || attachment.name }}...</span>
          <button class="composer-text-attachment-show" type="button" @click.stop="emit('show-text', attachment.fileUrl)">
            {{ t('chat.composer.showInTextField') }} <span aria-hidden="true">›</span>
          </button>
        </div>
      </div>
      <div v-else class="composer-attachment-file-pill">
        <span class="composer-attachment-icon">
          <component :is="attachmentIcon(attachment)" :size="14" />
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
  gap: var(--space-1_5);
}

.composer-attachment-item {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.composer-attachment-preview,
.composer-attachment-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-sm);
  flex: none;
}

.composer-attachment-thumb-btn {
  border: var(--size-1) solid var(--color-composer-attachment-border);
  background: transparent;
  padding: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
  cursor: zoom-in;
  transition: background-color 0.16s ease, border-color 0.16s ease;
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
  height: 38px;
  padding: 0 var(--space-2) 0 var(--space-1_5);
  border: var(--size-1) solid var(--color-composer-attachment-border);
  border-radius: var(--radius-md);
  background: var(--color-composer-attachment-bg);
  transition: background-color 0.16s ease, border-color 0.16s ease;
}

.composer-text-attachment-card {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  width: min(200px, calc(100vw - 96px));
  min-height: 72px;
  padding: var(--space-3);
  border: var(--size-1) solid var(--color-composer-attachment-border);
  border-radius: var(--radius-md);
  background: var(--color-composer-attachment-bg);
  cursor: pointer;
  transition: background-color 0.16s ease, border-color 0.16s ease;
}

.composer-attachment-thumb-btn:hover,
.composer-attachment-file-pill:hover,
.composer-text-attachment-card:hover,
.composer-text-attachment-card:focus-visible {
  border-color: var(--color-composer-attachment-border-hover);
  background: var(--color-composer-attachment-bg-hover);
}

.composer-text-attachment-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex: none;
  border-radius: var(--radius-md);
  background: var(--color-composer-attachment-icon-bg);
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
  top: -7px;
  right: -7px;
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
  opacity: 0;
  pointer-events: none;
  box-shadow: var(--shadow-composer-attachment-remove);
  transition: opacity 0.16s ease, background-color 0.16s ease;
}

.composer-attachment-item:hover .composer-attachment-remove,
.composer-attachment-item:focus-within .composer-attachment-remove {
  opacity: 1;
  pointer-events: auto;
}

.composer-attachment-remove:hover {
  background: var(--color-bg-surface-soft);
}
</style>
