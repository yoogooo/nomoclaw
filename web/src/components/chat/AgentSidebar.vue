<script setup lang="ts">
import { computed } from "vue";
import {
  Bot,
  Briefcase,
  Brain,
  BookOpen,
  Code2,
  Cpu,
  Database,
  FileSearch,
  Globe,
  Headphones,
  Lightbulb,
  Palette,
  PenTool,
  Rocket,
  Scale,
  Shield,
  Sparkles,
  User,
  Wrench
} from "lucide-vue-next";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { useConversationStore } from "@/stores/conversation";

const agentCatalogStore = useAgentCatalogStore();
const conversationStore = useConversationStore();

const AVATAR_ICON_MAP = {
  bot: Bot,
  user: User,
  sparkles: Sparkles,
  brain: Brain,
  book: BookOpen,
  tool: Wrench,
  shield: Shield,
  briefcase: Briefcase,
  code: Code2,
  database: Database,
  globe: Globe,
  search: FileSearch,
  rocket: Rocket,
  palette: Palette,
  idea: Lightbulb,
  audio: Headphones,
  legal: Scale,
  chip: Cpu,
  design: PenTool
} as const;

const AVATAR_COLOR_MAP: Record<keyof typeof AVATAR_ICON_MAP, string> = {
  bot: "var(--color-avatar-bot)",
  user: "var(--color-avatar-user)",
  sparkles: "var(--color-avatar-sparkles)",
  brain: "var(--color-avatar-brain)",
  book: "var(--color-avatar-book)",
  tool: "var(--color-avatar-tool)",
  shield: "var(--color-avatar-shield)",
  briefcase: "var(--color-avatar-briefcase)",
  code: "var(--color-avatar-code)",
  database: "var(--color-avatar-database)",
  globe: "var(--color-avatar-globe)",
  search: "var(--color-avatar-search)",
  rocket: "var(--color-avatar-rocket)",
  palette: "var(--color-avatar-palette)",
  idea: "var(--color-avatar-idea)",
  audio: "var(--color-avatar-audio)",
  legal: "var(--color-avatar-legal)",
  chip: "var(--color-avatar-chip)",
  design: "var(--color-avatar-design)"
};

const agents = computed(() =>
  [...agentCatalogStore.allAgents].sort((left, right) => {
    if (left.sortIndex !== right.sortIndex) {
      return left.sortIndex - right.sortIndex;
    }
    return (left.displayName || left.agentName).localeCompare(right.displayName || right.agentName, "zh-CN");
  })
);

function displayAvatar(name: string) {
  const normalized = (name || "").trim();
  return normalized ? normalized.slice(0, 1).toUpperCase() : "A";
}

function normalizeAvatarKey(raw?: string | null): keyof typeof AVATAR_ICON_MAP | null {
  const key = (raw || "").trim().toLowerCase();
  return key in AVATAR_ICON_MAP ? (key as keyof typeof AVATAR_ICON_MAP) : null;
}

function avatarIcon(raw?: string | null) {
  const key = normalizeAvatarKey(raw);
  return key ? AVATAR_ICON_MAP[key] : null;
}

function avatarBgColor(raw?: string | null, explicitColor?: string | null) {
  const provided = (explicitColor || "").trim();
  if (provided) return provided;
  const key = normalizeAvatarKey(raw);
  return key ? AVATAR_COLOR_MAP[key] : "var(--color-bg-soft-active)";
}
</script>

