<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { Copy } from "lucide-vue-next";
import { NCard, NCollapse, NCollapseItem, NEmpty, NSpin, NTag } from "naive-ui";
import { useRoute } from "vue-router";
import { conversationApi } from "@/api/conversationApi";
import { message as discreteMessage } from "@/discrete";
import { copyText } from "@/utils/clipboard";
import TraceJsonViewer from "@/components/chat/TraceJsonViewer.vue";
import type { ConversationMessage, LlmTraceDetail, LlmTraceSummary } from "@/types/api";

const route = useRoute();
const { t } = useI18n();
const traces = ref<LlmTraceSummary[]>([]);
const selected = ref<LlmTraceDetail | null>(null);
const message = ref<ConversationMessage | null>(null);
const loading = ref(false);
const detailLoading = ref(false);
const error = ref("");
const conversationUid = computed(() => String(route.query.conversationUid || "").trim());
const messageUid = computed(() => String(route.query.messageUid || "").trim());
const selectedSummary = computed(() => traces.value.find((item) => item.traceUid === selected.value?.traceUid) || null);

onMounted(() => void load());
watch(() => [conversationUid.value, messageUid.value], () => void load());

async function load() {
  if (!conversationUid.value || !messageUid.value) {
    error.value = t("chat.trace.invalidLink");
    return;
  }
  loading.value = true;
  error.value = "";
  try {
    const [items, messages] = await Promise.all([
      conversationApi.listLlmTraces(conversationUid.value, messageUid.value),
      conversationApi.listMessages(conversationUid.value)
    ]);
    traces.value = items;
    message.value = messages.find((item) => item.messageUid === messageUid.value) || null;
    const preferred = [...items].reverse().find((item) => item.status === "SUCCEEDED") || items[items.length - 1];
    if (preferred) await loadDetail(preferred.traceUid);
    else selected.value = null;
  } catch {
    traces.value = [];
    selected.value = null;
    error.value = t("chat.trace.loadFailed");
  } finally {
    loading.value = false;
  }
}

async function loadDetail(traceUid: string) {
  detailLoading.value = true;
  try {
    selected.value = await conversationApi.getLlmTrace(conversationUid.value, traceUid);
  } catch {
    discreteMessage.error(t("chat.trace.detailFailed"));
  } finally {
    detailLoading.value = false;
  }
}

function statusType(status: string) {
  return status === "SUCCEEDED" ? "success" : status === "FAILED" ? "error" : "warning";
}

function formatTime(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "medium",
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone
  }).format(date);
}

function tokenText(item: LlmTraceSummary) {
  return item.usageAvailable ? `${item.totalTokens.toLocaleString()} ${t("chat.trace.tokens")}` : t("chat.trace.unavailable");
}

function prettyJson(value: string) {
  if (!value) return "";
  try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; }
}

async function copy(value: string) {
  try {
    await copyText(value);
    discreteMessage.success(t("chat.trace.copied"));
  } catch {
    discreteMessage.error(t("chat.messages.copyFailed"));
  }
}

</script>

