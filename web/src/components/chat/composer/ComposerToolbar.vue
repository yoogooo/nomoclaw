<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { ArrowUp, ChevronDown, Lightbulb, Paperclip, ShieldAlert, Square } from "lucide-vue-next";
import { NDropdown, NSelect } from "naive-ui";
import UiInstantTooltip from "@/components/UiInstantTooltip.vue";

const props = defineProps<{
  selectedModelKey: string;
  modelOptions: any[];
  showJinnangPicker: boolean;
  approvalMode: "default" | "full_access";
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
  (e: "change-approval-mode", value: "default" | "full_access"): void;
  (e: "submit"): void;
}>();

const { t } = useI18n();

const selectedModelLabel = computed(() => {
  const selected = props.modelOptions.find((option) => option?.value === props.selectedModelKey);
  const label = selected?.label;
  return typeof label === "string" && label.trim().length > 0 ? label.trim() : t("chat.composer.modelPlaceholder");
});

const modelSelectWidthCh = computed(() => {
  const labelLength = selectedModelLabel.value.length;
  return Math.min(Math.max(labelLength + 6, 14), 34);
});

const approvalModeOptions = computed(() => [
  { label: t("chat.composer.permissionMode.default"), key: "default" },
  { label: t("chat.composer.permissionMode.fullAccess"), key: "full_access" }
]);

const approvalModeLabel = computed(() =>
  props.approvalMode === "full_access"
    ? t("chat.composer.permissionMode.fullAccess")
    : t("chat.composer.permissionMode.default")
);

function onApprovalModeSelect(key: string) {
  if (key !== "default" && key !== "full_access") {
    return;
  }
  emit("change-approval-mode", key);
}
</script>

<template>
  <div class="composer-toolbar-row composer-toolbar-row-bottom">
    <div class="composer-model-inline" :style="{ '--model-select-width-ch': `${modelSelectWidthCh}ch` }">
      <n-select
        :value="selectedModelKey"
        :options="modelOptions"
        size="small"
        :placeholder="t('chat.composer.modelPlaceholder')"
        :disabled="switchingContext"
        :render-label="renderLabel"
        :render-option="renderOption"
        @update:value="emit('change-model', $event)"
      />
    </div>

    <n-dropdown trigger="click" :options="approvalModeOptions" @select="onApprovalModeSelect">
      <button
        class="composer-approval-mode-btn"
        :class="{ 'composer-approval-mode-btn-danger': approvalMode === 'full_access' }"
        type="button"
        :aria-label="t('chat.composer.permissionMode.label')"
      >
        <ShieldAlert :size="14" />
        <span>{{ approvalModeLabel }}</span>
        <ChevronDown :size="14" />
      </button>
    </n-dropdown>

    <div class="composer-upload-inline">
      <div class="composer-tools">
        <UiInstantTooltip :content="t('chat.composer.upload')">
          <button
            class="composer-tool-btn icon-only"
            type="button"
            :aria-label="t('chat.composer.upload')"
            :disabled="uploadDisabled || uploadingFiles || switchingContext"
            @click="emit('trigger-upload')"
          >
            <Paperclip :size="16" />
          </button>
        </UiInstantTooltip>
        <UiInstantTooltip :content="t('chat.composer.jinnang')">
          <button
            class="composer-tool-btn icon-only"
            :class="{ active: showJinnangPicker }"
            type="button"
            :aria-label="t('chat.composer.jinnang')"
            @click="emit('toggle-jinnang')"
          >
            <Lightbulb :size="16" />
          </button>
        </UiInstantTooltip>
      </div>
    </div>

    <div class="composer-meta">
      <UiInstantTooltip :content="isRunningCurrentConversation ? t('chat.composer.stop') : t('chat.composer.send')">
        <button
          class="composer-submit"
          :class="{ 'composer-submit-cancel': isRunningCurrentConversation }"
          :disabled="isSubmitDisabled"
          :aria-label="isRunningCurrentConversation ? t('chat.composer.stop') : t('chat.composer.send')"
          @click="emit('submit')"
        >
          <Square v-if="isRunningCurrentConversation" :size="14" class="composer-stop-icon" />
          <ArrowUp v-else :size="20" />
        </button>
      </UiInstantTooltip>
    </div>
  </div>
</template>

<style scoped>
.composer-toolbar-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.composer-toolbar-row-bottom {
  margin-top: 0;
}

