<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { NButton, NSwitch, NTag } from "naive-ui";
import { SquarePen } from "lucide-vue-next";
import type { SkillLinkedAgent } from "@/components/agents/agentManagementTypes";

interface SkillItem {
  id: string;
  name: string;
  description: string;
  path: string;
  enabled: boolean;
  linkedAgents?: SkillLinkedAgent[];
}

withDefaults(defineProps<{
  skills: SkillItem[];
  importDisabled?: boolean;
  importLabel?: string;
  showLinkedAgents?: boolean;
  showToggle?: boolean;
  showPath?: boolean;
  showDescriptionCopy?: boolean;
  showToolbar?: boolean;
  showHoverEdit?: boolean;
}>(), {
  importDisabled: false,
  importLabel: "",
  showLinkedAgents: false,
  showToggle: true,
  showPath: true,
  showDescriptionCopy: true,
  showToolbar: true,
  showHoverEdit: false
});

const emit = defineEmits<{
  (e: "toggle", skillId: string, enabled: boolean): void;
  (e: "open", skillId: string): void;
  (e: "import"): void;
}>();
const { t } = useI18n();
</script>

<template>
  <div class="ui-tab-body">
    <div
      v-if="showToolbar"
      class="ui-toolbar-between"
      :class="{ 'ui-toolbar-start': !showDescriptionCopy }"
    >
      <n-button type="primary" :disabled="importDisabled" @click="emit('import')">
        {{ importLabel || t("agents.skills.import") }}
      </n-button>
      <div v-if="showDescriptionCopy" class="ui-copy-muted-block">{{ t("agents.skills.description") }}</div>
    </div>
    <div v-if="skills.length" class="ui-card-grid">
      <article
        v-for="skill in skills"
        :key="skill.id"
        class="ui-card-base ui-card-padding-md skill-card"
        @click="emit('open', skill.id)"
      >
        <n-button
          v-if="showHoverEdit"
          class="skill-card-edit-sheet"
          size="small"
          tertiary
          type="primary"
          @click.stop="emit('open', skill.id)"
        >
          <template #icon>
            <SquarePen :size="14" />
          </template>
          {{ t("common.edit") }}
        </n-button>
        <div class="ui-card-head-between">
          <div class="skill-card-head-main">
            <div class="skill-card-name ui-title-strong">{{ skill.name }}</div>
          </div>
          <n-tag
            class="skill-card-status-tag"
            size="small"
            round
            :type="skill.enabled ? 'success' : 'default'"
          >
            {{ skill.enabled ? t("common.enabled") : t("common.disabled") }}
          </n-tag>
          <n-switch
            v-if="showToggle"
            size="small"
            :value="skill.enabled"
            @update:value="emit('toggle', skill.id, $event)"
            @click.stop
          />
        </div>
        <div class="skill-card-desc">{{ skill.description || t("agents.common.noDescription") }}</div>
        <div v-if="showLinkedAgents && skill.linkedAgents?.length" class="skill-card-linked-agents">
          <n-tag
            v-for="agent in skill.linkedAgents"
            :key="agent.agentUid"
            class="skill-card-agent-tag"
            size="small"
            round
          >
            {{ agent.displayName || agent.agentName }}
          </n-tag>
        </div>
        <div v-if="showPath" class="ui-caption-muted ui-path-break skill-card-path">{{ skill.path }}</div>
      </article>
    </div>
    <div v-else class="ui-empty-muted">{{ t("agents.skills.empty") }}</div>
  </div>
</template>

<style scoped>
.skill-card {
  position: relative;
  display: flex;
  min-height: var(--size-180);
  flex-direction: column;
  overflow: hidden;
}

.skill-card-edit-sheet {
  position: absolute;
  right: var(--space-3);
  bottom: var(--space-3);
  left: var(--space-3);
  justify-content: center;
  border-radius: var(--radius-lg);
  background: var(--color-bg-panel);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.22);
  opacity: 0;
  pointer-events: none;
  transform: translateY(calc(100% + var(--space-3)));
  transition:
    opacity 0.18s ease,
    transform 0.22s ease;
}

.skill-card:hover .skill-card-edit-sheet,
.skill-card:focus-within .skill-card-edit-sheet {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0);
}

.skill-card-head-main {
  min-width: 0;
  padding-right: var(--space-8);
  flex: 1;
}

.skill-card-name {
  min-width: 0;
}

.skill-card-status-tag {
  flex-shrink: 0;
}

.skill-card-desc {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  line-height: 1.6;
  min-height: var(--size-42);
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 10;
}

.skill-card-path {
  margin-top: var(--space-2);
}

.skill-card-linked-agents {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: auto;
  padding-top: var(--space-3);
}

.skill-card-agent-tag:deep(.n-tag) {
  background: rgba(15, 23, 42, 0.92);
  border-color: rgba(51, 65, 85, 0.9);
  color: #e2e8f0;
}

.ui-toolbar-start {
  justify-content: flex-start;
}
</style>
