<script setup lang="ts">
import { useI18n } from "vue-i18n";

defineProps<{
  modelValue: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "keydown", event: KeyboardEvent): void;
}>();

const { t } = useI18n();
</script>

<template>
  <textarea
    id="messageInput"
    :value="modelValue"
    class="composer-textarea"
    :placeholder="t('chat.composer.placeholder')"
    @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    @keydown="emit('keydown', $event)"
  />
</template>

<style scoped>
.composer-textarea {
  min-height: 3.2em;
  width: 100%;
  overflow-y: auto;
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

@media (max-width: var(--size-breakpoint-md)) {
  .composer-textarea {
    min-height: 3.2em;
    padding: 0;
  }
}
</style>
