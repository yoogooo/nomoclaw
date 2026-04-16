<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { NButton, NSwitch, NTag } from "naive-ui";
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
}>(), {
  importDisabled: false,
  importLabel: "",
  showLinkedAgents: false,
  showToggle: true,
  showPath: true,
  showDescriptionCopy: true,
  showToolbar: true
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
        class="ui-card-base ui-card-padding-md"
        @click="emit('open', skill.id)"
      >
        <div class="ui-card-head-between">
          <div class="skill-card-name ui-title-strong">{{ skill.name }}</div>
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
            size="small"
            round
            type="success"
          >
            {{ agent.displayName || agent.agentName }}
          </n-tag>
        </div>
        <div v-else-if="showLinkedAgents" class="ui-caption-muted skill-card-linked-empty">
          {{ t("agents.skills.unlinked") }}
        </div>
        <div v-if="showPath" class="ui-caption-muted ui-path-break skill-card-path">{{ skill.path }}</div>
      </article>
    </div>
    <div v-else class="ui-empty-muted">{{ t("agents.skills.empty") }}</div>
  </div>
</template>

<style scoped>
.skill-card-desc {
  margin-top: var(--space-2);
  color: var(--color-text-secondary);
  line-height: 1.6;
  min-height: var(--size-42);
}

.skill-card-path {
  margin-top: var(--space-2);
}

.skill-card-linked-agents {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-top: var(--space-3);
}

.skill-card-linked-empty {
  margin-top: var(--space-3);
}

.ui-toolbar-start {
  justify-content: flex-start;
}
</style>