<template>
  <div class="trace-page-shell">
    <main class="trace-page">
      <!-- header intentionally omitted: trace is a full-screen two-pane view -->
      <template v-if="false">
      <header class="trace-page-header">
        <div>
          <div class="trace-heading">
            <h1>{{ t("chat.trace.title") }}</h1>
            <p>{{ message?.content || t("chat.trace.messageUnavailable") }}</p>
          </div>
        </div>
      </header>
      </template>

      <div v-if="error" class="trace-error">{{ error }}</div>
      <n-spin v-else-if="loading" class="trace-loading" />
      <n-empty v-else-if="!traces.length" :description="t('chat.trace.empty')" class="trace-empty" />
      <section v-else class="trace-layout">
        <n-card class="trace-timeline-card" :title="t('chat.trace.timeline')">
          <div class="trace-timeline-scroll">
            <button
              v-for="item in traces"
              :key="item.traceUid"
              class="trace-timeline-item"
              :class="{ selected: item.traceUid === selected?.traceUid }"
              type="button"
              @click="loadDetail(item.traceUid)"
            >
              <div class="trace-item-topline">
                <span>{{ item.scene }}</span>
                <n-tag size="small" :type="statusType(item.status)">{{ item.status }}
                </n-tag>
              </div>
              <div class="trace-item-title">{{ t("chat.trace.roundAttempt", { round: item.roundIndex, attempt: item.attemptIndex }) }}</div>
              <div class="trace-item-meta"><span class="trace-model-label">{{ t("chat.trace.model") }}</span> {{ item.provider }} / {{ item.modelName }}</div>
              <div class="trace-item-meta">{{ item.latencyMs }}ms · {{ tokenText(item) }} · {{ formatTime(item.requestStartedTime) }}</div>
            </button>
          </div>
        </n-card>

        <n-card class="trace-detail-card" :title="t('chat.trace.detail')">
          <div class="trace-detail-scroll">
            <n-spin v-if="detailLoading" />
            <template v-else-if="selected">
              <div class="trace-detail-overview">
                <n-tag :type="statusType(selected.status)">{{ selected.status }}
                </n-tag>
                <span><strong>{{ t("chat.trace.provider") }}</strong> {{ selected.provider }}</span>
                <span><strong>{{ t("chat.trace.model") }}</strong> {{ selected.modelName }}</span>
                <span>{{ selected.latencyMs }}ms</span>
                <span>{{ tokenText(selectedSummary || selected) }}</span>
              </div>
              <n-collapse multiple :default-expanded-names="['protocol', 'overview', 'response']">
              <n-collapse-item name="protocol" :title="t('chat.trace.rawProtocol')">
                <div v-if="selected.rawRequestJson || selected.rawResponseJson || selected.rawStreamEvents" class="trace-protocol">
                  <div class="trace-kv-grid">
                    <span>{{ t('chat.trace.protocol') }}</span><strong>{{ selected.protocolType || '—' }}</strong>
                    <span>{{ t('chat.trace.endpoint') }}</span><strong>{{ selected.requestMethod }} {{ selected.requestUrl }}</strong>
                    <span>{{ t('chat.trace.responseStatus') }}</span><strong>{{ selected.responseStatus ?? '—' }}</strong>
                  </div>
                  <div class="trace-subsection"><h3>{{ t('chat.trace.requestHeaders') }}</h3><div class="trace-code-wrap trace-request-body"><button class="trace-copy" @click="copy(prettyJson(selected.requestHeaders))"><Copy :size="14" /></button><TraceJsonViewer :raw-text="selected.requestHeaders" /></div></div>
                  <div class="trace-subsection"><h3>{{ t('chat.trace.rawRequestJson') }}</h3><div class="trace-code-wrap trace-request-body"><button class="trace-copy" @click="copy(prettyJson(selected.rawRequestJson))"><Copy :size="14" /></button><TraceJsonViewer :raw-text="selected.rawRequestJson" /></div></div>
                  <div class="trace-subsection"><h3>{{ t('chat.trace.rawResponseJson') }}</h3><div class="trace-code-wrap trace-response-body"><button class="trace-copy" @click="copy(prettyJson(selected.rawResponseJson))"><Copy :size="14" /></button><TraceJsonViewer :raw-text="selected.rawResponseJson" /></div></div>
                  <div class="trace-subsection"><h3>{{ t('chat.trace.streamEvents') }}</h3><div v-if="selected.rawStreamEvents" class="trace-code-wrap trace-response-body"><button class="trace-copy" @click="copy(prettyJson(selected.rawStreamEvents))"><Copy :size="14" /></button><TraceJsonViewer :raw-text="selected.rawStreamEvents" /></div><span v-else class="trace-empty-response">{{ t('chat.trace.nonStreaming') }}</span></div>
                </div>
                <div v-else class="trace-empty-response">{{ t('chat.trace.rawUnavailable') }}</div>
              </n-collapse-item>
              <n-collapse-item name="overview" :title="t('chat.trace.overview')">
                <div class="trace-kv-grid">
                  <span>{{ t("chat.trace.requestTime") }}</span><strong>{{ formatTime(selected.requestStartedTime) }}</strong>
                  <span>{{ t("chat.trace.responseTime") }}</span><strong>{{ formatTime(selected.responseFinishedTime) }}</strong>
                  <span>{{ t("chat.trace.requestUid") }}</span><strong>{{ selected.requestUid }}</strong>
                  <span>{{ t("chat.trace.roundAttemptLabel") }}</span><strong>{{ selected.roundIndex }} / {{ selected.attemptIndex }}</strong>
                </div>
              </n-collapse-item>
              <n-collapse-item name="prompt" :title="t('chat.trace.systemPrompt')"><div class="trace-code-wrap trace-request-body"><button class="trace-copy" @click="copy(selected.systemPrompt)"><Copy :size="14" /></button><pre>{{ selected.systemPrompt || "—" }}</pre></div></n-collapse-item>
              <n-collapse-item name="tools" :title="t('chat.trace.tools')"><div class="trace-request-body"><TraceJsonViewer :raw-text="selected.toolSpecifications" /></div></n-collapse-item>
              <n-collapse-item name="messages" :title="t('chat.trace.messages')"><div class="trace-code-wrap trace-request-body"><button class="trace-copy" @click="copy(prettyJson(selected.requestMessages))"><Copy :size="14" /></button><TraceJsonViewer :raw-text="selected.requestMessages" /></div></n-collapse-item>
              <n-collapse-item name="choice" :title="t('chat.trace.toolChoice')"><div class="trace-request-body"><TraceJsonViewer :raw-text="selected.toolChoice" /></div></n-collapse-item>
              <n-collapse-item name="response" :title="t('chat.trace.response')">
                <div v-if="selected.responseContent" class="trace-response-section">
                  <h3>{{ t("chat.trace.assistantContent") }}</h3>
                  <div class="trace-code-wrap trace-response-body"><button class="trace-copy" @click="copy(selected.responseContent)"><Copy :size="14" /></button><pre>{{ selected.responseContent }}</pre></div>
                </div>
                <div v-if="selected.responseThinking" class="trace-response-section">
                  <h3>{{ t("chat.trace.thinking") }}</h3>
                  <div class="trace-code-wrap trace-thinking-body"><button class="trace-copy" @click="copy(selected.responseThinking)"><Copy :size="14" /></button><pre>{{ selected.responseThinking }}</pre></div>
                </div>
                <div v-if="selected.responseToolCalls" class="trace-response-section"><h3>{{ t("chat.trace.toolCalls") }}</h3><div class="trace-response-body"><TraceJsonViewer :raw-text="selected.responseToolCalls" /></div></div>
                <div v-if="!selected.responseContent && !selected.responseThinking && !selected.responseToolCalls" class="trace-empty-response">{{ t("chat.trace.noResponse") }}</div>
              </n-collapse-item>
              <n-collapse-item name="raw" :title="t('chat.trace.rawRequest')"><div class="trace-request-body"><TraceJsonViewer :raw-text="selected.requestMetadata" /></div></n-collapse-item>
              <n-collapse-item v-if="selected.errorMessage" name="error" :title="t('chat.trace.error')"><pre class="trace-code trace-error-code">{{ selected.errorType }}\n{{ selected.errorMessage }}</pre></n-collapse-item>
              </n-collapse>
            </template>
          </div>
        </n-card>
      </section>
    </main>
  </div>
