<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NForm, NFormItem, NInput, NModal, NSwitch, NTabPane, NTabs } from "naive-ui";
import { conversationApi } from "@/api/conversationApi";
import type { ImportedSkillResponse } from "@/types/api";

const props = defineProps<{
  show: boolean;
  agentUid: string;
}>();

const emit = defineEmits<{
  (event: "update:show", value: boolean): void;
  (event: "success", skill: ImportedSkillResponse): void;
}>();
const { t } = useI18n();

const activeTab = ref<"url" | "archive" | "create">("archive");
const attachToAgent = ref(true);
const importing = ref(false);
const archiveFile = ref<File | null>(null);

const urlForm = reactive({
  url: ""
});

const createForm = reactive({
  skillKey: "",
  displayName: "",
  description: "",
  purpose: ""
});

const supportedSources = [
  "https://skills.sh/",
  "https://clawhub.ai/",
  "https://skillsmp.com/",
  "https://github.com/",
  "https://modelscope.cn/skills/"
];

const canSubmit = computed(() => {
  if (importing.value || !props.agentUid) return false;
  if (activeTab.value === "url") return !!urlForm.url.trim();
  if (activeTab.value === "archive") return !!archiveFile.value;
  return !!createForm.skillKey.trim() && !!createForm.displayName.trim() && !!createForm.description.trim();
});

watch(
  () => props.show,
  (show) => {
    if (!show) return;
    attachToAgent.value = true;
    archiveFile.value = null;
  }
);

async function submit() {
  if (!canSubmit.value) return;
  importing.value = true;
  try {
    let result: ImportedSkillResponse;
    if (activeTab.value === "url") {
      result = await conversationApi.importSkillFromUrl(props.agentUid, {
        url: urlForm.url.trim(),
        attachToAgent: attachToAgent.value
      });
    } else if (activeTab.value === "archive") {
      result = await conversationApi.importSkillArchive(props.agentUid, archiveFile.value!, attachToAgent.value);
    } else {
      result = await conversationApi.createSkill(props.agentUid, {
        skillKey: createForm.skillKey.trim(),
        displayName: createForm.displayName.trim(),
        description: createForm.description.trim(),
        purpose: createForm.purpose.trim(),
        attachToAgent: attachToAgent.value
      });
    }
    emit("success", result);
    emit("update:show", false);
  } finally {
    importing.value = false;
  }
}

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement | null;
  archiveFile.value = target?.files?.[0] || null;
}
</script>

