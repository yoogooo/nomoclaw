<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { NButton, NDrawer, NDrawerContent, NTag } from "naive-ui";
import type { ManagedGlobalSkill, ManagedSharedSkill, ManagedSkill } from "@/components/agents/agentManagementTypes";

const props = withDefaults(defineProps<{
  show: boolean;
  skill: ManagedSkill | ManagedSharedSkill | ManagedGlobalSkill | null;
  showGlobalActions?: boolean;
  actionLoading?: boolean;
}>(), {
  showGlobalActions: false,
  actionLoading: false
});

const emit = defineEmits<{
  (e: "update:show", value: boolean): void;
  (e: "toggle-global"): void;
  (e: "delete"): void;
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
        <div v-if="'linkedAgents' in skill" class="ui-card-section">
          <div class="skill-drawer-label ui-field-label">{{ t("agents.skillDrawer.linkedAgents") }}</div>
          <div v-if="skill.linkedAgents?.length" class="skill-drawer-linked-agents">
            <n-tag
              v-for="agent in skill.linkedAgents"
              :key="agent.agentUid"
              size="small"
              round
              type="success"
            >
              {{ agent.displayName || agent.agentName }}
            </n-tag>
          </div>
          <div v-else class="skill-drawer-value ui-field-value">{{ t("agents.skillDrawer.noLinkedAgents") }}</div>
        </div>
      </div>
      <template v-if="props.showGlobalActions" #footer>
        <div class="skill-drawer-actions">
          <n-button type="primary" secondary :loading="props.actionLoading" @click="emit('toggle-global')">
            {{ skill.enabled ? t("agents.skills.disable") : t("agents.skills.enable") }}
          </n-button>
          <n-button type="error" secondary :loading="props.actionLoading" @click="emit('delete')">
            {{ t("common.delete") }}
          </n-button>
        </div>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.skill-drawer-path {
  word-break: break-all;
}

.skill-drawer-linked-agents {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-2);
}

.skill-drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
