<script setup lang="ts">
import { NButton, NInput, NPopconfirm } from "naive-ui";
import type { AgentTip } from "@/components/agents/agentManagementTypes";

defineProps<{
  tips: AgentTip[];
  tipTitle: string;
  tipContent: string;
}>();

const emit = defineEmits<{
  (e: "add"): void;
  (e: "remove", tipId: string): void;
  (e: "update:title", value: string): void;
  (e: "update:content", value: string): void;
}>();
</script>

<template>
  <div class="tab-body">
    <div class="ui-note-editor">
      <n-input :value="tipTitle" placeholder="锦囊标题" @update:value="emit('update:title', $event)" />
      <n-input
        :value="tipContent"
        type="textarea"
        :autosize="{ minRows: 3, maxRows: 6 }"
        placeholder="锦囊内容"
        @update:value="emit('update:content', $event)"
      />
      <div class="ui-actions-right">
        <n-button @click="emit('add')">新增锦囊</n-button>
      </div>
    </div>
    <div v-if="tips.length" class="ui-note-list">
      <div v-for="tip in tips" :key="tip.id" class="ui-note-item">
        <div class="ui-note-head">
          <div class="tip-title ui-item-title">{{ tip.title }}</div>
          <n-popconfirm
            :show-icon="false"
            :positive-button-props="{ type: 'error' }"
            positive-text="删除"
            negative-text="取消"
            @positive-click="emit('remove', tip.id)"
          >
            <template #trigger>
              <n-button text type="error">删除</n-button>
            </template>
            确认删除“{{ tip.title }}”吗？
          </n-popconfirm>
        </div>
        <div class="tip-content ui-item-content">{{ tip.content }}</div>
      </div>
    </div>
    <div v-else class="ui-empty-muted">暂无锦囊</div>
  </div>
</template>

<style scoped>
.tab-body {
  padding-top: var(--space-1_5);
}
</style>
