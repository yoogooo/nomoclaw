<script setup lang="ts">
import { ref } from "vue";
import { X } from "lucide-vue-next";
import { NModal } from "naive-ui";
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

const previewTip = ref<JinnangTip | null>(null);

function openPreview(tip: JinnangTip) {
  previewTip.value = tip;
}

function closePreview() {
  previewTip.value = null;
}
</script>

<template>
  <div class="jinnang-picker ui-card-soft">
    <button class="jinnang-picker-close" type="button" aria-label="关闭锦囊面板" @click="emit('close')">
      <X :size="14" />
    </button>
    <div class="jinnang-picker-title ui-title-strong">共 {{ props.tips.length }} 个锦囊</div>
    <div v-if="props.tips.length" class="jinnang-picker-list">
      <div
        v-for="tip in props.tips"
        :key="tip.id"
        class="jinnang-option ui-card-hoverable"
        :class="{ active: props.selectedId === tip.id }"
      >
        <button type="button" class="jinnang-option-main" @click="selectTip(tip.id)">
          <span class="jinnang-radio-ui" aria-hidden="true" />
          <span class="jinnang-option-content">
            <span class="jinnang-option-title ui-title-strong">{{ tip.title }}</span>
            <span class="jinnang-option-summary">{{ tip.summary }}</span>
          </span>
        </button>
        <button class="jinnang-view-btn" type="button" @click.stop="openPreview(tip)">
          查看
        </button>
      </div>
    </div>
    <div v-else class="jinnang-picker-empty">暂无可选锦囊</div>

    <n-modal :show="Boolean(previewTip)" class="jinnang-preview-modal" @mask-click="closePreview">
      <div v-if="previewTip" class="jinnang-preview-panel">
        <div class="jinnang-preview-header">
          <div class="jinnang-preview-title">{{ previewTip.title }}</div>
          <button class="jinnang-preview-close" type="button" aria-label="关闭查看弹窗" @click="closePreview">
            <X :size="14" />
          </button>
        </div>
        <div v-if="previewTip.summary" class="jinnang-preview-summary">{{ previewTip.summary }}</div>
        <div class="jinnang-preview-content">{{ previewTip.sourceContent }}</div>
      </div>
    </n-modal>
  </div>
</template>

<style scoped>
.jinnang-picker {
  position: relative;
  padding: var(--space-3);
}

.jinnang-picker-close {
  position: absolute;
  right: var(--space-2);
  top: var(--space-2);
  width: var(--size-24);
  height: var(--size-24);
  border: 0;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-text-subtle);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.jinnang-picker-close:hover {
  background: var(--color-bg-soft-hover);
}

.jinnang-picker-title {
  font-size: var(--font-size-xs);
  padding-right: var(--size-28);
}

.jinnang-picker-list {
  margin-top: var(--space-2_5);
  max-height: var(--size-220);
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.jinnang-option {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  padding: var(--space-2_5) var(--space-3);
  border: var(--size-1) solid transparent;
  border-radius: 0;
  background: transparent;
  box-shadow: none !important;
  transition: border-color 0.16s ease, background-color 0.16s ease, box-shadow 0.16s ease;
}

.jinnang-option + .jinnang-option::before {
  content: "";
  position: absolute;
  top: calc(-1 * var(--space-1));
  left: var(--space-3);
  right: var(--space-3);
  height: var(--size-1);
  background: color-mix(in srgb, var(--color-border-soft) 70%, transparent);
  pointer-events: none;
}

.jinnang-option-main {
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
  display: flex;
  gap: var(--space-3);
  align-items: center;
  padding: 0;
}

.jinnang-option:hover,
.jinnang-option:focus-within {
  border-color: var(--color-border-brand-hover);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--color-bg-brand-soft) 68%, white);
  box-shadow: 0 1px 2px rgb(15 23 42 / 0.05) !important;
}

.jinnang-option.active {
  border-color: var(--color-overlay-brand-38);
  border-radius: var(--radius-xl);
  background: var(--color-bg-brand-soft);
}

.jinnang-radio-ui {
  display: inline-block;
  flex: none;
  width: var(--size-16);
  height: var(--size-16);
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
  gap: var(--space-1_5);
  padding-right: var(--size-96);
}

.jinnang-option-title {
  font-size: var(--font-size-sm);
}

.jinnang-option-summary {
  color: var(--color-text-subtle);
  font-size: var(--font-size-sm);
  line-height: 1.45;
}

.jinnang-view-btn {
  position: absolute;
  right: var(--space-2_5);
  top: var(--space-2);
  display: inline-flex;
  align-items: center;
  border: var(--size-1) solid color-mix(in srgb, var(--color-border-brand-strong) 70%, transparent);
  border-radius: var(--radius-pill);
  padding: var(--size-3) var(--space-1_5);
  background: linear-gradient(
    140deg,
    color-mix(in srgb, var(--color-bg-brand-soft) 85%, white),
    color-mix(in srgb, var(--color-bg-brand-soft) 60%, var(--color-bg-surface))
  );
  color: var(--color-text-brand-strong);
  font-size: var(--font-size-2xs);
  font-weight: 800;
  letter-spacing: 0.01em;
  box-shadow: var(--shadow-soft-sm);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.16s ease, background 0.16s ease, border-color 0.16s ease, transform 0.16s ease;
}

.jinnang-view-btn:hover {
  border-color: var(--color-border-brand-strong);
  background: linear-gradient(
    140deg,
    color-mix(in srgb, var(--color-bg-brand-soft) 96%, white),
    color-mix(in srgb, var(--color-bg-brand-soft) 78%, var(--color-bg-surface))
  );
  transform: translateY(calc(var(--size-1) * -1));
}

.jinnang-option:hover .jinnang-view-btn,
.jinnang-option:focus-within .jinnang-view-btn {
  opacity: 1;
  pointer-events: auto;
}

.jinnang-picker-empty {
  margin-top: var(--space-2);
  color: var(--color-text-runtime-subtle);
  font-size: var(--font-size-xs);
}

.jinnang-preview-panel {
  width: min(720px, calc(100vw - 48px));
  height: min(560px, calc(100vh - 72px));
  margin: 0 auto;
  border-radius: var(--radius-xl);
  border: var(--size-1) solid color-mix(in srgb, var(--color-border-soft) 78%, transparent);
  background: var(--color-bg-surface);
  box-shadow: var(--shadow-soft-lg);
  padding: var(--space-4) var(--space-4_5);
  display: flex;
  flex-direction: column;
  gap: var(--space-2_5);
  height: 100%;
  overflow: hidden;
}

.jinnang-preview-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--space-2);
}

.jinnang-preview-title {
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--color-text-title);
  line-height: 1.45;
}

.jinnang-preview-close {
  border: 0;
  background: transparent;
  width: var(--size-24);
  height: var(--size-24);
  border-radius: var(--radius-pill);
  color: var(--color-text-subtle);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.jinnang-preview-close:hover {
  background: var(--color-bg-soft-hover);
}

.jinnang-preview-summary {
  font-size: var(--font-size-sm);
  line-height: 1.6;
  color: var(--color-text-subtle);
}

.jinnang-preview-content {
  white-space: pre-wrap;
  line-height: 1.7;
  color: var(--color-text-body);
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: var(--space-1_5);
}

@media (hover: none) {
  .jinnang-view-btn {
    opacity: 1;
    pointer-events: auto;
  }
}
</style>