.composer-model-inline {
  width: clamp(11rem, var(--model-select-width-ch), 24rem);
  flex: 0 1 auto;
}

.composer-upload-inline {
  display: flex;
  align-items: center;
  flex: none;
}

.composer-approval-mode-btn {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: var(--space-1_5);
  border: var(--size-1) solid var(--color-border-strong);
  border-radius: var(--control-radius-md);
  background: transparent;
  color: var(--color-text-secondary);
  padding: var(--space-1) var(--space-2_5);
  font-size: var(--text-caption-size);
  font-weight: 700;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease;
}

.composer-approval-mode-btn:hover {
  background: transparent;
  border-color: var(--color-border-active);
}

.composer-approval-mode-btn-danger {
  color: color-mix(in srgb, var(--warning) 82%, #8a2e1a);
  border-color: color-mix(in srgb, var(--warning) 52%, var(--color-border-strong));
  background: transparent;
}

.composer-approval-mode-btn-danger:hover {
  border-color: color-mix(in srgb, var(--warning) 66%, var(--color-border-strong));
  background: transparent;
}

:root[data-theme="dark"] .composer-approval-mode-btn-danger {
  color: color-mix(in srgb, var(--warning) 88%, #fff);
  border-color: color-mix(in srgb, var(--warning) 62%, rgba(255, 255, 255, 0.22));
  background: transparent;
}

:root[data-theme="dark"] .composer-approval-mode-btn-danger:hover {
  color: color-mix(in srgb, var(--warning) 92%, #fff);
  border-color: color-mix(in srgb, var(--warning) 74%, rgba(255, 255, 255, 0.24));
  background: transparent;
}

.composer-tools {
  display: flex;
  gap: var(--space-2);
}

.composer-meta {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: var(--space-2_5);
  flex: none;
}

.composer-tool-btn {
  padding: var(--space-1_5) var(--space-3);
  border: 0;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-text-subtle);
  font-size: var(--text-caption-size);
  font-weight: 600;
  cursor: pointer;
  transition: color 0.16s ease, background-color 0.16s ease;
}

.composer-tool-btn.icon-only {
  width: var(--size-32);
  height: var(--size-32);
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-pill);
}

.composer-tool-btn:hover:not(:disabled) {
  background: var(--color-bg-surface-soft);
  color: var(--color-text-primary);
}

.composer-tool-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.composer-tool-btn.active {
  background: var(--color-bg-brand-soft);
  color: var(--color-text-brand);
}

.composer-submit {
  width: var(--size-32);
  height: var(--size-32);
  padding: 0;
  border: 0;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--color-accent-brand) 88%, var(--color-bg-surface) 12%);
  color: var(--color-button-primary-text);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: box-shadow 0.18s ease, transform 0.18s ease, background-color 0.18s ease, opacity 0.18s ease;
}

.composer-submit:hover {
  background: color-mix(in srgb, var(--color-accent-brand) 96%, var(--color-bg-surface) 4%);
}

.composer-submit:active {
  transform: translateY(var(--size-1));
}

.composer-submit-cancel {
  background: var(--color-composer-stop-bg);
  color: var(--color-composer-stop-fg);
}

.composer-submit-cancel:hover {
  background: var(--color-composer-stop-bg-hover);
}

.composer-submit:disabled {
  background: var(--color-border-strong);
  color: var(--color-disabled-text);
  box-shadow: none;
  opacity: 0.85;
  cursor: not-allowed;
}

.composer-stop-icon {
  fill: currentColor;
}

.composer-model-inline :deep(.n-base-selection) {
  background: transparent;
  border: 0;
  box-shadow: none;
}

.composer-model-inline :deep(.n-base-selection-overlay),
.composer-model-inline :deep(.n-base-selection-label),
.composer-model-inline :deep(.n-base-selection-input),
.composer-model-inline :deep(.n-base-selection-placeholder) {
  background: transparent;
}

.composer-model-inline :deep(.n-base-selection-label) {
  color: var(--color-text-secondary);
}

@media (max-width: 860px) {
  .composer-toolbar-row {
    align-items: stretch;
  }

  .composer-model-inline {
    width: 100%;
    flex-basis: 100%;
  }

  .composer-meta {
    margin-left: 0;
    justify-content: flex-end;
    align-items: flex-end;
  }
}
</style>
