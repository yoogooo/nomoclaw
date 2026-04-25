<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from "vue";
import { useI18n } from "vue-i18n";
import { Sparkles, X } from "lucide-vue-next";
import { message as discreteMessage } from "@/discrete";
import { useConversationStore } from "@/stores/conversation";
import { useJinnangStore } from "@/stores/jinnang";
import JinnangPicker from "../JinnangPicker.vue";
import ComposerAttachmentStrip from "./ComposerAttachmentStrip.vue";
import ComposerDropMask from "./ComposerDropMask.vue";
import ComposerPreviewOverlay from "./ComposerPreviewOverlay.vue";
import ComposerTextarea from "./ComposerTextarea.vue";
import ComposerToolbar from "./ComposerToolbar.vue";

const conversationStore = useConversationStore();
const jinnangStore = useJinnangStore();
const { t } = useI18n();
const showJinnangPicker = ref(false);
const appliedJinnangId = ref("");
const fileInputRef = ref<HTMLInputElement | null>(null);
const isDragActive = ref(false);
const previewImageUrl = ref("");
const isImeComposing = ref(false);

const isRunningCurrentConversation = computed(() =>
  conversationStore.runningConversationUid === conversationStore.currentConversationUid
  && Boolean(conversationStore.currentConversationUid)
);
const isMessageEmpty = computed(() => conversationStore.draftMessage.trim().length === 0);
const isSubmitDisabled = computed(() => !isRunningCurrentConversation.value && isMessageEmpty.value);
const appliedJinnang = computed(() => jinnangStore.tips.find((item) => item.id === appliedJinnangId.value) || null);
const uploadHint = computed(() => {
  const policy = conversationStore.currentUploadPolicy;
  if (!policy.enabled) {
    return conversationStore.uploadDisabledReason || t("chat.composer.uploadDisabled");
  }
  const sameTypeHint = policy.singleMimeGroupOnly ? t("chat.composer.sameTypeOnlyHint") : t("chat.composer.mixedTypeHint");
  return t("chat.composer.uploadLimitHint", {
    maxImages: policy.maxImagesPerMessage,
    maxFiles: policy.maxFilesPerMessage,
    sameTypeHint
  });
});

function onKeydown(event: KeyboardEvent) {
  const isCompositionEvent = event.isComposing || isImeComposing.value || event.keyCode === 229;
  if (event.key === "Enter" && !event.shiftKey && !event.ctrlKey && !event.metaKey && !event.altKey && !isCompositionEvent) {
    event.preventDefault();
    if (isRunningCurrentConversation.value) {
      return;
    }
    void conversationStore.sendMessage();
  }
}

function onCompositionStart() {
  isImeComposing.value = true;
}

function onCompositionEnd() {
  isImeComposing.value = false;
}

function toggleJinnangPicker() {
  showJinnangPicker.value = !showJinnangPicker.value;
}

function applyJinnangById(tipId: string) {
  const tip = jinnangStore.tips.find((item) => item.id === tipId);
  if (!tip) return;
  appliedJinnangId.value = tip.id;
  showJinnangPicker.value = false;
}

function removeAppliedJinnang() {
  appliedJinnangId.value = "";
}

function triggerFilePicker() {
  if (conversationStore.loading) {
    discreteMessage.info(t("chat.composer.switchingConversation"));
    return;
  }
  if (!conversationStore.hasAnyConfiguredModel) {
    conversationStore.guideToModelSetup();
    return;
  }
  if (conversationStore.uploadDisabledReason) {
    discreteMessage.info(conversationStore.uploadDisabledReason);
    return;
  }
  fileInputRef.value?.click();
}

function containsFiles(event: DragEvent) {
  return Array.from(event.dataTransfer?.types || []).includes("Files");
}

function onDragEnter(event: DragEvent) {
  if (!containsFiles(event)) {
    return;
  }
  event.preventDefault();
  isDragActive.value = true;
}

function onDragOver(event: DragEvent) {
  if (!containsFiles(event)) {
    return;
  }
  event.preventDefault();
  event.dataTransfer!.dropEffect = "copy";
  isDragActive.value = true;
}

