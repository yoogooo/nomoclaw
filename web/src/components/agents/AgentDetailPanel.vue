<script setup lang="ts">
import { useI18n } from "vue-i18n";
import type { ManagedAgent } from "@/components/agents/agentManagementTypes";

defineProps<{
  selectedAgent: ManagedAgent | null;
}>();
const { t } = useI18n();
</script>

<template>
  <section class="agent-detail-column">
    <article v-if="selectedAgent" class="surface-card detail-tabs-card">
      <slot />
    </article>
    <div v-else class="surface-card">
      <div class="page-title">{{ t("agents.detail.title") }}</div>
      <div class="ui-empty-muted">{{ t("agents.detail.empty") }}</div>
    </div>
  </section>
</template>

<style scoped>
.agent-detail-column {
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: var(--space-3_5);
}

.detail-tabs-card {
  min-height: 0;
  height: 100%;
  flex: 1 1 auto;
  overflow: hidden;
  box-shadow: none;
}

@media (max-width: 1120px) {
  .agent-detail-column {
    overflow: visible;
    max-height: none;
  }
}
</style>
