<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { NButton, NDrawer, NDrawerContent, NSwitch } from "naive-ui";
import type { ManagedGlobalSkill, ManagedGlobalSkillAgentBinding } from "@/components/agents/agentManagementTypes";

const props = withDefaults(defineProps<{
  show: boolean;
  skill: ManagedGlobalSkill | null;
  loading?: boolean;
  saving?: boolean;
  enabledDraft?: boolean;
  agentBindings?: ManagedGlobalSkillAgentBinding[];
  saveDisabled?: boolean;
}>(), {
  loading: false,
  saving: false,
  enabledDraft: false,
  agentBindings: () => [],
  saveDisabled: true
});

const emit = defineEmits<{
  (e: "update:show", value: boolean): void;
  (e: "update:enabled", value: boolean): void;
  (e: "update:agent", payload: { agentUid: string; enabled: boolean }): void;
  (e: "save"): void;
  (e: "delete"): void;
}>();

const { t } = useI18n();
const title = computed(() => props.skill?.name || "");
</script>

<template>
  <n-drawer :show="show" :width="520" placement="right" @update:show="emit('update:show', $event)">
    <n-drawer-content :title="title" closable>
      <div v-if="skill" class="ui-stack-sm">
        <div class="ui-card-section">
          <div class="skill-drawer-label ui-field-label">{{ t("agents.skillDrawer.status") }}</div>
          <div class="skill-drawer-switch-row">
            <span class="ui-field-value">{{ enabledDraft ? t("common.enabled") : t("common.disabled") }}</span>
            <n-switch
              :value="enabledDraft"
              :disabled="loading || saving"
              @update:value="emit('update:enabled', $event)"
            />
          </div>
        </div>
        <div class="ui-card-section">
          <div class="skill-drawer-label ui-field-label">{{ t("agents.skillDrawer.description") }}</div>
          <div class="skill-drawer-value ui-field-value">{{ skill.description || t("agents.common.noDescription") }}</div>
        </div>
        <div class="ui-card-section">
          <div class="skill-drawer-label ui-field-label">{{ t("agents.skillDrawer.path") }}</div>
          <div class="skill-drawer-path ui-field-value">{{ skill.path }}</div>
        </div>
        <div class="ui-card-section">
          <div class="skill-drawer-label ui-field-label">{{ t("agents.skillDrawer.agentBindings") }}</div>
          <div class="skill-agent-list">
            <div
              v-for="agent in agentBindings"
              :key="agent.agentUid"
              class="skill-agent-row"
            >
              <div class="skill-agent-name">{{ agent.displayName || agent.agentName }}</div>
              <n-switch
                :value="agent.enabled"
                :disabled="loading || saving"
                @update:value="emit('update:agent', { agentUid: agent.agentUid, enabled: $event })"
              />
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="skill-drawer-actions">
          <n-button type="error" secondary :loading="saving" :disabled="loading || saving || !skill" @click="emit('delete')">
            {{ t("common.delete") }}
          </n-button>
          <n-button type="primary" :loading="saving" :disabled="saveDisabled || loading || !skill" @click="emit('save')">
            {{ t("common.save") }}
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

.skill-drawer-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.skill-agent-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2_5);
}

.skill-agent-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2_5) var(--space-3);
  border-radius: var(--radius-md);
  border: var(--size-1) solid var(--color-border-soft);
  background: var(--color-bg-panel);
}

.skill-agent-name {
  color: var(--color-text-primary);
  font-size: var(--text-body-size);
}

.skill-drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}
</style>