function onDragLeave(event: DragEvent) {
  const nextTarget = event.relatedTarget as Node | null;
  if (nextTarget && (event.currentTarget as HTMLElement | null)?.contains(nextTarget)) {
    return;
  }
  isDragActive.value = false;
}

async function onDrop(event: DragEvent) {
  if (!containsFiles(event)) {
    return;
  }
  event.preventDefault();
  isDragActive.value = false;
  if (conversationStore.loading) {
    discreteMessage.info(t("chat.composer.switchingConversation"));
    return;
  }
  if (!conversationStore.hasAnyConfiguredModel) {
    conversationStore.guideToModelSetup();
    return;
  }
  if (conversationStore.uploadDisabledReason) {
    discreteMessage.info(conversationStore.uploadDisabledReason);
    return;
  }
  const files = Array.from(event.dataTransfer?.files || []);
  if (files.length) {
    try {
      await conversationStore.uploadFiles(files);
    } catch {
      // Errors are surfaced centrally by requestJson/toast; keep handler stable.
    }
  }
}

async function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  const files = target.files;
  try {
    if (files?.length) {
      await conversationStore.uploadFiles(files);
    }
  } catch {
    // Errors are surfaced centrally by requestJson/toast; keep handler stable.
  } finally {
    // Always reset so selecting the same file again will trigger change.
    target.value = "";
  }
}

function onModelChange(value: string) {
  if (!value) return;
  void conversationStore.changeRuntimeModel(value);
}

function openImagePreview(fileUrl: string) {
  previewImageUrl.value = fileUrl;
}

function closeImagePreview() {
  previewImageUrl.value = "";
}

function onPreviewKeydown(event: KeyboardEvent) {
  if (event.key === "Escape" && previewImageUrl.value) {
    closeImagePreview();
  }
}

function displayModelName(label: string) {
  const raw = (label || "").trim();
  if (!raw) {
    return raw;
  }
  const markerIndex = raw.lastIndexOf("::");
  return markerIndex >= 0 ? raw.slice(markerIndex + 2) : raw;
}

function renderModelLabel(option: any) {
  if (Array.isArray(option.children)) {
    return h("span", { style: "font-weight: 800;" }, option.label || "");
  }
  return displayModelName(option.label || "");
}

function renderModelOption(params: any) {
  if (Array.isArray(params?.option?.children)) {
    return params?.node;
  }
  return h("div", { style: "padding-left:14px;" }, [params?.node]);
}

onMounted(() => {
  window.addEventListener("keydown", onPreviewKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener("keydown", onPreviewKeydown);
});
</script>

<template>
  <div class="composer-spacer" aria-hidden="true" />
  <div
    class="composer-wrap"
    :class="{ 'composer-wrap-dragging': isDragActive }"
    @dragenter="onDragEnter"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
  >
    <ComposerDropMask v-if="isDragActive">{{ uploadHint }}</ComposerDropMask>

    <div class="composer-input-shell">
      <ComposerTextarea
        :model-value="conversationStore.draftMessage"
        @update:model-value="conversationStore.draftMessage = $event"
        @keydown="onKeydown"
        @compositionstart="onCompositionStart"
        @compositionend="onCompositionEnd"
      />

      <ComposerAttachmentStrip
        :attachments="conversationStore.draftAttachments"
        @preview="openImagePreview"
        @remove="conversationStore.removeDraftAttachment($event)"
      />

      <JinnangPicker
        v-if="showJinnangPicker"
        :tips="jinnangStore.tips"
        :selected-id="appliedJinnangId"
        @pick="applyJinnangById($event)"
        @close="showJinnangPicker = false"
      />

      <div v-if="appliedJinnang" class="applied-jinnang">
        <Sparkles :size="14" class="applied-jinnang-icon" />
        <span class="applied-jinnang-title">{{ appliedJinnang.title }}</span>
        <button class="applied-jinnang-remove" type="button" @click="removeAppliedJinnang()" :aria-label="t('chat.composer.removeTip')">
          <X :size="12" />
        </button>
      </div>

      <div class="composer-toolbar-shell">
        <ComposerToolbar
          :selected-model-key="conversationStore.selectedModelKey"
          :model-options="conversationStore.availableModelOptions"
          :show-jinnang-picker="showJinnangPicker"
          :upload-disabled="Boolean(conversationStore.uploadDisabledReason)"
          :uploading-files="conversationStore.uploadingFiles"
          :switching-context="conversationStore.loading"
          :is-running-current-conversation="isRunningCurrentConversation"
          :is-submit-disabled="isSubmitDisabled"
          :render-label="renderModelLabel"
          :render-option="renderModelOption"
          @change-model="onModelChange"
          @trigger-upload="triggerFilePicker"
          @toggle-jinnang="toggleJinnangPicker"
          @submit="conversationStore.sendMessage()"
        />
      </div>
    </div>

    <ComposerPreviewOverlay
      v-if="previewImageUrl"
      :image-url="previewImageUrl"
      @close="closeImagePreview"
    />

    <div class="composer-upload-status">
      <div class="composer-upload-hint">{{ conversationStore.uploadingFiles ? t("chat.composer.uploadHintUploading") : uploadHint }}</div>
      <div class="composer-send-hint">{{ t("chat.composer.sendHint") }}</div>
      <input ref="fileInputRef" class="composer-file-input" type="file" multiple @change="onFileChange" />
    </div>
  </div>
</template>

<style scoped>
.composer-spacer {
  display: none;
}

.composer-wrap {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--space-2_5);
  padding: var(--space-4_5) var(--space-6) var(--space-5_5);
  border-top: 0;
}

