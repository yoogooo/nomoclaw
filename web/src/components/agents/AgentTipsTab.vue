<script setup lang="ts">
import { computed, ref } from "vue";
import { NButton, NInput, NPopconfirm } from "naive-ui";
import { Pencil, Trash2 } from "lucide-vue-next";
import type { AgentTip } from "@/components/agents/agentManagementTypes";

defineProps<{
  tips: AgentTip[];
  tipTitle: string;
  tipContent: string;
}>();

const emit = defineEmits<{
  (e: "add"): void;
  (e: "remove", tipId: string): void;
  (e: "update-tip", payload: { tipId: string; title: string; content: string }): void;
  (e: "update:title", value: string): void;
  (e: "update:content", value: string): void;
}>();

const editingTipId = ref("");
const editingTitle = ref("");
const editingContent = ref("");

const isEditing = computed(() => editingTipId.value !== "");

function startEdit(tip: AgentTip) {
  editingTipId.value = tip.id;
  editingTitle.value = tip.title;
  editingContent.value = tip.content;
}

function cancelEdit() {
  editingTipId.value = "";
  editingTitle.value = "";
  editingContent.value = "";
}

function saveEdit() {
  if (!editingTipId.value) return;
  emit("update-tip", {
    tipId: editingTipId.value,
    title: editingTitle.value,
    content: editingContent.value
  });
  cancelEdit();
}
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
        <n-button :disabled="isEditing" @click="emit('add')">新增锦囊</n-button>
      </div>
    </div>
    <div v-if="tips.length" class="ui-note-list">
      <div v-for="tip in tips" :key="tip.id" class="ui-note-item">
        <template v-if="editingTipId === tip.id">
          <div class="ui-note-editor tip-editor">
            <n-input :value="editingTitle" placeholder="锦囊标题" @update:value="editingTitle = $event" />
            <n-input
              :value="editingContent"
              type="textarea"
              :autosize="{ minRows: 3, maxRows: 8 }"
              placeholder="锦囊内容"
              @update:value="editingContent = $event"
            />
            <div class="ui-actions-right">
              <n-button tertiary @click="cancelEdit">取消</n-button>
              <n-button type="primary" @click="saveEdit">保存</n-button>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="ui-note-head">
            <div class="tip-title ui-item-title">{{ tip.title }}</div>
            <div class="tip-actions">
              <n-button text title="编辑" aria-label="编辑" @click="startEdit(tip)">
                <Pencil :size="14" />
              </n-button>
              <n-popconfirm
                :show-icon="false"
                :positive-button-props="{ type: 'error' }"
                positive-text="删除"
                negative-text="取消"
                @positive-click="emit('remove', tip.id)"
              >
                <template #trigger>
                  <n-button text type="error" title="删除" aria-label="删除" :disabled="isEditing">
                    <Trash2 :size="14" />
                  </n-button>
                </template>
                确认删除“{{ tip.title }}”吗？
              </n-popconfirm>
            </div>
          </div>
          <div class="tip-content ui-item-content">{{ tip.content }}</div>
        </template>
      </div>
    </div>
    <div v-else class="ui-empty-muted">暂无锦囊</div>
  </div>
</template>

<style scoped>
.tab-body {
  padding-top: var(--space-1_5);
}

.ui-note-item {
  background: var(--color-bg-surface-soft);
  border-color: var(--color-border-soft);
}

.tip-title {
  color: var(--color-text-primary);
}

.tip-content {
  color: var(--color-text-secondary);
}

.tip-actions {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.16s ease;
}

.tip-actions :deep(.n-button__content) {
  display: inline-flex;
  align-items: center;
}

.ui-note-item:hover .tip-actions,
.ui-note-item:focus-within .tip-actions {
  opacity: 1;
  pointer-events: auto;
}

.tip-editor {
  margin-top: 0;
}

.tip-editor .ui-actions-right {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
}

:global(:root[data-theme="dark"]) .tab-body .ui-note-item {
  background: #232427;
  border-color: rgba(255, 255, 255, 0.14);
}

:global(:root[data-theme="dark"]) .tab-body .tip-title {
  color: #F2F3F5;
}

:global(:root[data-theme="dark"]) .tab-body .tip-content {
  color: #E7EAEE;
}
</style>
