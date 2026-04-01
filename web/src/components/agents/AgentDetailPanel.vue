<script setup lang="ts">
import type { ManagedAgent } from "@/components/agents/agentManagementTypes";

defineProps<{
  selectedAgent: ManagedAgent | null;
}>();
</script>

<template>
  <section class="agent-detail-column">
    <article v-if="selectedAgent" class="surface-card detail-tabs-card">
      <slot />
    </article>
    <div v-else class="surface-card">
      <div class="page-title">Agent 详细信息</div>
      <div class="ui-empty-muted">请先在左侧选择一个 Agent，或新建 Agent。</div>
    </div>
  </section>
</template>

<style scoped>
.agent-detail-column {
  max-height: calc(100vh - 250px);
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: var(--space-3_5);
}

.detail-tabs-card {
  min-height: 0;
  flex-shrink: 0;
}

@media (max-width: var(--size-breakpoint-lg)) {
  .agent-detail-column {
    overflow: visible;
    max-height: none;
  }
}
</style>
