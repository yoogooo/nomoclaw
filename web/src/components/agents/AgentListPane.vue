<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { Plus } from "lucide-vue-next";
import { NButton } from "naive-ui";
import type { AvatarIconOption, ManagedAgent } from "@/components/agents/agentManagementTypes";

const props = defineProps<{
  agents: ManagedAgent[];
  selectedAgentUid: string;
  avatarIconOptions: AvatarIconOption[];
  defaultAvatarColor: string;
}>();

const emit = defineEmits<{
  (e: "create"): void;
  (e: "select", agentUid: string): void;
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

function avatarFallbackText(agent: ManagedAgent) {
  const value = (agent.displayName || agent.agentName || "").trim();
  return value.slice(0, 1).toUpperCase() || "A";
}
</script>

<template>
  <aside class="agent-list-column">
    <div class="surface-card agent-list-pane">
      <div class="ui-pane-head">
        <div class="pane-title ui-pane-title">{{ t("agents.list.title") }}</div>
        <n-button size="small" type="primary" class="new-agent-btn" @click="emit('create')">
          {{ t("agents.list.newAgent") }}
        </n-button>
      </div>
      <div v-if="!agents.length" class="ui-empty-muted">{{ t("agents.list.empty") }}</div>
      <div v-else class="agent-list">
        <button
          v-for="agent in agents"
          :key="agent.agentUid"
          class="agent-row"
          :class="{ active: agent.agentUid === selectedAgentUid }"
          type="button"
          @click="emit('select', agent.agentUid)"
        >
          <div class="agent-item-avatar" :style="{ backgroundColor: agent.avatarColor || defaultAvatarColor }">
            <component v-if="avatarIconOf(agent.avatar)" :is="avatarIconOf(agent.avatar)" :size="18" />
            <span v-else>{{ avatarFallbackText(agent) }}</span>
          </div>
          <div class="agent-row-meta">
            <div class="agent-item-name">{{ agent.displayName || agent.agentName }}</div>
            <div class="agent-item-meta">{{ agent.memberRole || t("agents.list.member") }}</div>
          </div>
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.agent-list-column {
  min-height: 0;
  overflow: hidden;
}

.agent-list-pane {
  height: 100%;
  max-height: calc(100vh - 250px);
  min-height: 0;
  overflow: auto;
}

.new-agent-btn {
  min-width: var(--size-108);
  border-radius: var(--radius-md);
}

.agent-list {
  margin-top: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-2_5);
}

.agent-row {
  position: relative;
  display: flex;
  width: 100%;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2_5) var(--space-3);
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-xl);
  background: var(--color-bg-surface);
  text-align: left;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.agent-row:hover {
  border-color: var(--color-border-slate-light);
  background: var(--color-bg-surface-soft);
}

.agent-row.active {
  border-color: var(--color-border-brand-hover);
  background: var(--color-gradient-agent-active);
  box-shadow: var(--color-shadow-brand-soft);
}

.agent-row-meta {
  min-width: 0;
}

.agent-item-avatar {
  display: flex;
  width: var(--size-44);
  height: var(--size-44);
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  color: var(--color-text-inverse);
  font-weight: 700;
  flex-shrink: 0;
}

.agent-item-name {
  font-weight: 600;
}

.agent-row.active .agent-item-name {
  color: var(--color-text-brand);
}

.agent-item-meta {
  margin-top: var(--space-1);
  color: var(--color-text-muted);
  font-size: var(--text-body-size);
}

.agent-row.active .agent-item-avatar {
  box-shadow: var(--color-shadow-brand-inner);
}

@media (max-width: var(--size-breakpoint-lg)) {
  .agent-list-column {
    overflow: visible;
  }

  .agent-list-pane {
    max-height: none;
  }
}
</style>
