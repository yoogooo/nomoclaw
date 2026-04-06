<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { NDrawer, NDrawerContent } from "naive-ui";
import type { ManagedSkill } from "@/components/agents/agentManagementTypes";

defineProps<{
  show: boolean;
  skill: ManagedSkill | null;
}>();

const emit = defineEmits<{
  (e: "update:show", value: boolean): void;
}>();
const { t } = useI18n();
</script>

<template>
  <n-drawer :show="show" :width="460" placement="right" @update:show="emit('update:show', $event)">
    <n-drawer-content v-if="skill" :title="skill.name" closable>
      <div class="ui-stack-sm">
        <div class="ui-card-section">
          <div class="skill-drawer-label ui-field-label">{{ t("agents.skillDrawer.status") }}</div>
          <div class="skill-drawer-value ui-field-value">{{ skill.enabled ? t("common.enabled") : t("agents.skillDrawer.disabled") }}</div>
        </div>
        <div class="ui-card-section">
          <div class="skill-drawer-label ui-field-label">{{ t("agents.skillDrawer.description") }}</div>
          <div class="skill-drawer-value ui-field-value">{{ skill.description || t("agents.common.noDescription") }}</div>
        </div>
        <div class="ui-card-section">
          <div class="skill-drawer-label ui-field-label">{{ t("agents.skillDrawer.path") }}</div>
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
