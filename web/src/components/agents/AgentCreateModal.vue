<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { Bot } from "lucide-vue-next";
import { NButton, NForm, NFormItem, NInput, NModal, NSelect } from "naive-ui";
import type { AvatarIconOption } from "@/components/agents/agentManagementTypes";
import AgentIconPicker from "@/components/agents/AgentIconPicker.vue";

const props = defineProps<{
  show: boolean;
  form: {
    displayName: string;
    agentName: string;
    agentType: string;
    description: string;
    avatar: string;
    avatarColor: string;
    workspace: string;
    codexWorkdir: string;
  };
  avatarIconOptions: AvatarIconOption[];
  avatarColorOptions: string[];
  agentTypeOptions: Array<{ label: string; value: string }>;
}>();

const emit = defineEmits<{
  (e: "update:show", value: boolean): void;
  (e: "save"): void;
  (e: "update:displayName", value: string): void;
  (e: "update:agentName", value: string): void;
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
  const workspace = normalizePath(props.form.workspace);
  return workspace ? `${workspace}/report` : "";
});

const derivedTmpDir = computed(() => {
  const workspace = normalizePath(props.form.workspace);
  return workspace ? `${workspace}/tmp` : "";
});

const isCodexAgent = computed(() => props.form.agentType === "codex");
</script>

<template>
  <n-modal
    :show="show"
    preset="card"
    :title="t('agents.create.title')"
    style="width: min(var(--size-760), calc(100vw - var(--size-32)))"
    @update:show="emit('update:show', $event)"
  >
    <n-form label-placement="top">
      <n-form-item :label="t('agents.basic.displayName')">
        <n-input :value="form.displayName" :placeholder="t('agents.create.displayNamePlaceholder')" @update:value="emit('update:displayName', $event)" />
      </n-form-item>
      <n-form-item>
        <template #label>
          <span class="ui-form-label-main">{{ t("agents.basic.agentName") }}</span>
          <span class="ui-form-label-note">{{ t("agents.basic.agentNameHint") }}</span>
        </template>
        <n-input :value="form.agentName" :placeholder="t('agents.create.agentNamePlaceholder')" @update:value="emit('update:agentName', $event)" />
      </n-form-item>
      <n-form-item :label="t('agents.basic.description')">
        <n-input :value="form.description" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" @update:value="emit('update:description', $event)" />
      </n-form-item>
      <n-form-item :label="t('agents.basic.agentType')">
        <n-select
          :value="form.agentType"
          :options="agentTypeOptions"
          @update:value="emit('update:agentType', String($event || 'chat'))"
        />
      </n-form-item>
      <n-form-item :label="t('agents.basic.workspaceDir')">
        <n-input
          :value="form.workspace"
          :placeholder="t('agents.create.workspaceDirPlaceholder')"
          @update:value="emit('update:workspace', $event)"
        />
      </n-form-item>
      <n-form-item v-if="isCodexAgent" :label="t('agents.basic.codexWorkdir')">
        <n-input
          :value="form.codexWorkdir"
          :placeholder="t('agents.create.codexWorkdirPlaceholder')"
          @update:value="emit('update:codexWorkdir', $event)"
        />
      </n-form-item>
      <n-form-item :label="t('agents.basic.reportDir')">
        <n-input
          :value="derivedReportDir"
          :placeholder="t('agents.create.reportDirPlaceholder')"
          disabled
        />
      </n-form-item>
      <n-form-item :label="t('agents.basic.tmpDir')">
        <n-input
          :value="derivedTmpDir"
          :placeholder="t('agents.create.tmpDirPlaceholder')"
          disabled
        />
      </n-form-item>
      <n-form-item :label="t('agents.basic.iconSelect')">
        <div class="ui-avatar-config">
          <div class="ui-avatar-preview-card">
            <div class="ui-avatar-preview-circle" :style="{ backgroundColor: form.avatarColor }">
              <component :is="avatarIconOf(form.avatar) || Bot" :size="20" />
            </div>
            <div class="ui-avatar-preview-text">{{ t("agents.basic.preview") }}</div>
          </div>
          <div class="ui-avatar-selector-group">
            <div class="ui-avatar-section-title">{{ t("agents.basic.iconStyle") }}</div>
            <AgentIconPicker
              :model-value="form.avatar"
              :options="avatarIconOptions"
              @update:model-value="emit('update:avatar', $event)"
            />
            <div class="ui-avatar-section-title">{{ t("agents.basic.themeColor") }}</div>
            <div class="ui-avatar-color-grid">
              <button
                v-for="color in avatarColorOptions"
                :key="`create_color_${color}`"
                type="button"
                class="ui-avatar-color-btn"
                :class="{ active: form.avatarColor === color }"
                :style="{ backgroundColor: color }"
                @click="emit('update:avatarColor', color)"
              />
            </div>
          </div>
        </div>
      </n-form-item>
    </n-form>
    <template #action>
      <div class="ui-actions-end">
        <n-button @click="emit('update:show', false)">{{ t("common.cancel") }}</n-button>
        <n-button type="primary" @click="emit('save')">{{ t("agents.create.submit") }}</n-button>
      </div>
    </template>
  </n-modal>
</template>
