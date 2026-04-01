<script setup lang="ts">
import { computed, h, onBeforeUnmount, onMounted, ref } from "vue";
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
const showJinnangPicker = ref(false);
const appliedJinnangId = ref("");
const fileInputRef = ref<HTMLInputElement | null>(null);
const isDragActive = ref(false);
const previewImageUrl = ref("");

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
    return conversationStore.uploadDisabledReason || "当前模型不支持上传文件";
  }
  const sameTypeHint = policy.singleMimeGroupOnly ? "同一条消息只能上传同一类型文件。" : "支持同一条消息上传多种类型文件。";
  return `图片最多 ${policy.maxImagesPerMessage} 张，非图片文件最多 ${policy.maxFilesPerMessage} 个。${sameTypeHint}`;
});

function onKeydown(event: KeyboardEvent) {
  if (event.metaKey && event.key === "Enter") {
    event.preventDefault();
    void conversationStore.sendMessage();
  }
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
    discreteMessage.info("正在切换会话，请稍候再上传");
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
    discreteMessage.info("正在切换会话，请稍候再上传");
    return;
  }
  if (conversationStore.uploadDisabledReason) {
    discreteMessage.info(conversationStore.uploadDisabledReason);
    return;
  }
  const files = Array.from(event.dataTransfer?.files || []);
  if (files.length) {
    await conversationStore.uploadFiles(files);
  }
}

async function onFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  const files = target.files;
  if (files?.length) {
    await conversationStore.uploadFiles(files);
  }
  target.value = "";
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

function renderModelLabel(option: any) {
  if (Array.isArray(option.children)) {
    return h("span", { style: "font-weight: 800;" }, option.label || "");
  }
  return option.label || "";
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

    <ComposerTextarea
      :model-value="conversationStore.draftMessage"
      @update:model-value="conversationStore.draftMessage = $event"
      @keydown="onKeydown"
    />

    <ComposerAttachmentStrip
      :attachments="conversationStore.draftAttachments"
      @preview="openImagePreview"
      @remove="conversationStore.removeDraftAttachment($event)"
    />

    <ComposerPreviewOverlay
      v-if="previewImageUrl"
      :image-url="previewImageUrl"
      @close="closeImagePreview"
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
      <button class="applied-jinnang-remove" type="button" @click="removeAppliedJinnang()" aria-label="删除锦囊">
        <X :size="12" />
      </button>
    </div>

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

    <div class="composer-upload-status">
      <div class="composer-upload-hint">{{ conversationStore.uploadingFiles ? "文件上传中..." : uploadHint }}</div>
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
  gap: var(--space-3);
  padding: var(--space-4_5) var(--space-6) var(--space-5_5);
  border-top: var(--size-1) solid var(--color-border-panel);
}

.composer-wrap-dragging {
  border-top-color: var(--color-border-brand-hover);
  background: color-mix(in srgb, var(--color-bg-brand-soft) 36%, var(--color-bg-surface));
}

.applied-jinnang {
  display: inline-flex;
  align-items: center;
  gap: var(--size-8);
  align-self: flex-start;
  width: fit-content;
  max-width: 100%;
  border: var(--size-1) solid var(--color-border-brand-soft);
  border-radius: var(--radius-pill);
  background: var(--color-bg-brand-soft);
  padding: var(--space-1_5) var(--space-2_5);
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
  font-size: var(--font-size-xs);
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
}

.composer-upload-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  line-height: 1.5;
}

.composer-file-input {
  display: none;
}

@media (max-width: var(--size-breakpoint-md)) {
  .composer-spacer {
    display: block;
    height: calc(var(--size-210) + env(safe-area-inset-bottom));
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
    border-top: var(--size-1) solid var(--color-border-strong);
    background: var(--color-bg-overlay-strong);
    backdrop-filter: blur(var(--size-8));
  }
}
</style>
