<script setup lang="ts">
defineProps<{
  modelValue: string;
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "keydown", event: KeyboardEvent): void;
}>();
</script>

<template>
  <textarea
    id="messageInput"
    :value="modelValue"
    class="composer-textarea ui-focus-highlight"
    placeholder="例如：请结合我上传的文件，整理出一版可直接使用的结论和建议。"
    @input="emit('update:modelValue', ($event.target as HTMLTextAreaElement).value)"
    @keydown="emit('keydown', $event)"
  />
</template>

<style scoped>
.composer-textarea {
  min-height: var(--size-88);
  width: 100%;
  resize: vertical;
  padding: var(--space-4_5) var(--space-4_5);
  border: var(--size-1) solid var(--color-border-strong);
  border-radius: var(--radius-xl);
  background: var(--color-bg-surface-mute);
  color: var(--color-text-primary);
  font: inherit;
  outline: none;
}

.composer-textarea::placeholder {
  color: var(--color-text-faint);
}

@media (max-width: var(--size-breakpoint-md)) {
  .composer-textarea {
    min-height: var(--size-76);
    border-radius: var(--radius-lg);
    padding: var(--space-3_5) var(--space-3_5);
  }
}
</style>
