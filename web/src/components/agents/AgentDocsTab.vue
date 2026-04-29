<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { NButton, NInput, NSwitch } from "naive-ui";

interface DocListItem {
  key: string;
  label: string;
  enabled: boolean;
  sizeBytes: number;
  updatedText: string;
}

const props = defineProps<{
  docItems: DocListItem[];
  selectedDocKey: string;
  selectedDocLabel: string;
  selectedDocPath: string;
  selectedDocEnabled: boolean;
  docEditable: boolean;
  selectedDocContent: string;
}>();

const emit = defineEmits<{
  (e: "select-doc", key: string): void;
  (e: "toggle-doc", key: string, enabled: boolean): void;
  (e: "toggle-edit"): void;
  (e: "save"): void;
  (e: "update-content", value: string): void;
}>();
const { t } = useI18n();
</script>

<template>
  <div class="tab-body">
    <div class="docs-split">
      <aside class="docs-list ui-card-soft">
        <div class="docs-list-title ui-title-strong">{{ t("agents.docs.listTitle") }}</div>
        <button
          v-for="item in docItems"
          :key="item.key"
          type="button"
          class="doc-list-item ui-card-base ui-card-hoverable"
          :class="{ active: selectedDocKey === item.key }"
          @click="emit('select-doc', item.key)"
        >
          <div class="doc-list-top">
            <div class="doc-list-name ui-title-strong">{{ item.label }}</div>
            <n-switch
              size="small"
              :value="item.enabled"
              @update:value="emit('toggle-doc', item.key, $event)"
              @click.stop
            />
          </div>
          <div class="doc-list-meta">
            <span>{{ item.sizeBytes }} B</span>
            <span class="doc-list-updated">{{ item.updatedText }}</span>
          </div>
        </button>
      </aside>

      <section class="docs-editor ui-card-base">
        <div class="docs-editor-head">
          <div>
            <div class="docs-editor-title ui-title-strong">{{ selectedDocLabel }}</div>
            <div class="docs-editor-path ui-caption-muted">{{ selectedDocPath }}</div>
          </div>
          <div class="docs-actions">
            <n-button
              size="small"
              tertiary
              :disabled="!selectedDocEnabled"
              @click="emit('toggle-edit')"
            >
              {{ docEditable ? t("agents.docs.cancelEdit") : t("agents.docs.enableEdit") }}
            </n-button>
            <n-button
              size="small"
              type="primary"
              :disabled="!docEditable || !selectedDocEnabled"
              @click="emit('save')"
            >
              {{ t("agents.docs.saveConfig") }}
            </n-button>
          </div>
        </div>
        <n-input
          :value="selectedDocContent"
          type="textarea"
          :autosize="{ minRows: 18, maxRows: 34 }"
          :disabled="!docEditable || !selectedDocEnabled"
          @update:value="emit('update-content', $event)"
        />
      </section>
    </div>
  </div>
</template>

<style scoped>
.tab-body {
  padding-top: var(--space-1_5);
}

.docs-split {
  margin-top: var(--space-1);
  display: grid;
  grid-template-columns: var(--size-300) minmax(0, 1fr);
  gap: var(--space-3_5);
}

.docs-list {
  padding: var(--space-3);
}

.docs-list-title {
  margin-bottom: var(--space-2_5);
}

.doc-list-item {
  width: 100%;
  text-align: left;
  padding: var(--space-3_5) var(--space-3_5);
  min-height: var(--size-72);
  cursor: pointer;
}

.doc-list-item + .doc-list-item {
  margin-top: var(--space-2);
}

.doc-list-item.active {
  border-color: var(--color-border-active);
  box-shadow: var(--shadow-card-md);
}

.doc-list-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2_5);
}

.doc-list-meta {
  margin-top: var(--space-1);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2_5);
  font-size: var(--text-caption-size);
  color: var(--color-text-soft);
}

.doc-list-updated {
  text-align: right;
  white-space: nowrap;
}

.docs-editor {
  padding: var(--space-3);
}

.docs-editor-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-3);
  margin-bottom: var(--space-2_5);
}

.docs-actions {
  display: flex;
  gap: var(--space-2);
}

.docs-editor-title {
  font-size: var(--text-title-sm-size);
  font-weight: 600;
}

.docs-editor-path {
  margin-top: var(--space-1);
}

@media (max-width: 1120px) {
  .docs-split {
    grid-template-columns: 1fr;
  }
}
</style>
