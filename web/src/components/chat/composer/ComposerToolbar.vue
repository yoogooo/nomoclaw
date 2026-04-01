<script setup lang="ts">
import { Lightbulb, Paperclip } from "lucide-vue-next";
import { NSelect } from "naive-ui";

defineProps<{
  selectedModelKey: string;
  modelOptions: any[];
  showJinnangPicker: boolean;
  uploadDisabled: boolean;
  uploadingFiles: boolean;
  switchingContext: boolean;
  isRunningCurrentConversation: boolean;
  isSubmitDisabled: boolean;
  renderLabel: (option: any) => any;
  renderOption: (params: any) => any;
}>();

const emit = defineEmits<{
  (e: "change-model", value: string): void;
  (e: "trigger-upload"): void;
  (e: "toggle-jinnang"): void;
  (e: "submit"): void;
}>();
</script>

<template>
  <div class="composer-toolbar-row composer-toolbar-row-bottom">
    <div class="composer-model-inline">
      <n-select
        :value="selectedModelKey"
        :options="modelOptions"
        placeholder="选择已配置模型"
        :disabled="switchingContext"
        :render-label="renderLabel"
        :render-option="renderOption"
        @update:value="emit('change-model', $event)"
      />
    </div>

    <div class="composer-upload-inline">
      <div class="composer-tools">
        <button
          class="composer-tool-btn icon-only"
          type="button"
          title="上传文件"
          aria-label="上传文件"
          :disabled="uploadDisabled || uploadingFiles || switchingContext"
          @click="emit('trigger-upload')"
        >
          <Paperclip :size="16" />
        </button>
        <button
          class="composer-tool-btn icon-only"
          :class="{ active: showJinnangPicker }"
          type="button"
          title="锦囊"
          aria-label="锦囊"
          @click="emit('toggle-jinnang')"
        >
          <Lightbulb :size="16" />
        </button>
      </div>
    </div>

    <div class="composer-meta">
      <button
        class="composer-submit"
        :class="{ 'composer-submit-cancel': isRunningCurrentConversation }"
        :disabled="isSubmitDisabled"
        @click="emit('submit')"
      >
        {{ isRunningCurrentConversation ? "取消" : "发送" }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.composer-toolbar-row {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.composer-toolbar-row-bottom {
  margin-top: var(--space-1);
}

.composer-model-inline {
  width: min(100%, 250px);
  flex: 0 1 250px;
}

.composer-upload-inline {
  display: flex;
  align-items: center;
  flex: none;
}

.composer-tools {
  display: flex;
  gap: var(--space-2);
}

.composer-meta {
  margin-left: auto;
  display: flex;
  align-items: center;
  flex: none;
}

.composer-tool-btn {
  padding: var(--size-7) var(--space-3);
  border: var(--size-1) solid var(--color-border-strong);
  border-radius: var(--radius-pill);
  background: var(--color-bg-surface-soft);
  color: var(--color-text-subtle);
  font-size: var(--font-size-xs);
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.16s ease, color 0.16s ease, background-color 0.16s ease;
}

.composer-tool-btn.icon-only {
  width: var(--size-34);
  height: var(--size-34);
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.composer-tool-btn:hover:not(:disabled) {
  border-color: var(--color-border-brand-soft-hover);
  background: var(--color-bg-brand-soft-hover);
  color: var(--color-text-brand);
}

.composer-tool-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.composer-tool-btn.active {
  border-color: var(--color-border-brand-hover);
  background: var(--color-bg-brand-soft);
  color: var(--color-text-brand);
}

.composer-submit {
  min-width: var(--size-84);
  padding: var(--space-3) var(--space-5_5);
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--color-accent-brand);
  color: var(--color-text-inverse);
  font-size: var(--font-size-lg);
  font-weight: 700;
  cursor: pointer;
  transition: box-shadow 0.18s ease, transform 0.18s ease, background-color 0.18s ease;
}

.composer-submit:hover {
  background: var(--color-accent-brand-hover);
  box-shadow: var(--shadow-button-brand);
}

.composer-submit:active {
  transform: translateY(var(--size-1));
}

.composer-submit-cancel {
  background: var(--color-accent-danger);
}

.composer-submit-cancel:hover {
  background: var(--color-accent-danger-hover);
  box-shadow: var(--shadow-button-danger);
}

.composer-submit:disabled {
  background: var(--color-border-strong);
  color: var(--color-disabled-text);
  box-shadow: none;
  cursor: not-allowed;
}

@media (max-width: var(--size-breakpoint-md)) {
  .composer-toolbar-row {
    align-items: stretch;
  }

  .composer-model-inline {
    width: 100%;
    flex-basis: 100%;
  }

  .composer-meta {
    margin-left: 0;
  }
}
</style>
