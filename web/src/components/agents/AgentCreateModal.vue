<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { Bot } from "lucide-vue-next";
import { NButton, NForm, NFormItem, NInput, NModal } from "naive-ui";
import type { AvatarIconOption } from "@/components/agents/agentManagementTypes";

const props = defineProps<{
  show: boolean;
  form: {
    displayName: string;
    agentName: string;
    description: string;
    avatar: string;
    avatarColor: string;
  };
  avatarIconOptions: AvatarIconOption[];
  avatarColorOptions: string[];
}>();

const emit = defineEmits<{
  (e: "update:show", value: boolean): void;
  (e: "save"): void;
  (e: "update:displayName", value: string): void;
  (e: "update:agentName", value: string): void;
  (e: "update:description", value: string): void;
  (e: "update:avatar", value: string): void;
  (e: "update:avatarColor", value: string): void;
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
            <div class="ui-avatar-icon-grid">
              <button
                v-for="item in avatarIconOptions"
                :key="`create_icon_${item.key}`"
                type="button"
                class="ui-avatar-icon-btn"
                :class="{ active: form.avatar === item.key }"
                @click="emit('update:avatar', item.key)"
              >
                <component :is="item.icon" :size="16" />
              </button>
            </div>
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