<template>
  <n-modal
    :show="show"
    preset="card"
    :title="t('agents.import.title')"
    style="width: min(980px, calc(100vw - 32px))"
    @update:show="emit('update:show', $event)"
  >
    <div class="ui-modal-subtitle">
      {{ t("agents.import.subtitle") }}
    </div>

    <n-tabs v-model:value="activeTab" type="segment" animated>
      <n-tab-pane name="archive" :tab="t('agents.import.tabArchive')">
        <div class="import-skill-panel">
          <div class="import-hint-text">{{ t("agents.import.archiveHint") }}</div>
          <label class="ui-upload-card" role="button" :aria-label="t('agents.import.pickArchive')">
            <input
              class="ui-upload-card-input"
              type="file"
              accept=".zip,.tar.gz,.tgz,application/zip,application/gzip"
              @change="handleFileChange"
            >
            <div class="ui-upload-card-visual">
              <div class="ui-upload-icon" aria-hidden="true">↑</div>
              <div class="ui-upload-main">{{ t("agents.import.pickArchive") }}</div>
              <div class="ui-upload-sub">{{ t("agents.import.archiveSupportFormat") }}</div>
              <span class="ui-upload-cta">{{ t("agents.import.archiveChooseButton") }}</span>
            </div>
          </label>
          <div class="ui-upload-file-status">
            <span class="ui-upload-file-label">{{ t("agents.import.archiveSelectedLabel") }}</span>
            <span class="ui-upload-file-name">{{ archiveFile ? archiveFile.name : t("agents.import.archiveNotSelected") }}</span>
          </div>
        </div>
      </n-tab-pane>

      <n-tab-pane name="url" :tab="t('agents.import.tabUrl')">
        <div class="import-skill-panel">
          <div class="ui-note-card import-source-card">
            <div class="ui-note-title">{{ t("agents.import.supportedSources") }}</div>
            <ul class="ui-list-compact import-source-list">
              <li v-for="source in supportedSources" :key="source">{{ source }}</li>
            </ul>
          </div>

          <n-form label-placement="top" class="import-url-form">
            <n-form-item label="Skill URL">
              <n-input v-model:value="urlForm.url" :placeholder="t('agents.import.urlPlaceholder')" />
            </n-form-item>
          </n-form>
        </div>
      </n-tab-pane>

      <n-tab-pane name="create" :tab="t('agents.import.tabCreate')">
        <n-form label-placement="top" class="import-skill-panel">
          <n-form-item label="Skill Key">
            <n-input v-model:value="createForm.skillKey" :placeholder="t('agents.import.skillKeyPlaceholder')" />
          </n-form-item>
          <n-form-item :label="t('agents.basic.displayName')">
            <n-input v-model:value="createForm.displayName" :placeholder="t('agents.import.displayNamePlaceholder')" />
          </n-form-item>
          <n-form-item :label="t('agents.basic.description')">
            <n-input
              v-model:value="createForm.description"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 4 }"
              :placeholder="t('agents.import.descriptionPlaceholder')"
            />
          </n-form-item>
          <n-form-item :label="t('agents.import.purpose')">
            <n-input
              v-model:value="createForm.purpose"
              type="textarea"
              :autosize="{ minRows: 4, maxRows: 8 }"
              :placeholder="t('agents.import.purposePlaceholder')"
            />
          </n-form-item>
        </n-form>
      </n-tab-pane>
    </n-tabs>

    <div class="ui-modal-footer">
      <div class="ui-toggle-row">
        <span>{{ t("agents.import.attachToAgent") }}</span>
        <n-switch v-model:value="attachToAgent" />
      </div>
    </div>

    <template #action>
      <div class="ui-actions-end">
        <n-button @click="emit('update:show', false)">{{ t("common.cancel") }}</n-button>
        <n-button type="primary" :loading="importing" :disabled="!canSubmit" @click="submit">
          {{ activeTab === "create" ? t("agents.import.createSkill") : t("agents.import.importSkill") }}
        </n-button>
      </div>
    </template>
  </n-modal>
</template>

<style scoped>
.import-skill-panel {
  margin-top: var(--space-4);
}

.import-url-form {
  margin-top: var(--space-4);
}

.import-source-list {
  margin-bottom: 0;
}

.import-hint-text {
  margin-bottom: var(--space-3);
  color: var(--color-text-secondary);
  font-size: var(--font-size-base);
}

.ui-upload-card {
  position: relative;
  display: block;
  margin-top: var(--space-4);
  border-radius: var(--radius-xl);
  border: 1px dashed var(--color-border-brand-soft);
  background: linear-gradient(180deg, var(--color-bg-surface-soft), var(--color-bg-surface-mute));
  cursor: pointer;
  overflow: hidden;
}

.ui-upload-card:focus-within {
  border-color: var(--color-border-active);
  box-shadow: 0 0 0 var(--size-4) var(--color-bg-brand-soft);
}

.ui-upload-card-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  cursor: pointer;
}

.ui-upload-card-visual {
  padding: var(--space-7) var(--space-6);
  text-align: center;
}

.ui-upload-icon {
  width: 36px;
  height: 36px;
  margin: 0 auto var(--space-3);
  border-radius: 999px;
  display: grid;
  place-items: center;
  font-size: 18px;
  font-weight: 700;
  color: var(--color-accent-brand);
  background: var(--color-bg-brand-soft);
}

.ui-upload-main {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-text-primary);
}

.ui-upload-sub {
  margin-top: var(--space-2);
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.ui-upload-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-top: var(--space-4);
  min-width: 132px;
  height: 34px;
  padding: 0 var(--space-4);
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-border-brand-light);
  background: var(--color-bg-brand-soft);
  color: var(--color-text-brand-strong);
  font-size: var(--font-size-sm);
  font-weight: 700;
}

.ui-upload-file-status {
  margin-top: var(--space-3);
  display: flex;
  gap: var(--space-3);
  align-items: baseline;
  flex-wrap: wrap;
  color: var(--color-text-secondary);
}

.ui-upload-file-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-tertiary);
}

.ui-upload-file-name {
  font-size: var(--font-size-sm);
  color: var(--color-text-primary);
  font-weight: 600;
  word-break: break-all;
}
</style>
