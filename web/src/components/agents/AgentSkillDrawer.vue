<script setup lang="ts">
import { NDrawer, NDrawerContent } from "naive-ui";
import type { ManagedSkill } from "@/components/agents/agentManagementTypes";

defineProps<{
  show: boolean;
  skill: ManagedSkill | null;
}>();

const emit = defineEmits<{
  (e: "update:show", value: boolean): void;
}>();
</script>

<template>
  <n-drawer :show="show" :width="460" placement="right" @update:show="emit('update:show', $event)">
    <n-drawer-content v-if="skill" :title="skill.name" closable>
      <div class="ui-stack-sm">
        <div class="ui-card-section">
          <div class="skill-drawer-label ui-field-label">状态</div>
          <div class="skill-drawer-value ui-field-value">{{ skill.enabled ? "已启用" : "已关闭" }}</div>
        </div>
        <div class="ui-card-section">
          <div class="skill-drawer-label ui-field-label">描述</div>
          <div class="skill-drawer-value ui-field-value">{{ skill.description || "暂无描述" }}</div>
        </div>
        <div class="ui-card-section">
          <div class="skill-drawer-label ui-field-label">完整路径</div>
          <div class="skill-drawer-path ui-field-value">{{ skill.path }}</div>
        </div>
      </div>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.skill-drawer-path {
  word-break: break-all;
}
</style>
