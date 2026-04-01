<script setup lang="ts">
import { computed, ref } from "vue";
import { ArrowDown, ArrowUp, Check, Copy, Sparkles } from "lucide-vue-next";
import { NButton, NCard, NCollapse, NCollapseItem, NFlex, NPopconfirm, NTag } from "naive-ui";
import ApprovalBanner from "./ApprovalBanner.vue";
import ComposerPanel from "./composer/ComposerPanel.vue";
import { message as discreteMessage } from "@/discrete";
import { useConversationStore } from "@/stores/conversation";
import { useConversationRunsStore } from "@/stores/conversationRuns";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { useJinnangStore } from "@/stores/jinnang";
import { formatMessageTime } from "@/utils/format";
import { renderMarkdown } from "@/utils/markdown";
import type { ConversationMessage } from "@/types/api";

const conversationStore = useConversationStore();
const conversationRunsStore = useConversationRunsStore();
const agentCatalogStore = useAgentCatalogStore();
const jinnangStore = useJinnangStore();
const copiedMessageMap = ref<Record<string, boolean>>({});
const savingTipMap = ref<Record<string, boolean>>({});
const savedTipMap = ref<Record<string, boolean>>({});

const latestMessageUid = computed(() => {
  const items = conversationStore.messages;
  return (items.length ? items[items.length - 1].messageUid : "") || "";
});

function runTone(status: string) {
  if (status === "completed") return "success";
  if (status === "failed" || status === "rejected" || status === "canceled") return "error";
  if (status === "waiting_approval") return "warning";
  if (status === "running") return "info";
  return "default";
}

function copyConversationUid() {
  if (!conversationStore.currentConversationUid) return;
  void window.navigator.clipboard.writeText(conversationStore.currentConversationUid);
}

function messageActionKey(messageItem: ConversationMessage) {
  return messageItem.messageUid || `${messageItem.createdTime}-${messageItem.content}`;
}

function runHasWaitingApprovalStep(messageItem: ConversationMessage) {
  if (!messageItem.messageUid) return false;
  const run = conversationRunsStore.runsByMessageUid[messageItem.messageUid];
  if (!run) return false;
  return run.steps.some((step) => step.status === "waiting_approval");
}

function runHasActiveApproval(messageItem: ConversationMessage) {
  if (!messageItem.messageUid) return false;
  const run = conversationRunsStore.runsByMessageUid[messageItem.messageUid];
  if (!run) return false;
  const waitingSteps = run.steps.filter((step) => step.status === "waiting_approval");
  if (!waitingSteps.length) return false;
  if (!conversationStore.approval.stepUid) return true;
  return run.steps.some(
    (step) => step.stepUid === conversationStore.approval.stepUid && step.status === "waiting_approval"
  );
}

async function copyAssistantMessage(messageItem: ConversationMessage) {
  const content = messageItem.content || "";
  if (!content.trim()) return;
  try {
    await window.navigator.clipboard.writeText(content);
    const key = messageActionKey(messageItem);
    copiedMessageMap.value = {
      ...copiedMessageMap.value,
      [key]: true
    };
    window.setTimeout(() => {
      copiedMessageMap.value = {
        ...copiedMessageMap.value,
        [key]: false
      };
    }, 1200);
  } catch {
    discreteMessage.error("复制失败，请重试");
  }
}

async function saveJinnang(messageItem: ConversationMessage) {
  const key = messageActionKey(messageItem);
  if (savingTipMap.value[key]) return;
  const conversationAgentUid = conversationStore.conversations.find((item) => item.conversationUid === conversationStore.currentConversationUid)?.agentUid || "";
  const targetAgentUid = (conversationAgentUid || agentCatalogStore.selectedAgentUid || "").trim();
  if (!targetAgentUid) {
    discreteMessage.warning("当前未选择可归属的 Agent，无法保存锦囊。");
    return;
  }
  savingTipMap.value = {
    ...savingTipMap.value,
    [key]: true
  };
  let saved = false;
  try {
    await jinnangStore.addTip({
      agentUid: targetAgentUid,
      messageUid: messageItem.messageUid,
      content: messageItem.content,
      sourceTime: messageItem.createdTime,
      conversationUid: conversationStore.currentConversationUid || ""
    });
    saved = true;
  } catch {
    saved = false;
  } finally {
    savingTipMap.value = {
      ...savingTipMap.value,
      [key]: false
    };
  }
  if (!saved) {
    return;
  }
  savedTipMap.value = {
    ...savedTipMap.value,
    [key]: true
  };
  window.setTimeout(() => {
    savedTipMap.value = {
      ...savedTipMap.value,
      [key]: false
    };
  }, 1400);
}

