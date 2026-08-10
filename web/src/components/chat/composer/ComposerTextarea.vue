<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";

const props = defineProps<{
  modelValue: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "paste", event: ClipboardEvent): void;
  (e: "keydown", event: KeyboardEvent): void;
  (e: "compositionstart"): void;
  (e: "compositionend"): void;
}>();

const { t } = useI18n();
const textareaRef = ref<HTMLTextAreaElement | null>(null);

const MIN_LINES = 2;
const MAX_LINES = 5;

function getLineHeight(textarea: HTMLTextAreaElement) {
  const styles = window.getComputedStyle(textarea);
  const lineHeight = Number.parseFloat(styles.lineHeight);
  if (Number.isFinite(lineHeight)) {
    return lineHeight;
  }
  const fontSize = Number.parseFloat(styles.fontSize);
  return Number.isFinite(fontSize) ? fontSize * 1.6 : 22.4;
}

function resizeTextarea(target?: HTMLTextAreaElement) {
  const textarea = target || textareaRef.value;
  if (!textarea) {
    return;
  }
  const lineHeight = getLineHeight(textarea);
  const minHeight = lineHeight * MIN_LINES;
  const maxHeight = lineHeight * MAX_LINES;
  textarea.style.height = "auto";
  const nextHeight = Math.min(Math.max(textarea.scrollHeight, minHeight), maxHeight);
  textarea.style.height = `${nextHeight}px`;
  textarea.style.overflowY = textarea.scrollHeight > maxHeight ? "auto" : "hidden";
}

function onInput(event: Event) {
  const target = event.target as HTMLTextAreaElement;
  emit("update:modelValue", target.value);
  resizeTextarea(target);
}

onMounted(() => {
  resizeTextarea();
});

watch(
  () => props.modelValue,
  async () => {
    await nextTick();
    resizeTextarea();
  }
);
</script>

<template>
  <textarea
    id="messageInput"
    ref="textareaRef"
    :value="modelValue"
    class="composer-textarea"
    :placeholder="t('chat.composer.placeholder')"
    @input="onInput"
    @paste="emit('paste', $event)"
    @keydown="emit('keydown', $event)"
    @compositionstart="emit('compositionstart')"
    @compositionend="emit('compositionend')"
  />
</template>

<style scoped>
.composer-textarea {
  min-height: 3.2em;
  max-height: 8em;
  width: 100%;
  overflow-y: hidden;
  resize: none;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--color-text-primary);
  font: inherit;
  line-height: 1.6;
  outline: none;
  box-shadow: none;
  scrollbar-width: thin;
  scrollbar-color: var(--color-border-strong) transparent;
}

.composer-textarea::-webkit-scrollbar {
  width: var(--size-8);
}

.composer-textarea::-webkit-scrollbar-track {
  background: transparent;
}

.composer-textarea::-webkit-scrollbar-thumb {
  border-radius: var(--radius-pill);
  background: var(--color-border-strong);
}

.composer-textarea::-webkit-scrollbar-thumb:hover {
  background: var(--color-border-active);
}

.composer-textarea::placeholder {
  color: var(--color-text-faint);
}

@media (max-width: 860px) {
  .composer-textarea {
    min-height: 3.2em;
    padding: 0;
  }
}
</style>