.composer-wrap-dragging {
  border-top-color: var(--color-border-brand-hover);
  background: color-mix(in srgb, var(--color-bg-brand-soft) 36%, var(--color-bg-surface));
}

.applied-jinnang {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  align-self: flex-start;
  width: fit-content;
  max-width: 100%;
  border: var(--size-1) solid var(--color-border-brand-soft);
  border-radius: var(--radius-pill);
  background: var(--color-bg-brand-soft);
  padding: var(--space-1_5) var(--space-2_5);
}

.composer-input-shell {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  padding: var(--space-3);
  border: var(--size-1) solid var(--color-border-strong);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--color-bg-surface-mute) 84%, var(--color-bg-surface));
  box-shadow: inset 0 1px 0 color-mix(in srgb, white 6%, transparent);
}

.composer-toolbar-shell {
  margin-top: 0;
  padding-top: var(--space-1_5);
  border-top: 0;
}

.applied-jinnang-remove {
  border: 0;
  background: transparent;
  color: var(--color-text-subtle);
  width: var(--size-18);
  height: var(--size-18);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border-radius: var(--radius-pill);
  cursor: pointer;
}

.applied-jinnang-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--text-caption-size);
  font-weight: 700;
  color: var(--color-text-brand);
}

.applied-jinnang-icon {
  color: var(--color-text-brand);
}

.applied-jinnang-remove:hover {
  background: var(--color-bg-gray-tint-20);
}

.composer-upload-status {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.composer-upload-hint {
  flex: 1 1 auto;
  min-width: 0;
  font-size: var(--text-caption-size);
  color: var(--color-text-muted);
  line-height: 1.5;
}

.composer-send-hint {
  flex: none;
  font-size: var(--text-caption-size);
  color: var(--color-text-faint);
  line-height: 1.5;
  text-align: right;
  white-space: nowrap;
}

.composer-file-input {
  display: none;
}

@media (max-width: var(--size-breakpoint-md)) {
  .composer-spacer {
    display: block;
    height: calc(var(--size-190) + var(--size-20) + env(safe-area-inset-bottom));
    flex: none;
  }

  .composer-wrap {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 70;
    padding: var(--space-3_5) var(--space-4) calc(var(--space-4) + env(safe-area-inset-bottom));
    gap: var(--space-2_5);
    border-top: 0;
    background: var(--color-bg-overlay-strong);
    backdrop-filter: blur(var(--size-8));
  }

  .composer-input-shell {
    padding: var(--space-3);
    border-radius: var(--radius-lg);
  }
}
</style>
