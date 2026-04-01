<script setup lang="ts">
import type { JinnangTip } from "@/stores/jinnang";

const props = defineProps<{
  tips: JinnangTip[];
  selectedId: string;
}>();

const emit = defineEmits<{
  (event: "pick", value: string): void;
  (event: "close"): void;
}>();

function selectTip(id: string) {
  emit("pick", id);
}
</script>

<template>
  <div class="jinnang-picker ui-card-soft">
    <div class="jinnang-picker-title ui-title-strong">共 {{ props.tips.length }} 个锦囊</div>
    <div v-if="props.tips.length" class="jinnang-picker-list">
      <button
        v-for="tip in props.tips"
        :key="tip.id"
        type="button"
        class="jinnang-option ui-card-hoverable"
        :class="{ active: props.selectedId === tip.id }"
        @click="selectTip(tip.id)"
      >
        <span class="jinnang-radio-ui" aria-hidden="true" />
        <span class="jinnang-option-content">
          <span class="jinnang-option-title ui-title-strong">{{ tip.title }}</span>
          <span class="jinnang-option-summary">{{ tip.summary }}</span>
        </span>
      </button>
    </div>
    <div v-else class="jinnang-picker-empty">暂无可选锦囊</div>
    <div class="jinnang-picker-actions">
      <button class="jinnang-picker-action ui-pill-btn" type="button" @click="emit('close')">关闭</button>
    </div>
  </div>
</template>

<style scoped>
.jinnang-picker {
  padding: var(--space-3);
}

.jinnang-picker-title {
  font-size: var(--font-size-xs);
}

.jinnang-picker-list {
  margin-top: var(--space-2_5);
  max-height: var(--size-180);
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-1_5);
}

.jinnang-option {
  display: flex;
  gap: var(--space-2);
  align-items: flex-start;
  width: 100%;
  padding: var(--size-9) var(--space-2_5);
  border: var(--size-1) solid transparent;
  border-radius: var(--radius-m);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, background-color 0.16s ease;
}

.jinnang-option:hover {
  border-color: var(--color-border-runtime-soft);
  background: var(--color-overlay-white-62);
}

.jinnang-option.active {
  border-color: var(--color-overlay-brand-38);
  background: var(--color-bg-brand-soft);
}

.jinnang-radio-ui {
  display: inline-block;
  flex: none;
  width: var(--size-16);
  height: var(--size-16);
  margin-top: var(--size-1);
  border: var(--size-1_5) solid var(--color-text-runtime-subtle);
  border-radius: var(--radius-pill);
  background: var(--color-bg-surface);
  position: relative;
}

.jinnang-option.active .jinnang-radio-ui {
  border-color: var(--color-accent-brand);
  box-shadow: 0 0 0 var(--size-2) var(--color-overlay-brand-15);
}

.jinnang-option.active .jinnang-radio-ui::after {
  content: "";
  position: absolute;
  left: 50%;
  top: 50%;
  width: var(--size-7);
  height: var(--size-7);
  transform: translate(-50%, -50%);
  border-radius: var(--radius-pill);
  background: var(--color-accent-brand);
}

.jinnang-option-content {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--size-3);
}

.jinnang-option-title {
  font-size: var(--font-size-xs);
}

.jinnang-option-summary {
  color: var(--color-text-subtle);
  font-size: var(--font-size-xs);
  line-height: 1.45;
}

.jinnang-picker-empty {
  margin-top: var(--space-2);
  color: var(--color-text-runtime-subtle);
  font-size: var(--font-size-xs);
}

.jinnang-picker-actions {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  width: 100%;
  gap: var(--space-2);
  margin-top: var(--space-2_5);
}

.jinnang-picker-action {
  min-height: var(--size-32);
  padding: var(--size-7) var(--space-3_5);
  font-size: var(--font-size-sm);
  cursor: pointer;
}

.jinnang-picker-action.primary {
  border-color: var(--color-border-brand-hover);
  background: var(--color-bg-brand-soft);
  color: var(--color-text-brand);
}

.jinnang-picker-action:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
