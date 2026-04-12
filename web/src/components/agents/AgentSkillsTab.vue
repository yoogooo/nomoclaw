<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { NButton, NSwitch } from "naive-ui";

interface SkillItem {
  id: string;
  name: string;
  description: string;
  path: string;
  enabled: boolean;
}

defineProps<{
  skills: SkillItem[];
}>();

const emit = defineEmits<{
  (e: "toggle", skillId: string, enabled: boolean): void;
  (e: "open", skillId: string): void;
  (e: "import"): void;
}>();
const { t } = useI18n();
</script>

<template>
  <div class="ui-tab-body">
    <div class="ui-toolbar-between">
      <div class="ui-copy-muted-block">{{ t("agents.skills.description") }}</div>
      <n-button type="primary" @click="emit('import')">{{ t("agents.skills.import") }}</n-button>
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
            size="small"
            :value="skill.enabled"
            @update:value="emit('toggle', skill.id, $event)"
            @click.stop
          />
        </div>
        <div class="skill-card-desc">{{ skill.description || t("agents.common.noDescription") }}</div>
        <div class="ui-caption-muted ui-path-break skill-card-path">{{ skill.path }}</div>
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
</style>
