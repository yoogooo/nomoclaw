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
  "https://lobehub.com/",
  "https://market.lobehub.com/",
  "https://github.com/",
  "https://modelscope.cn/skills/"
];

const urlExamples = [
  "https://skills.sh/vercel-labs/skills/find-skills",
  "https://lobehub.com/zh/skills/openclaw-skills-cli-developer",
  "https://market.lobehub.com/api/v1/skills/openclaw-skills-cli-developer/download",
  "https://github.com/anthropics/skills/tree/main/skills/skill-creator",
  "https://modelscope.cn/skills/@anthropics/skill-creator"
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
          <div class="ui-note-card">
            {{ t("agents.import.archiveHint") }}
          </div>
          <label class="ui-upload-trigger">
            <input
              class="ui-upload-trigger-input"
              type="file"
              accept=".zip,.tar.gz,.tgz,application/zip,application/gzip"
              @change="handleFileChange"
            >
            <span>{{ archiveFile ? archiveFile.name : t("agents.import.pickArchive") }}</span>
          </label>
        </div>
      </n-tab-pane>

      <n-tab-pane name="url" :tab="t('agents.import.tabUrl')">
        <div class="import-skill-panel">
          <div class="ui-note-card">
            <div class="ui-note-title">{{ t("agents.import.supportedSources") }}</div>
            <ul class="ui-list-compact">
              <li v-for="source in supportedSources" :key="source">{{ source }}</li>
            </ul>
            <div class="ui-note-title">{{ t("agents.import.urlExamples") }}</div>
            <ul class="ui-list-compact">
              <li v-for="example in urlExamples" :key="example">{{ example }}</li>
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
</style>