<template>
  <aside class="panel agent-sidebar">
    <div class="agent-brand">
      <div class="agent-brand-title">NomoClaw</div>
      <div class="agent-brand-subtitle">你的私人助理</div>
    </div>

    <div class="panel-body agent-sidebar-body">
      <div class="section-label">单聊</div>
      <div class="agent-list">
        <button
          v-for="agent in agents"
          :key="agent.agentUid"
          class="agent-entry"
          :class="{ active: agentCatalogStore.selectedEntryType === 'agent' && agentCatalogStore.selectedAgentUid === agent.agentUid }"
          @click="agentCatalogStore.selectAgent(agent.agentGroupUid, agent.agentUid); conversationStore.applyAgentSelection()"
        >
          <div class="agent-avatar" :style="{ backgroundColor: avatarBgColor(agent.avatar, (agent as any).avatarColor) }">
            <component v-if="avatarIcon(agent.avatar)" :is="avatarIcon(agent.avatar)" :size="16" />
            <span v-else>{{ displayAvatar(agent.displayName || agent.agentName) }}</span>
          </div>
          <div class="agent-name">{{ agent.displayName || agent.agentName }}</div>
        </button>
      </div>

      <div class="section-label section-spacing">团队</div>
      <div class="agent-list">
        <button
          v-for="group in agentCatalogStore.groups"
          :key="group.agentGroupUid"
          class="agent-entry"
          :class="{ active: agentCatalogStore.selectedEntryType === 'group' && agentCatalogStore.selectedAgentGroupUid === group.agentGroupUid }"
          @click="agentCatalogStore.selectGroup(group.agentGroupUid); conversationStore.applyAgentSelection()"
        >
          <div class="agent-avatar">{{ group.avatar || displayAvatar(group.displayName) }}</div>
          <div class="agent-name">{{ group.displayName }}</div>
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.agent-sidebar {
  display: flex;
  min-height: 0;
  flex-direction: column;
}

.agent-brand {
  padding: var(--space-4_5) var(--space-5) var(--space-4);
}

.agent-brand-title {
  color: var(--color-text-brand);
  font-size: var(--font-size-lg);
  font-weight: 600;
  letter-spacing: 0.08em;
}

.agent-brand-subtitle {
  margin-top: var(--space-2_5);
  color: var(--color-text-subtle);
  font-size: var(--font-size-sm);
}

.agent-sidebar-body {
  flex: 1;
  min-height: 0;
  padding-top: var(--space-6);
  overflow: auto;
}

.agent-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-2_5);
}

.agent-entry {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3) var(--space-4);
  border: var(--size-1) solid var(--color-border-soft);
  border-radius: var(--radius-xl-2);
  background: var(--color-bg-surface);
  color: inherit;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.agent-entry:hover {
  border-color: var(--color-border-slate-light);
  background: var(--color-bg-surface-soft);
}

.agent-entry.active {
  border-color: var(--color-border-brand-hover);
  background: var(--color-gradient-agent-active);
  box-shadow: var(--color-shadow-brand-soft);
}

.agent-avatar {
  display: flex;
  width: var(--size-42);
  height: var(--size-42);
  align-items: center;
  justify-content: center;
  border-radius: var(--size-15);
  color: var(--color-text-inverse);
  font-size: var(--size-15);
  font-weight: 600;
}

.agent-entry.active .agent-avatar {
  box-shadow: var(--color-shadow-brand-inner);
}

.agent-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-md);
  font-weight: 700;
  text-align: left;
}

.agent-entry.active .agent-name {
  color: var(--color-text-brand);
}

.section-spacing {
  margin-top: var(--space-5_5);
}

.agent-sidebar-body .section-label {
  position: sticky;
  top: 0;
  z-index: 1;
  margin: 0;
  padding: var(--space-1_5) 0 var(--space-2);
  background: transparent;
}

@media (max-width: var(--size-breakpoint-lg)) {
  .agent-brand {
    padding: var(--space-3_5) var(--space-4) var(--space-3);
  }

  .agent-sidebar-body {
    max-height: var(--size-260);
    padding-top: var(--space-3);
    padding-bottom: var(--space-3);
  }

  .agent-list {
    flex-direction: row;
    gap: var(--space-2_5);
    overflow-x: auto;
    padding-bottom: var(--size-2);
  }

  .agent-entry {
    min-width: var(--size-170);
    border-radius: var(--radius-md);
  }
}
</style>
