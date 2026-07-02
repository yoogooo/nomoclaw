<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { Bot } from "lucide-vue-next";
import { NButton, NForm, NFormItem, NInput, NSelect } from "naive-ui";
import type { AvatarIconOption, BasicFormModel, ManagedAgent } from "@/components/agents/agentManagementTypes";

const props = defineProps<{
  selectedAgent: ManagedAgent;
  basicForm: BasicFormModel;
  avatarIconOptions: AvatarIconOption[];
  avatarColorOptions: string[];
  agentTypeOptions: Array<{ label: string; value: string }>;
}>();

const emit = defineEmits<{
  (e: "save"): void;
  (e: "remove"): void;
  (e: "update:displayName", value: string): void;
  (e: "update:agentType", value: string): void;
  (e: "update:description", value: string): void;
  (e: "update:avatar", value: string): void;
  (e: "update:avatarColor", value: string): void;
  (e: "update:workspace", value: string): void;
  (e: "update:codexWorkdir", value: string): void;
}>();
const { t } = useI18n();

const iconMap = computed(() =>
  props.avatarIconOptions.reduce<Record<string, AvatarIconOption["icon"]>>((acc, item) => {
    acc[item.key] = item.icon;
    return acc;
  }, {})
);

function avatarIconOf(raw: unknown) {
  const key = typeof raw === "string" ? raw.trim().toLowerCase() : "";
  return iconMap.value[key] || null;
}

function normalizePath(path: string) {
  return (path || "").trim().replace(/[\\/]+$/, "");
}

const derivedReportDir = computed(() => {
  const workspace = normalizePath(props.basicForm.workspace);
  return workspace ? `${workspace}/report` : "";
});

const derivedTmpDir = computed(() => {
  const workspace = normalizePath(props.basicForm.workspace);
  return workspace ? `${workspace}/tmp` : "";
});

const isCodexAgent = computed(() => props.basicForm.agentType === "codex");
</script>

<template>
  <div class="tab-body">
    <n-form label-placement="top" class="basic-form">
      <div class="ui-form-grid-single">
        <n-form-item :label="t('agents.basic.displayName')">
          <n-input :value="basicForm.displayName" @update:value="emit('update:displayName', $event)" />
        </n-form-item>
        <n-form-item>
          <template #label>
            <span class="ui-form-label-main">{{ t("agents.basic.agentName") }}</span>
            <span class="ui-form-label-note">{{ t("agents.basic.agentNameHint") }}</span>
          </template>
          <n-input :value="selectedAgent.agentName" disabled />
        </n-form-item>
      </div>
      <n-form-item :label="t('agents.basic.description')">
        <n-input
          :value="basicForm.description"
          type="textarea"
          :autosize="{ minRows: 3, maxRows: 6 }"
          @update:value="emit('update:description', $event)"
        />
      </n-form-item>
      <n-form-item :label="t('agents.basic.agentType')">
        <n-select
          :value="basicForm.agentType"
          :options="agentTypeOptions"
          @update:value="emit('update:agentType', String($event || 'chat'))"
        />
      </n-form-item>
      <div class="ui-form-grid-single">
        <n-form-item :label="t('agents.basic.workspaceDir')">
          <n-input :value="basicForm.workspace" @update:value="emit('update:workspace', $event)" />
        </n-form-item>
        <n-form-item :label="t('agents.basic.reportDir')">
          <n-input :value="derivedReportDir" disabled />
        </n-form-item>
      </div>
      <n-form-item v-if="isCodexAgent" :label="t('agents.basic.codexWorkdir')">
        <n-input :value="basicForm.codexWorkdir" @update:value="emit('update:codexWorkdir', $event)" />
      </n-form-item>
      <n-form-item :label="t('agents.basic.tmpDir')">
        <n-input :value="derivedTmpDir" disabled />
      </n-form-item>
      <n-form-item :label="t('agents.basic.iconSelect')">
        <div class="ui-avatar-config">
          <div class="ui-avatar-preview-card">
            <div class="ui-avatar-preview-circle" :style="{ backgroundColor: basicForm.avatarColor }">
              <component :is="avatarIconOf(basicForm.avatar) || Bot" :size="20" />
            </div>
            <div class="ui-avatar-preview-text">{{ t("agents.basic.preview") }}</div>
          </div>
          <div class="ui-avatar-selector-group">
            <div class="ui-avatar-section-title">{{ t("agents.basic.iconStyle") }}</div>
            <div class="ui-avatar-icon-grid">
              <button
                v-for="item in avatarIconOptions"
                :key="item.key"
                type="button"
                class="ui-avatar-icon-btn"
                :class="{ active: basicForm.avatar === item.key }"
                @click="emit('update:avatar', item.key)"
              >
                <component :is="item.icon" :size="16" />
              </button>
            </div>
            <div class="ui-avatar-section-title">{{ t("agents.basic.themeColor") }}</div>
            <div class="ui-avatar-color-grid">
              <button
                v-for="color in avatarColorOptions"
                :key="`basic_color_${color}`"
                type="button"
                class="ui-avatar-color-btn"
                :class="{ active: basicForm.avatarColor === color }"
                :style="{ backgroundColor: color }"
                @click="emit('update:avatarColor', color)"
              />
            </div>
          </div>
        </div>
      </n-form-item>
    </n-form>
    <div class="basic-actions-row">
      <n-button size="small" type="primary" @click="emit('save')">{{ t("common.save") }}</n-button>
      <n-button size="small" type="error" secondary strong @click="emit('remove')">{{ t("common.delete") }}</n-button>
    </div>
  </div>
</template>

<style scoped>
.tab-body {
  padding-top: var(--space-1_5);
}

.basic-form {
  margin-top: var(--space-1_5);
}

.basic-actions-row {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: var(--space-3);
  margin-top: var(--space-3_5);
}
</style>