</template>

<style scoped>
.trace-page-shell { height: 100vh; overflow: hidden; background: #000; color: #f2f3f5; }
.trace-page { min-width: 0; height: 100%; box-sizing: border-box; }
.trace-page :deep(.n-card) { background: #101114; border-color: #2a2d33; color: #f2f3f5; }
.trace-page :deep(.n-card-header) { color: #f2f3f5; }
.trace-page :deep(.n-collapse-item__header) { color: #e5e7eb; }
.trace-page :deep(.n-button) { color: #f2f3f5; }
.trace-layout { display: grid; grid-template-columns: minmax(280px, 340px) minmax(0, 1fr); gap: 0; width: 100%; height: 100vh; min-height: 0; align-items: stretch; }
.trace-layout > * { min-width: 0; min-height: 0; }
.trace-page :deep(.n-card) { border-radius: 0; height: 100%; }
.trace-timeline-item { width: 100%; display: block; text-align: left; padding: 14px; margin-bottom: 8px; border: 1px solid transparent; border-radius: 10px; background: transparent; color: inherit; cursor: pointer; }
.trace-timeline-item:hover, .trace-timeline-item.selected { background: #17191d; border-color: #287d72; }
.trace-item-topline, .trace-detail-overview { display: flex; align-items: center; gap: 8px; }
.trace-item-topline { justify-content: space-between; font-size: 12px; color: #a5aab3; }
.trace-item-title { margin-top: 8px; font-weight: 650; }
.trace-item-meta { margin-top: 5px; font-size: 12px; color: #a5aab3; }
.trace-detail-overview { flex-wrap: wrap; margin-bottom: 18px; color: #c2c5ca; }
.trace-timeline-card { min-height: 0; height: 100%; }
.trace-timeline-card :deep(.n-card__content) { min-height: 0; overflow: hidden; }
.trace-timeline-scroll { height: calc(100vh - 72px); max-height: calc(100vh - 72px); margin-right: -24px; padding-right: 24px; box-sizing: border-box; overflow-y: auto; overflow-x: hidden; scrollbar-color: #4c515b #101114; }
.trace-detail-card { min-height: 100%; height: 100vh; overflow-y: auto; overflow-x: hidden; scrollbar-color: #4c515b #101114; }
.trace-detail-card :deep(.n-card__content) { min-height: 0; overflow: visible; }
.trace-detail-scroll { height: auto; max-height: none; overflow: visible; padding-right: 8px; }
.trace-kv-grid { display: grid; grid-template-columns: 150px 1fr; gap: 10px; font-size: 13px; }
.trace-kv-grid span { color: #a5aab3; }
.trace-code-wrap { position: relative; }
.trace-code, pre { margin: 0; max-height: 420px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace; }
.trace-code-wrap pre { padding-right: 34px; }
.trace-request-body, .trace-response-body { padding: 14px; border: 1px solid; border-radius: 8px; }
.trace-request-body { background: #171b22; border-color: #2b333f; }
.trace-response-body { background: #19211d; border-color: #334038; }
.trace-thinking-body { background: #211f1a; border-color: #464033; }
.trace-response-section + .trace-response-section { margin-top: 16px; }
.trace-response-section h3 { margin: 0 0 8px; color: #a5aab3; font-size: 12px; font-weight: 600; }
.trace-empty-response { color: #a5aab3; font-size: 13px; }
.trace-copy { position: absolute; top: 11px; right: 8px; border: 0; background: transparent; color: inherit; cursor: pointer; }
.trace-subsection h3 { margin: 18px 0 8px; font-size: 13px; }
.trace-error, .trace-error-code { color: #ff6b6b; }
.trace-loading, .trace-empty { display: grid; place-items: center; min-height: 300px; }
</style>