function formatAttachmentSize(sizeBytes = 0) {
  if (sizeBytes >= 1024 * 1024) {
    return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MB`;
  }
  if (sizeBytes >= 1024) {
    return `${Math.round(sizeBytes / 1024)} KB`;
  }
  return `${sizeBytes} B`;
}

function isImageAttachment(contentType?: string, mimeGroup?: string) {
  return mimeGroup === "image" || Boolean(contentType?.startsWith("image/"));
}

function openAttachment(fileUrl: string) {
  window.open(fileUrl, "_blank", "noopener,noreferrer");
}

function resolveMessageTokenUsage(messageItem: ConversationMessage) {
  const input = Number(messageItem.inputTokens || 0);
  const output = Number(messageItem.outputTokens || 0);
  const total = Number(messageItem.totalTokens || 0);
  if (input > 0 || output > 0 || total > 0) {
    return { input, output, total };
  }
  if (messageItem.role === "assistant" && messageItem.parentMessageUid) {
    const parent = conversationStore.messages.find((item) => item.messageUid === messageItem.parentMessageUid);
    if (!parent) return null;
    const parentInput = Number(parent.inputTokens || 0);
    const parentOutput = Number(parent.outputTokens || 0);
    const parentTotal = Number(parent.totalTokens || 0);
    if (parentInput > 0 || parentOutput > 0 || parentTotal > 0) {
      return {
        input: parentInput,
        output: parentOutput,
        total: parentTotal
      };
    }
  }
  return null;
}

function messageTokenUsageText(messageItem: ConversationMessage) {
  const usage = resolveMessageTokenUsage(messageItem);
  if (!usage) return "";
  return `Total ${usage.total}`;
}
</script>

<template>
  <section class="panel message-panel">
    <div class="panel-header message-panel-header">
      <div class="panel-title">对话区</div>
      <button class="conversation-badge" :disabled="!conversationStore.currentConversationUid" @click="copyConversationUid">
        {{ conversationStore.currentConversationUid || "Conversation" }}
      </button>
    </div>

    <div class="panel-body message-list scroll-area">
      <div
        v-if="!conversationStore.messages.length && conversationStore.runningConversationUid !== conversationStore.currentConversationUid"
        class="empty-state conversation-empty"
      >
        当前对话暂无消息。
      </div>
      <template v-else>
        <div
          v-for="message in conversationStore.messages"
          :key="`${message.createdTime}-${message.messageUid || message.content}`"
          class="message-wrap"
          :class="{ user: message.role === 'user' }"
        >
          <div class="message-role">{{ message.role === "user" ? "YOU" : "ASSISTANT" }}</div>
          <div class="message-bubble" :class="{ user: message.role === 'user' }">
            <div class="message-html" v-html="renderMarkdown(message.content)" />
            <div v-if="message.attachments?.length" class="message-attachments">
              <button
                v-for="attachment in message.attachments"
                :key="attachment.fileUrl"
                type="button"
                class="message-attachment-card"
                @click="openAttachment(attachment.fileUrl)"
              >
                <img
                  v-if="isImageAttachment(attachment.contentType, attachment.mimeGroup)"
                  :src="attachment.fileUrl"
                  :alt="attachment.name"
                  class="message-attachment-image"
                />
                <div v-else class="message-attachment-file">FILE</div>
                <div class="message-attachment-meta">
                  <div class="message-attachment-name">{{ attachment.name }}</div>
                  <div class="message-attachment-subtitle">
                    {{ attachment.mimeGroup }} · {{ formatAttachmentSize(attachment.sizeBytes) }}
                  </div>
                </div>
              </button>
            </div>
            <n-flex v-if="message.fileLinks?.length" wrap :size="8" class="message-file-links">
              <n-button
                v-for="fileLink in message.fileLinks"
                :key="fileLink.path"
                size="small"
                secondary
                @click="conversationStore.openFile(fileLink.path)"
              >
                打开 {{ fileLink.name }}
              </n-button>
            </n-flex>
          </div>
          <div v-if="message.role !== 'user'" class="message-meta">
            <div class="message-time">{{ formatMessageTime(message.createdTime) }}</div>
            <div v-if="resolveMessageTokenUsage(message)" class="message-token-inline">
              <span class="message-token-item">
                <ArrowDown :size="12" />
                <span>{{ resolveMessageTokenUsage(message)?.input ?? 0 }}</span>
              </span>
              <span class="message-token-item">
                <ArrowUp :size="12" />
                <span>{{ resolveMessageTokenUsage(message)?.output ?? 0 }}</span>
              </span>
              <span class="message-token-total">{{ messageTokenUsageText(message) }}</span>
            </div>
            <div class="message-actions">
              <button
                class="message-action-btn icon-only"
                :class="{ copied: copiedMessageMap[messageActionKey(message)] }"
                type="button"
                title="复制"
                aria-label="复制"
                @click="copyAssistantMessage(message)"
              >
                <Check v-if="copiedMessageMap[messageActionKey(message)]" :size="14" />
                <Copy v-else :size="14" />
              </button>
              <div class="save-tip-wrap">
                <n-popconfirm
                  :show-icon="false"
                  :disabled="savingTipMap[messageActionKey(message)]"
                  positive-text="确定"
                  negative-text="取消"
                  @positive-click="saveJinnang(message)"
                >
                  <template #trigger>
                    <button
                      class="message-action-btn icon-only"
                      :class="{
                        loading: savingTipMap[messageActionKey(message)],
                        saved: savedTipMap[messageActionKey(message)]
                      }"
                      :disabled="savingTipMap[messageActionKey(message)]"
                      type="button"
                      title="保存为锦囊"
                      aria-label="保存为锦囊"
                    >
                      <Check v-if="savedTipMap[messageActionKey(message)] && !savingTipMap[messageActionKey(message)]" :size="14" />
                      <Sparkles v-else-if="!savingTipMap[messageActionKey(message)]" :size="14" />
                    </button>
                  </template>
                  保存为锦囊？
                </n-popconfirm>
                <span v-if="savedTipMap[messageActionKey(message)]" class="message-action-hint">已保存为锦囊</span>
              </div>
            </div>
          </div>
          <div v-else class="message-time user-message-time">{{ formatMessageTime(message.createdTime) }}</div>

          <n-card
            v-if="message.role === 'user' && message.messageUid && conversationRunsStore.runsByMessageUid[message.messageUid]"
            size="small"
            class="run-card"
            embedded
          >
            <n-collapse>
              <n-collapse-item :name="`run-${message.messageUid}`">
                <template #header>
                  <div class="run-title">执行过程</div>
                </template>
                <template #header-extra>
                  <n-tag size="small" :type="runTone(conversationRunsStore.runsByMessageUid[message.messageUid].status)">
                    {{ conversationRunsStore.runsByMessageUid[message.messageUid].status }}
                  </n-tag>
                </template>
                <div class="run-summary">{{ conversationRunsStore.runsByMessageUid[message.messageUid].summary }}</div>
                <n-collapse class="run-steps-collapse">
                  <n-collapse-item
                    v-for="(step, stepPosition) in conversationRunsStore.runsByMessageUid[message.messageUid].steps"
                    :key="step.stepUid"
                    :title="`${stepPosition + 1}. ${step.displayTitle}`"
                    :name="step.stepUid"
                  >
                    <template #header-extra>
                      <n-tag size="small" :type="runTone(step.status)">{{ step.status }}</n-tag>
                    </template>
                    <div class="run-details">{{ step.displayDetails || step.displaySummary || "无附加详情" }}</div>
                  </n-collapse-item>
                </n-collapse>
              </n-collapse-item>
            </n-collapse>
          </n-card>
          <ApprovalBanner
            v-if="runHasActiveApproval(message)"
            :force-visible="runHasWaitingApprovalStep(message)"
            class="run-approval-banner"
          />
        </div>

        <div
          v-if="conversationStore.currentConversationUid
            && conversationStore.runningConversationUid === conversationStore.currentConversationUid
            && !conversationRunsStore.runsByMessageUid[latestMessageUid]"
          class="message-wrap"
        >
          <div class="message-role">ASSISTANT</div>
          <div class="typing-indicator">
            <span v-for="index in 3" :key="index" />
          </div>
        </div>
      </template>
    </div>
    <ComposerPanel />
  </section>
</template>

<style scoped>
.message-panel {
  display: flex;
  min-height: 0;
  flex-direction: column;
}

.message-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  padding: var(--space-5) var(--space-6);
  border-bottom: var(--size-1) solid var(--color-border-soft);
}

.message-list {
  flex: 1;
}

.conversation-badge {
  padding: var(--space-1) var(--space-3);
  border: 0;
  border-radius: var(--radius-pill);
  background: var(--color-bg-soft-hover);
  color: var(--color-text-subtle);
  font-size: var(--font-size-xs);
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.conversation-badge:hover {
  background: var(--color-bg-soft-active);
}

.conversation-empty {
  margin-top: var(--space-1_5);
}

.message-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  margin-bottom: var(--space-4_5);
}

.message-wrap.user {
  align-items: flex-end;
}

.message-role {
  margin-bottom: var(--space-2);
  font-size: var(--font-size-2xs);
  font-weight: 700;
  letter-spacing: 0.22em;
  color: var(--text-muted);
}

.message-bubble {
  max-width: 92%;
  padding: var(--space-4_5) var(--space-5);
  border-radius: var(--radius-xl) var(--radius-xl) var(--radius-xl) var(--radius-s-md);
  background: var(--color-bg-canvas);
  line-height: 1.75;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.message-bubble.user {
  border-radius: var(--radius-xl) var(--radius-xl) var(--radius-s-md) var(--radius-xl);
  background: var(--color-accent-brand);
  color: var(--color-text-inverse);
}

.message-bubble .message-html {
  overflow-wrap: anywhere;
  word-break: break-word;
}

.message-attachments {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.message-attachment-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  width: 100%;
  padding: var(--space-3);
  border: var(--size-1) solid color-mix(in srgb, var(--color-border-soft) 88%, transparent);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--color-bg-surface-soft) 92%, white);
  text-align: left;
  cursor: pointer;
}

.message-attachment-image,
.message-attachment-file {
  width: var(--size-48);
  height: var(--size-48);
  border-radius: var(--radius-lg);
  flex: none;
}

.message-attachment-image {
  object-fit: cover;
}

.message-attachment-file {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-brand-soft);
  color: var(--color-text-brand);
  font-size: var(--font-size-2xs);
  font-weight: 800;
  letter-spacing: 0.08em;
}

.message-attachment-meta {
  min-width: 0;
}

.message-attachment-name {
  font-size: var(--font-size-sm);
  font-weight: 700;
  color: inherit;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.message-attachment-subtitle {
  margin-top: var(--space-1);
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.message-time {
  font-size: var(--font-size-2xs);
  color: var(--text-muted);
}

.user-message-time {
  width: min(var(--size-percent-message-max), var(--size-980));
  margin-top: var(--space-1);
  padding-right: var(--space-1_5);
  text-align: right;
}

.message-meta {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: var(--space-3);
  width: min(var(--size-percent-message-max), var(--size-980));
  margin-top: var(--space-1);
  opacity: 0;
  visibility: hidden;
  pointer-events: none;
  transform: translateY(calc(var(--size-1) * -1));
  transition: opacity 0.14s ease, transform 0.14s ease, visibility 0.14s ease;
}

.message-wrap:hover .message-meta,
.message-wrap:focus-within .message-meta {
  opacity: 1;
  visibility: visible;
  pointer-events: auto;
  transform: translateY(0);
}

.message-actions {
  display: flex;
  gap: var(--space-2);
}

@media (hover: none) {
  .message-meta {
    opacity: 1;
    visibility: visible;
    pointer-events: auto;
    transform: none;
  }
}

.message-token-inline {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  color: var(--color-text-cool-gray);
  font-size: var(--font-size-2xs);
  line-height: 1;
}

.message-token-item {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
}

.message-token-total {
  color: var(--text-muted);
}

.message-action-btn {
  border: var(--size-1) solid var(--color-border-ghost);
  border-radius: var(--radius-pill);
  padding: var(--space-1) var(--space-2_5);
  background: var(--color-bg-overlay-light);
  color: var(--color-text-subtle);
  font-size: var(--font-size-2xs);
  line-height: 1.2;
  cursor: pointer;
  transition: all 0.16s ease;
}

.message-action-btn.icon-only {
  width: var(--size-24);
  height: var(--size-24);
  padding: 0;
  border: none;
  background: transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-pill);
}

.message-action-btn.icon-only:hover {
  border-color: transparent;
  background: var(--color-bg-soft-hover);
}

.message-action-btn:hover {
  border-color: var(--color-border-brand-hover);
  background: var(--color-bg-brand-tint-12);
  color: var(--color-text-brand-strong);
}

.message-action-btn:disabled {
  cursor: not-allowed;
  opacity: 0.74;
}

.message-action-btn.copied {
  border-color: var(--color-border-brand-strong);
  background: var(--color-bg-brand-tint-14);
  color: var(--color-text-brand-strong);
  animation: copied-flash 0.25s ease-out;
}

.message-action-btn.icon-only.copied,
.message-action-btn.icon-only.saved,
.message-action-btn.icon-only.loading {
  border-color: transparent;
}

.message-action-btn.loading {
  border-color: var(--color-border-brand-hover);
  color: var(--color-text-brand-strong);
}

.message-action-btn.loading::before {
  content: "";
  display: inline-block;
  width: var(--space-2_5);
  height: var(--space-2_5);
  margin-right: var(--space-1_5);
  border: var(--size-1_5) solid var(--color-border-spinner);
  border-top-color: var(--color-text-brand-strong);
  border-radius: var(--radius-pill);
  vertical-align: calc(var(--size-1) * -1);
  animation: spin 0.65s linear infinite;
}

.message-action-btn.icon-only.loading::before {
  margin-right: 0;
}

.message-action-btn.saved {
  border-color: var(--color-border-spinner-soft);
  background: var(--color-bg-brand-tint-14);
  color: var(--color-text-brand-strong);
}

.save-tip-wrap {
  position: relative;
  display: inline-flex;
}

.message-action-hint {
  position: absolute;
  right: 0;
  bottom: calc(100% + var(--space-1_5));
  white-space: nowrap;
  font-size: var(--font-size-2xs);
  color: var(--color-text-brand-strong);
  background: var(--color-bg-brand-tint-12);
  border: var(--size-1) solid var(--color-border-accent-soft);
  border-radius: var(--radius-pill);
  padding: var(--size-3) var(--space-2);
  box-shadow: var(--shadow-soft-md);
  animation: hint-in 0.16s ease-out;
}

@keyframes hint-in {
  from {
    opacity: 0;
    transform: translateY(var(--size-3));
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes copied-flash {
  0% {
    transform: scale(0.96);
  }
  100% {
    transform: scale(1);
  }
}

.run-card {
  width: min(var(--size-percent-message-max), var(--size-980));
  margin-top: var(--space-3_5);
}

.run-title {
  font-size: var(--font-size-sm);
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.run-summary {
  margin-top: var(--space-1_5);
  line-height: 1.7;
}

.run-details {
  white-space: pre-wrap;
  line-height: 1.7;
}

.typing-indicator {
  display: flex;
  gap: var(--space-2);
  padding: var(--space-4_5) var(--space-5);
  border-radius: var(--radius-xl) var(--radius-xl) var(--radius-xl) var(--radius-s-md);
  background: var(--color-bg-canvas);
}

.typing-indicator span {
  width: var(--space-2_5);
  height: var(--space-2_5);
  border-radius: var(--radius-pill);
  background: var(--color-indicator-muted);
  animation: pulse 1.2s ease-in-out infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.18s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.36s;
}

@keyframes pulse {
  0%, 80%, 100% {
    opacity: 0.35;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(calc(var(--size-3) * -1));
  }
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: var(--size-breakpoint-lg)) {
  .message-list {
    padding-bottom: calc(var(--size-190) + env(safe-area-inset-bottom));
    -webkit-overflow-scrolling: touch;
  }
}

.message-file-links {
  margin-top: var(--space-3);
}

.run-steps-collapse {
  margin-top: var(--space-3_5);
}

.run-approval-banner {
  margin-top: var(--space-3);
}
</style>
