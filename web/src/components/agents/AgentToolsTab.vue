<script setup lang="ts">
import { computed } from "vue";
import { useI18n } from "vue-i18n";
import { NCollapse, NCollapseItem, NSwitch } from "naive-ui";

interface ToolItem {
  id: string;
  name: string;
  description: string;
  serverUid?: string;
  serverName?: string;
  serverDisplayName?: string;
  enabled: boolean;
}

const props = defineProps<{
  tools: ToolItem[];
  groupByServer?: boolean;
}>();

const emit = defineEmits<{
  (e: "toggle", toolId: string, enabled: boolean): void;
}>();
const { t } = useI18n();

const groupedTools = computed(() => {
  if (!props.groupByServer) {
    return [];
  }
  const groups = new Map<string, { key: string; name: string; tools: ToolItem[] }>();
  for (const tool of props.tools) {
    const key = tool.serverUid || tool.serverName || "unknown";
    const name = tool.serverDisplayName || tool.serverName || tool.serverUid || "MCP";
    const group = groups.get(key);
    if (group) {
      group.tools.push(tool);
    } else {
      groups.set(key, { key, name, tools: [tool] });
    }
  }
  return [...groups.values()];
});
</script>

<template>
  <div class="tab-body">
    <template v-if="tools.length">
      <n-collapse v-if="groupByServer" class="tool-server-groups" :default-expanded-names="groupedTools.map((group) => group.key)">
        <n-collapse-item
          v-for="group in groupedTools"
          :key="group.key"
          :name="group.key"
          class="tool-server-group"
        >
          <template #header>
            <div class="tool-server-title">
              <span>{{ group.name }}</span>
              <span class="tool-server-count">{{ group.tools.length }}</span>
            </div>
          </template>
          <div class="tools-card-grid">
            <article
              v-for="tool in group.tools"
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
        </n-collapse-item>
      </n-collapse>
      <div v-else class="tools-card-grid">
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
    </template>
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

.tool-server-groups {
  margin-top: var(--space-3);
}

.tool-server-title {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-primary);
  font-size: var(--text-title-sm-size);
  font-weight: 800;
  letter-spacing: 0.01em;
}

.tool-server-count {
  min-width: var(--size-24);
  padding: 0 var(--space-2);
  border-radius: var(--radius-full);
  background: var(--color-bg-muted);
  color: var(--color-text-muted);
  text-align: center;
  font-size: var(--text-caption-size);
  font-weight: 700;
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
  font-size: var(--text-title-sm-size);
  font-weight: 700;
  letter-spacing: 0.01em;
}

.tool-card-desc {
  margin-top: var(--space-2_5);
  color: var(--color-text-secondary);
  line-height: 1.55;
  font-size: var(--text-body-size);
}
</style>
