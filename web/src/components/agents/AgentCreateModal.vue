<script setup lang="ts">
import { computed } from "vue";
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
    title="新建 Agent"
    style="width: min(var(--size-760), calc(100vw - var(--size-32)))"
    @update:show="emit('update:show', $event)"
  >
    <n-form label-placement="top">
      <n-form-item label="显示名称">
        <n-input :value="form.displayName" placeholder="例如：通用助手" @update:value="emit('update:displayName', $event)" />
      </n-form-item>
      <n-form-item>
        <template #label>
          <span class="ui-form-label-main">Agent 标识</span>
          <span class="ui-form-label-note">（创建后不可修改，并作为目录名称使用）</span>
        </template>
        <n-input :value="form.agentName" placeholder="例如：agent_general_assistant" @update:value="emit('update:agentName', $event)" />
      </n-form-item>
      <n-form-item label="描述">
        <n-input :value="form.description" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" @update:value="emit('update:description', $event)" />
      </n-form-item>
      <n-form-item label="Icon 选择">
        <div class="ui-avatar-config">
          <div class="ui-avatar-preview-card">
            <div class="ui-avatar-preview-circle" :style="{ backgroundColor: form.avatarColor }">
              <component :is="avatarIconOf(form.avatar) || Bot" :size="20" />
            </div>
            <div class="ui-avatar-preview-text">实时预览</div>
          </div>
          <div class="ui-avatar-selector-group">
            <div class="ui-avatar-section-title">图标样式</div>
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
            <div class="ui-avatar-section-title">主题色</div>
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
        <n-button @click="emit('update:show', false)">取消</n-button>
        <n-button type="primary" @click="emit('save')">创建 Agent</n-button>
      </div>
    </template>
  </n-modal>
</template>
