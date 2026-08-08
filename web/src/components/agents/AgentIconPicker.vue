<script setup lang="ts">
import { computed, ref } from "vue";
import { useI18n } from "vue-i18n";
import { Bot, ChevronDown } from "lucide-vue-next";
import { NIcon, NPopover } from "naive-ui";
import type { AvatarIconOption } from "@/components/agents/agentManagementTypes";

const props = defineProps<{
  modelValue: string;
  options: AvatarIconOption[];
}>();

const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
}>();
const { t } = useI18n();
const show = ref(false);

const selectedOption = computed(() => props.options.find((item) => item.key === props.modelValue));
const selectedIcon = computed(() => selectedOption.value?.icon || Bot);
</script>

<template>
  <n-popover v-model:show="show" trigger="click" placement="bottom-start" :show-arrow="false" raw>
    <template #trigger>
      <button type="button" class="agent-icon-picker-trigger" :aria-label="t('agents.basic.iconSelect')">
        <span class="agent-icon-picker-trigger-icon">
          <component :is="selectedIcon" :size="18" />
        </span>
        <span class="agent-icon-picker-trigger-label">{{ selectedOption?.label || "Bot" }}</span>
        <n-icon :size="15" class="agent-icon-picker-trigger-arrow"><ChevronDown /></n-icon>
      </button>
    </template>
    <div class="agent-icon-picker-popover">
      <div class="agent-icon-picker-title">{{ t("agents.basic.iconStyle") }}</div>
      <div class="agent-icon-picker-grid">
        <button
          v-for="item in options"
          :key="item.key"
          type="button"
          class="ui-avatar-icon-btn"
          :class="{ active: modelValue === item.key }"
          :aria-label="item.label"
          :title="item.label"
          @click="emit('update:modelValue', item.key); show = false"
        >
          <component :is="item.icon" :size="17" />
        </button>
      </div>
    </div>
  </n-popover>
</template>

<style scoped>
.agent-icon-picker-trigger {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 10rem;
  height: var(--control-height-md);
  padding: 0 var(--space-2_5);
  border: var(--size-1) solid var(--color-border-strong);
  border-radius: var(--control-radius-md);
  background: var(--color-bg-surface-soft);
  color: var(--color-text-label);
  cursor: pointer;
  transition: border-color 0.16s ease, background-color 0.16s ease;
}

.agent-icon-picker-trigger:hover,
.agent-icon-picker-trigger:focus-visible {
  border-color: var(--color-border-active);
  background: var(--color-bg-brand-soft);
  outline: none;
}

.agent-icon-picker-trigger-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-brand);
}

.agent-icon-picker-trigger-label {
  flex: 1;
  text-align: left;
  font-size: var(--text-body-size);
}

.agent-icon-picker-trigger-arrow {
  color: var(--color-text-tertiary);
}

.agent-icon-picker-popover {
  width: var(--size-280);
  padding: var(--space-3);
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-panel-primary);
}

.agent-icon-picker-title {
  margin-bottom: var(--space-2_5);
  color: var(--color-text-label);
  font-size: var(--text-caption-size);
  font-weight: 600;
}

.agent-icon-picker-grid {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: var(--space-2);
}

.agent-icon-picker-grid :deep(.ui-avatar-icon-btn) {
  width: var(--size-36);
  height: var(--size-36);
}
</style>
