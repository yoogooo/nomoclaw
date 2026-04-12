<script setup lang="ts">
import { useI18n } from "vue-i18n";
import { NSwitch } from "naive-ui";

interface ToolItem {
  id: string;
  name: string;
  description: string;
  enabled: boolean;
}

defineProps<{
  tools: ToolItem[];
}>();

const emit = defineEmits<{
  (e: "toggle", toolId: string, enabled: boolean): void;
}>();
const { t } = useI18n();
</script>

<template>
  <div class="tab-body">
    <div v-if="tools.length" class="tools-card-grid">
      <article
        v-for="tool in tools"
        :key="tool.id"
        class="tool-card ui-card-base"
      >
        <div class="tool-card-head">
          <div class="tool-card-name ui-title-strong">{{ tool.name }}</div>
          <n-switch
            size="small"
            :value="tool.enabled"
            @update:value="emit('toggle', tool.id, $event)"
          />
        </div>
        <div class="tool-card-desc">{{ tool.description }}</div>
      </article>
    </div>
    <div v-else class="pane-empty-tip">{{ t("agents.tools.empty") }}</div>
  </div>
</template>

<style scoped>
.tab-body {
  padding-top: var(--space-1_5);
}

.pane-empty-tip {
  margin-top: var(--space-3);
  color: var(--color-text-muted);
}

.tools-card-grid {
  margin-top: var(--space-3_5);
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(var(--size-300), 1fr));
  gap: var(--space-3);
}

.tool-card {
  padding: var(--space-3_5) var(--space-4);
}

.tool-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2_5);
}

.tool-card-name {
  font-size: var(--font-size-lg);
  font-weight: 700;
  letter-spacing: 0.01em;
}

.tool-card-desc {
  margin-top: var(--space-2_5);
  color: var(--color-text-secondary);
  line-height: 1.55;
  font-size: var(--font-size-md);
}
</style>
