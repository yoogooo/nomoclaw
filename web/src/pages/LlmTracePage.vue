<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { AlertTriangle, ChevronDown, Copy, Maximize2, RefreshCw } from "lucide-vue-next";
import { NCard, NCollapse, NCollapseItem, NEmpty, NModal, NSpin, NTag } from "naive-ui";
import { useRoute } from "vue-router";
import { conversationApi } from "@/api/conversationApi";
import { message as discreteMessage } from "@/discrete";
import { copyText } from "@/utils/clipboard";
import TraceJsonTree from "@/components/chat/TraceJsonTree.vue";
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
const rawRequestExpanded = ref(true);
const rawResponseExpanded = ref(true);
const rawStreamExpanded = ref(true);
const requestHeadersExpanded = ref(true);
const scrollHintVisible = ref<Record<string, boolean>>({});
const fullscreenLog = ref<{ title: string; content: string } | null>(null);
const fullscreenMode = ref<"raw" | "json">("raw");
const fullscreenVisible = computed({
  get: () => Boolean(fullscreenLog.value),
  set: (visible: boolean) => {
    if (!visible) closeFullscreen();
  }
});
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
    await nextTick();
    updateScrollHints();
  } catch {
    discreteMessage.error(t("chat.trace.detailFailed"));
  } finally {
    detailLoading.value = false;
  }
}

function updateScrollHint(key: string, selector: string) {
  const content = document.querySelector<HTMLElement>(selector);
  scrollHintVisible.value[key] = Boolean(content && content.scrollHeight > content.clientHeight + 1 && content.scrollTop + content.clientHeight < content.scrollHeight - 4);
}

function updateScrollHints() {
  updateScrollHint("systemPrompt", ".trace-system-prompt");
  updateScrollHint("responseContent", ".trace-response-content");
  updateScrollHint("responseThinking", ".trace-response-thinking");
}

function scheduleScrollHintCheck() {
  window.setTimeout(updateScrollHints, 180);
}

function onScrollHintScroll(key: string, event: Event) {
  const prompt = event.currentTarget as HTMLElement;
  scrollHintVisible.value[key] = prompt.scrollTop + prompt.clientHeight < prompt.scrollHeight - 4;
}

function openFullscreen(title: string, content: string) {
  fullscreenMode.value = "raw";
  fullscreenLog.value = { title, content: content || "—" };
}

function closeFullscreen() {
  fullscreenLog.value = null;
}

function statusType(status: string) {
  return status === "SUCCEEDED" ? "success" : status === "FAILED" ? "error" : "warning";
}

function statusDotClass(status: string) {
  return status === "SUCCEEDED" ? "success" : status === "FAILED" ? "error" : "warning";
}

function formatTimelineTime(value?: string | null) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (part: number) => String(part).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}

function tokenText(item: LlmTraceSummary) {
  return item.usageAvailable ? `${item.totalTokens.toLocaleString()} ${t("chat.trace.tokens")}` : t("chat.trace.unavailable");
}

function tokenValue(item: LlmTraceSummary) {
  return item.usageAvailable ? item.totalTokens.toLocaleString() : "—";
}

function tokenBreakdownValue(item: LlmTraceSummary, value: number) {
  return item.usageAvailable ? value.toLocaleString() : "—";
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

      <section v-if="error" class="trace-state-panel trace-error-state" role="alert">
        <div class="trace-state-icon"><AlertTriangle :size="22" /></div>
        <h2>{{ error }}</h2>
        <p>{{ t("chat.trace.loadFailedHint") }}</p>
        <button v-if="conversationUid && messageUid" type="button" class="trace-retry-button" @click="load">
          <RefreshCw :size="15" />
          <span>{{ t("chat.trace.retry") }}</span>
        </button>
      </section>
      <section v-else-if="loading" class="trace-state-panel trace-loading">
        <n-spin size="medium" />
        <p>{{ t("chat.trace.loading") }}</p>
      </section>
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
              <div class="trace-item-content">
                <span class="trace-status-dot" :class="statusDotClass(item.status)" :aria-label="item.status" />
                <div class="trace-item-details">
                  <div class="trace-item-title">{{ t("chat.trace.round", { round: item.roundIndex }) }}</div>
                  <div class="trace-item-meta">{{ item.provider }} / {{ item.modelName }}</div>
                  <div class="trace-item-meta">{{ item.latencyMs }}ms · {{ tokenText(item) }}</div>
                  <div class="trace-item-meta trace-item-time">{{ formatTimelineTime(item.requestStartedTime) }}</div>
                </div>
              </div>
            </button>
          </div>
        </n-card>

        <n-card class="trace-detail-card" :title="t('chat.trace.detail')">
          <div class="trace-detail-scroll">
            <n-spin v-if="detailLoading" />
            <template v-else-if="selected">
              <div class="trace-detail-overview">
                <div class="trace-summary-main">
                  <div class="trace-summary-identity">
                    <span>{{ selected.provider }}</span>
                    <span class="trace-summary-separator">/</span>
                    <strong>{{ selected.modelName }}</strong>
                  </div>
                  <n-tag size="small" :type="statusType(selected.status)">{{ selected.status }}
                  </n-tag>
                </div>
                <div class="trace-summary-metrics">
                  <div class="trace-summary-metric">
                    <span>{{ t("chat.trace.latency") }}</span>
                    <strong>{{ selected.latencyMs }}<small>ms</small></strong>
                  </div>
                  <div class="trace-summary-metric">
                    <span>{{ t("chat.trace.tokens") }}</span>
                    <strong>{{ tokenValue(selectedSummary || selected) }}</strong>
                  </div>
                  <div class="trace-summary-metric">
                    <span>{{ t("chat.trace.inputTokens") }}</span>
                    <strong>{{ tokenBreakdownValue(selected, selected.inputTokens) }}</strong>
                  </div>
                  <div class="trace-summary-metric">
                    <span>{{ t("chat.trace.cachedInputTokens") }}</span>
                    <strong>{{ tokenBreakdownValue(selected, selected.cachedInputTokens) }}</strong>
                  </div>
                  <div class="trace-summary-metric">
                    <span>{{ t("chat.trace.outputTokens") }}</span>
                    <strong>{{ tokenBreakdownValue(selected, selected.outputTokens) }}</strong>
                  </div>
                  <div class="trace-summary-metric">
                    <span>{{ t("chat.trace.reasoningTokens") }}</span>
                    <strong>{{ tokenBreakdownValue(selected, selected.reasoningTokens) }}</strong>
                  </div>
                </div>
              </div>
              <n-collapse multiple :default-expanded-names="['protocol', 'overview', 'response']">
              <n-collapse-item name="overview" :title="t('chat.trace.overview')">
                <div class="trace-kv-grid">
                  <span>{{ t("chat.trace.requestTime") }}</span><strong>{{ formatTimelineTime(selected.requestStartedTime) }}</strong>
                  <span>{{ t("chat.trace.responseTime") }}</span><strong>{{ formatTimelineTime(selected.responseFinishedTime) }}</strong>
                  <span>{{ t("chat.trace.requestUid") }}</span><strong>{{ selected.requestUid }}</strong>
                  <span>{{ t("chat.trace.roundAttemptLabel") }}</span><strong>{{ selected.roundIndex }} / {{ selected.attemptIndex }}</strong>
                </div>
              </n-collapse-item>
              <n-collapse-item name="protocol" :title="t('chat.trace.rawProtocol')">
                <div v-if="selected.rawRequestJson || selected.rawResponseJson || selected.rawStreamEvents" class="trace-protocol">
                  <div class="trace-kv-grid">
                    <span>{{ t('chat.trace.protocol') }}</span><strong>{{ selected.protocolType || '—' }}</strong>
                    <span>{{ t('chat.trace.endpoint') }}</span><strong>{{ selected.requestMethod }} {{ selected.requestUrl }}</strong>
                    <span>{{ t('chat.trace.responseStatus') }}</span><strong>{{ selected.responseStatus ?? '—' }}</strong>
                  </div>
                  <div class="trace-subsection">
                    <button
                      class="trace-subsection-toggle"
                      type="button"
                      :aria-expanded="requestHeadersExpanded"
                      @click="requestHeadersExpanded = !requestHeadersExpanded"
                    >
                      <ChevronDown :size="16" class="trace-subsection-chevron" :class="{ collapsed: !requestHeadersExpanded }" />
                      <h3>{{ t('chat.trace.requestHeaders') }}</h3>
                    </button>
                    <div v-if="requestHeadersExpanded" class="trace-code-wrap trace-request-body">
                      <button class="trace-copy" @click="copy(prettyJson(selected.requestHeaders))"><Copy :size="14" /></button>
                      <TraceJsonViewer :raw-text="selected.requestHeaders" :title="t('chat.trace.requestHeaders')" :has-external-copy="true" :show-fullscreen="false" />
                    </div>
                  </div>
                  <div class="trace-subsection">
                    <button
                      class="trace-subsection-toggle"
                      type="button"
                      :aria-expanded="rawRequestExpanded"
                      @click="rawRequestExpanded = !rawRequestExpanded"
                    >
                      <ChevronDown :size="16" class="trace-subsection-chevron" :class="{ collapsed: !rawRequestExpanded }" />
                      <h3>{{ t('chat.trace.rawRequestJson') }}</h3>
                    </button>
                    <div v-if="rawRequestExpanded" class="trace-code-wrap trace-request-body">
                      <button class="trace-copy" @click="copy(prettyJson(selected.rawRequestJson))"><Copy :size="14" /></button>
                      <button class="trace-fullscreen" :title="t('chat.trace.fullscreen')" @click.stop="openFullscreen(t('chat.trace.rawRequestJson'), selected.rawRequestJson)"><Maximize2 :size="14" /></button>
                      <TraceJsonViewer :raw-text="selected.rawRequestJson" :title="t('chat.trace.rawRequestJson')" :has-external-copy="true" :show-fullscreen="false" />
                    </div>
                  </div>
                  <div class="trace-subsection">
                    <button
                      class="trace-subsection-toggle"
                      type="button"
                      :aria-expanded="rawResponseExpanded"
                      @click="rawResponseExpanded = !rawResponseExpanded"
                    >
                      <ChevronDown :size="16" class="trace-subsection-chevron" :class="{ collapsed: !rawResponseExpanded }" />
                      <h3>{{ t('chat.trace.rawResponseJson') }}</h3>
                    </button>
                    <div v-if="rawResponseExpanded" class="trace-code-wrap trace-response-body">
                      <button class="trace-copy" @click="copy(prettyJson(selected.rawResponseJson))"><Copy :size="14" /></button>
                      <button class="trace-fullscreen" :title="t('chat.trace.fullscreen')" @click.stop="openFullscreen(t('chat.trace.rawResponseJson'), selected.rawResponseJson)"><Maximize2 :size="14" /></button>
                      <TraceJsonViewer :raw-text="selected.rawResponseJson" :title="t('chat.trace.rawResponseJson')" :has-external-copy="true" :show-fullscreen="false" />
                    </div>
                  </div>
                  <div class="trace-subsection">
                    <button
                      class="trace-subsection-toggle"
                      type="button"
                      :aria-expanded="rawStreamExpanded"
                      @click="rawStreamExpanded = !rawStreamExpanded"
                    >
                      <ChevronDown :size="16" class="trace-subsection-chevron" :class="{ collapsed: !rawStreamExpanded }" />
                      <h3>{{ t('chat.trace.streamEvents') }}</h3>
                    </button>
                    <div v-if="rawStreamExpanded && selected.rawStreamEvents" class="trace-code-wrap trace-response-body">
                      <button class="trace-copy" @click="copy(prettyJson(selected.rawStreamEvents))"><Copy :size="14" /></button>
                      <button class="trace-fullscreen" :title="t('chat.trace.fullscreen')" @click.stop="openFullscreen(t('chat.trace.streamEvents'), selected.rawStreamEvents)"><Maximize2 :size="14" /></button>
                      <TraceJsonViewer :raw-text="selected.rawStreamEvents" :title="t('chat.trace.streamEvents')" :has-external-copy="true" :show-fullscreen="false" />
                    </div>
                    <span v-else-if="rawStreamExpanded" class="trace-empty-response">{{ t('chat.trace.nonStreaming') }}</span>
                  </div>
                </div>
                <div v-else class="trace-empty-response">{{ t('chat.trace.rawUnavailable') }}</div>
              </n-collapse-item>
              <n-collapse-item name="prompt" :title="t('chat.trace.systemPrompt')" @click="scheduleScrollHintCheck"><div class="trace-code-wrap trace-request-body trace-prompt-wrap" :class="{ 'has-more': scrollHintVisible.systemPrompt }"><button class="trace-copy" @click="copy(selected.systemPrompt)"><Copy :size="14" /></button><button class="trace-fullscreen" :title="t('chat.trace.fullscreen')" @click.stop="openFullscreen(t('chat.trace.systemPrompt'), selected.systemPrompt)"><Maximize2 :size="14" /></button><pre class="trace-system-prompt" @scroll="onScrollHintScroll('systemPrompt', $event)">{{ selected.systemPrompt || "—" }}</pre><div v-if="scrollHintVisible.systemPrompt" class="trace-scroll-hint"><ChevronDown :size="16" /><span>{{ t('chat.trace.scrollForMore') }}</span></div></div></n-collapse-item>
              <n-collapse-item name="tools" :title="t('chat.trace.tools')"><div class="trace-request-body"><TraceJsonViewer :raw-text="selected.toolSpecifications" :title="t('chat.trace.tools')" @fullscreen="openFullscreen(t('chat.trace.tools'), $event)" /></div></n-collapse-item>
              <n-collapse-item name="choice" :title="t('chat.trace.toolChoice')"><div class="trace-request-body"><TraceJsonViewer :raw-text="selected.toolChoice" :title="t('chat.trace.toolChoice')" @fullscreen="openFullscreen(t('chat.trace.toolChoice'), $event)" /></div></n-collapse-item>
              <n-collapse-item name="messages" :title="t('chat.trace.messages')"><div class="trace-code-wrap trace-request-body"><button class="trace-copy" @click="copy(prettyJson(selected.requestMessages))"><Copy :size="14" /></button><button class="trace-fullscreen" :title="t('chat.trace.fullscreen')" @click.stop="openFullscreen(t('chat.trace.messages'), selected.requestMessages)"><Maximize2 :size="14" /></button><TraceJsonViewer :raw-text="selected.requestMessages" :title="t('chat.trace.messages')" :has-external-copy="true" :show-fullscreen="false" /></div></n-collapse-item>
              <n-collapse-item name="response" :title="t('chat.trace.response')">
                <div v-if="selected.responseContent" class="trace-response-section">
                  <h3>{{ t("chat.trace.assistantContent") }}</h3>
                  <div class="trace-code-wrap trace-response-body trace-prompt-wrap" :class="{ 'has-more': scrollHintVisible.responseContent }"><button class="trace-copy" @click="copy(selected.responseContent)"><Copy :size="14" /></button><button class="trace-fullscreen" :title="t('chat.trace.fullscreen')" @click.stop="openFullscreen(t('chat.trace.assistantContent'), selected.responseContent)"><Maximize2 :size="14" /></button><pre class="trace-response-content" @scroll="onScrollHintScroll('responseContent', $event)">{{ selected.responseContent }}</pre><div v-if="scrollHintVisible.responseContent" class="trace-scroll-hint"><ChevronDown :size="16" /><span>{{ t('chat.trace.scrollForMore') }}</span></div></div>
                </div>
                <div v-if="selected.responseThinking" class="trace-response-section">
                  <h3>{{ t("chat.trace.thinking") }}</h3>
                  <div class="trace-code-wrap trace-thinking-body trace-prompt-wrap" :class="{ 'has-more': scrollHintVisible.responseThinking }"><button class="trace-copy" @click="copy(selected.responseThinking)"><Copy :size="14" /></button><button class="trace-fullscreen" :title="t('chat.trace.fullscreen')" @click.stop="openFullscreen(t('chat.trace.thinking'), selected.responseThinking)"><Maximize2 :size="14" /></button><pre class="trace-response-thinking" @scroll="onScrollHintScroll('responseThinking', $event)">{{ selected.responseThinking }}</pre><div v-if="scrollHintVisible.responseThinking" class="trace-scroll-hint"><ChevronDown :size="16" /><span>{{ t('chat.trace.scrollForMore') }}</span></div></div>
                </div>
                <div v-if="selected.responseToolCalls" class="trace-response-section"><h3>{{ t("chat.trace.toolCalls") }}</h3><div class="trace-response-body"><TraceJsonViewer :raw-text="selected.responseToolCalls" :title="t('chat.trace.toolCalls')" @fullscreen="openFullscreen(t('chat.trace.toolCalls'), $event)" /></div></div>
                <div v-if="!selected.responseContent && !selected.responseThinking && !selected.responseToolCalls" class="trace-empty-response">{{ t("chat.trace.noResponse") }}</div>
              </n-collapse-item>
              <n-collapse-item v-if="selected.errorMessage" name="error" :title="t('chat.trace.error')"><div class="trace-code-wrap trace-request-body"><button class="trace-fullscreen" :title="t('chat.trace.fullscreen')" @click.stop="openFullscreen(t('chat.trace.error'), selected.errorType + '\n' + selected.errorMessage)"><Maximize2 :size="14" /></button><pre class="trace-code trace-error-code">{{ selected.errorType }}\n{{ selected.errorMessage }}</pre></div></n-collapse-item>
              </n-collapse>
            </template>
          </div>
        </n-card>
      </section>
    </main>
    <n-modal
      v-model:show="fullscreenVisible"
      preset="card"
      class="trace-fullscreen-modal"
      :title="fullscreenLog?.title || ''"
      :style="{ width: '100vw', height: '100vh', maxWidth: '100vw', maxHeight: '100vh', margin: '0' }"
      :content-style="{ display: 'flex', flex: '1 1 auto', minHeight: '0', padding: '0' }"
      :closable="true"
      :mask-closable="true"
    >
      <div class="trace-fullscreen-content">
        <div class="trace-fullscreen-toolbar">
          <div class="trace-format-switch" role="tablist" :aria-label="t('chat.trace.format')">
            <button type="button" :class="{ active: fullscreenMode === 'raw' }" @click="fullscreenMode = 'raw'">{{ t("chat.trace.rawFormat") }}</button>
            <button type="button" :class="{ active: fullscreenMode === 'json' }" @click="fullscreenMode = 'json'">{{ t("chat.trace.jsonFormat") }}</button>
          </div>
        </div>
        <pre v-if="fullscreenMode === 'raw'" class="trace-fullscreen-raw">{{ fullscreenLog?.content || "—" }}</pre>
        <div v-else class="trace-fullscreen-json"><TraceJsonTree :raw="fullscreenLog?.content || ''" /></div>
      </div>
    </n-modal>
  </div>
</template>

<style scoped>
.trace-page-shell { height: 100vh; overflow: hidden; background: #000; color: #f2f3f5; }
.trace-page { position: relative; min-width: 0; height: 100%; box-sizing: border-box; }
.trace-page :deep(.n-card) { background: #101114; border-color: #2a2d33; color: #f2f3f5; }
.trace-page :deep(.n-card-header) { color: #f2f3f5; }
.trace-page :deep(.n-collapse-item__header) { color: #e5e7eb; }
.trace-page :deep(.n-button) { color: #f2f3f5; }
.trace-layout { display: grid; grid-template-columns: minmax(220px, 280px) minmax(0, 1fr); gap: 0; width: 100%; height: 100vh; min-height: 0; align-items: stretch; }
.trace-layout > * { min-width: 0; min-height: 0; }
.trace-page :deep(.n-card) { border-radius: 0; height: 100%; }
.trace-timeline-item { width: 100%; display: block; text-align: left; padding: 11px 12px; margin-bottom: 5px; border: 1px solid transparent; border-radius: 8px; background: transparent; color: inherit; cursor: pointer; }
.trace-timeline-item:hover, .trace-timeline-item.selected { background: #17191d; border-color: #287d72; }
.trace-item-content { display: grid; grid-template-columns: 8px minmax(0, 1fr); align-items: start; gap: 10px; }
.trace-item-details { min-width: 0; }
.trace-status-dot { width: 7px; height: 7px; margin-top: 6px; flex: none; border-radius: 50%; background: #f0b429; }
.trace-status-dot.success { background: #27c281; }
.trace-status-dot.error { background: #ef5b5b; }
.trace-item-title { font-size: 13px; font-weight: 650; }
.trace-item-meta { margin-top: 4px; overflow-wrap: anywhere; font-size: 13px; color: #a3aab5; line-height: 1.45; }
.trace-item-time { color: #7f8792; font-size: 12px; }
.trace-detail-overview { display: flex; align-items: stretch; flex-direction: column; gap: 14px; margin-bottom: 18px; padding: 0 0 16px; border-bottom: 1px solid #292d34; color: #c2c5ca; }
.trace-summary-main { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-width: 0; }
.trace-summary-identity { display: flex; align-items: baseline; gap: 7px; min-width: 0; color: #aeb4be; font-size: 14px; }
.trace-summary-identity strong { min-width: 0; color: #eef0f2; font-size: 16px; font-weight: 600; overflow-wrap: anywhere; }
.trace-summary-separator { color: #68707c; }
.trace-summary-main :deep(.n-tag) { flex: none; font-size: 11px; }
.trace-summary-metrics { display: flex; align-items: stretch; flex-wrap: wrap; gap: 20px; }
.trace-summary-metric { display: flex; min-width: 76px; flex-direction: column; justify-content: center; gap: 3px; }
.trace-summary-metric + .trace-summary-metric { padding-left: 20px; border-left: 1px solid #30343c; }
.trace-summary-metric span { color: #8f96a1; font-size: 11px; }
.trace-summary-metric strong { color: #e1e4e8; font-size: 15px; font-weight: 550; white-space: nowrap; }
.trace-summary-metric small { margin-left: 3px; color: #9da3ad; font-size: 11px; font-weight: 400; }
.trace-timeline-card { min-height: 0; height: 100%; }
.trace-timeline-card :deep(.n-card__content) { min-height: 0; overflow: hidden; }
.trace-timeline-scroll { height: calc(100vh - 72px); max-height: calc(100vh - 72px); margin-right: -24px; padding-right: 24px; box-sizing: border-box; overflow-y: auto; overflow-x: hidden; scrollbar-color: #4c515b #101114; }
.trace-detail-card { min-height: 100%; height: 100vh; overflow-y: auto; overflow-x: hidden; scrollbar-color: #4c515b #101114; }
.trace-detail-card :deep(.n-card__content) { min-height: 0; overflow: visible; }
.trace-detail-scroll { height: auto; max-height: none; overflow: visible; padding-right: 8px; }
.trace-kv-grid { display: grid; grid-template-columns: minmax(132px, 180px) minmax(0, 1fr); overflow: hidden; border: 1px solid #292d34; border-radius: 8px; background: #14161a; font-size: 13px; }
.trace-kv-grid span, .trace-kv-grid strong { min-width: 0; padding: 10px 12px; border-bottom: 1px solid #252930; line-height: 1.5; }
.trace-kv-grid span { color: #9da3ad; }
.trace-kv-grid strong { color: #e1e4e8; font-weight: 500; overflow-wrap: anywhere; }
.trace-kv-grid span:nth-last-child(2), .trace-kv-grid strong:last-child { border-bottom: 0; }
.trace-code-wrap { position: relative; }
.trace-code, pre { margin: 0; max-height: 420px; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font: 12px/1.6 ui-monospace, SFMono-Regular, Menlo, monospace; }
.trace-code-wrap pre { padding-right: 34px; }
.trace-request-body, .trace-response-body { padding: 14px; border: 1px solid; border-radius: 8px; }
.trace-request-body { background: #171b22; border-color: #2b333f; }
.trace-prompt-wrap { overflow: hidden; padding-bottom: 0; }
.trace-prompt-wrap.has-more { box-shadow: inset 0 -34px 28px -24px rgba(16, 17, 20, 0.95); }
.trace-system-prompt { position: relative; max-height: 420px; padding-bottom: 42px !important; scrollbar-color: #4c515b #171b22; }
.trace-scroll-hint { position: absolute; right: 50%; bottom: 8px; display: flex; align-items: center; gap: 5px; max-width: calc(100% - 24px); padding: 5px 9px; transform: translateX(50%); border: 1px solid rgba(110, 123, 141, 0.45); border-radius: 999px; background: rgba(23, 27, 34, 0.92); color: #c5ccd6; font-size: 11px; pointer-events: none; white-space: nowrap; }
.trace-scroll-hint svg { animation: trace-scroll-hint-bounce 1.4s ease-in-out infinite; color: #7fd1c2; }
@keyframes trace-scroll-hint-bounce { 0%, 100% { transform: translateY(-2px); } 50% { transform: translateY(2px); } }
.trace-fullscreen { position: absolute; top: 10px; right: 34px; z-index: 2; display: grid; place-items: center; padding: 4px; border: 0; border-radius: 4px; background: #171b22; color: #aeb6c2; cursor: pointer; }
.trace-fullscreen:hover { background: #2a3039; color: #f2f3f5; }
.trace-fullscreen-modal { box-sizing: border-box; display: flex !important; flex-direction: column; width: 100vw !important; max-width: 100vw !important; height: 100vh !important; max-height: 100vh !important; margin: 0 !important; border-radius: 0; }
.trace-fullscreen-modal :deep(.n-card__header) { flex: none; }
.trace-fullscreen-modal :deep(.n-card__content) { display: flex; flex: 1 1 auto; min-height: 0; }
.trace-fullscreen-content { box-sizing: border-box; display: flex; flex: 1 1 auto; flex-direction: column; width: 100%; height: 100%; min-height: 0; max-height: none; padding: 24px 28px; overflow: hidden; background: #171b22; color: #e1e4e8; }
.trace-fullscreen-toolbar { flex: none; }
.trace-format-switch { display: inline-flex; gap: 2px; margin-bottom: 14px; padding: 2px; border: 1px solid #343a43; border-radius: 6px; background: #121418; }
.trace-format-switch button { border: 0; border-radius: 4px; padding: 3px 8px; background: transparent; color: #9da3ad; font: 11px/1.4 ui-monospace, SFMono-Regular, Menlo, monospace; cursor: pointer; }
.trace-format-switch button:hover, .trace-format-switch button.active { background: #2a2f37; color: #f2f3f5; }
.trace-fullscreen-raw, .trace-fullscreen-json { box-sizing: border-box; flex: 1 1 auto; min-height: 0; max-height: none; margin: 0; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; scrollbar-color: #4c515b #171b22; }
.trace-fullscreen-raw { font: 13px/1.65 ui-monospace, SFMono-Regular, Menlo, monospace; }
.trace-fullscreen-json { padding-right: 8px; }
.trace-response-body { background: #19211d; border-color: #334038; }
.trace-thinking-body { background: #211f1a; border-color: #464033; }
.trace-response-section + .trace-response-section { margin-top: 16px; }
.trace-response-section h3 { margin: 0 0 8px; color: #a5aab3; font-size: 12px; font-weight: 600; }
.trace-empty-response { color: #a5aab3; font-size: 13px; }
.trace-copy { position: absolute; top: 11px; right: 8px; z-index: 3; display: grid; place-items: center; padding: 4px; border: 0; border-radius: 4px; background: #171b22; color: inherit; cursor: pointer; }
.trace-copy:hover { background: #2a3039; color: #f2f3f5; }
.trace-subsection h3 { margin: 18px 0 8px; font-size: 13px; }
.trace-subsection-toggle + .trace-code-wrap,
.trace-subsection-toggle + .trace-empty-response { margin-left: 12px; }
.trace-subsection-toggle { display: flex; align-items: center; justify-content: flex-start; width: 100%; padding: 0 0 0 12px; border: 0; background: transparent; color: inherit; cursor: pointer; text-align: left; }
.trace-subsection-toggle h3 { flex: 1; }
.trace-subsection-chevron { flex: none; margin: 10px 6px 0 0; color: #a5aab3; transition: transform 160ms ease; }
.trace-subsection-chevron.collapsed { transform: rotate(-90deg); }
.trace-error-code { color: #ff6b6b; }
.trace-state-panel { display: flex; box-sizing: border-box; width: min(440px, calc(100% - 48px)); min-height: 260px; margin: 0 auto; padding: 40px 32px; flex-direction: column; align-items: center; justify-content: center; text-align: center; }
.trace-error-state { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); border: 1px solid #3b2c2f; border-radius: 16px; background: linear-gradient(145deg, #17171b, #101114); box-shadow: 0 18px 50px rgba(0, 0, 0, 0.28); }
.trace-state-icon { display: grid; width: 48px; height: 48px; place-items: center; margin-bottom: 18px; border: 1px solid rgba(239, 91, 91, 0.32); border-radius: 50%; background: rgba(239, 91, 91, 0.1); color: #ff8585; }
.trace-state-panel h2 { margin: 0; color: #f2f3f5; font-size: 17px; font-weight: 650; }
.trace-state-panel p { max-width: 320px; margin: 10px 0 0; color: #9299a5; font-size: 13px; line-height: 1.6; }
.trace-retry-button { display: inline-flex; align-items: center; gap: 7px; margin-top: 24px; padding: 8px 14px; border: 1px solid #3d8178; border-radius: 7px; background: rgba(40, 125, 114, 0.16); color: #9fe1d6; font: inherit; font-size: 13px; cursor: pointer; transition: background 160ms ease, border-color 160ms ease; }
.trace-retry-button:hover { border-color: #58b7a9; background: rgba(40, 125, 114, 0.28); }
.trace-loading { position: absolute; top: 50%; left: 50%; min-height: 180px; gap: 14px; transform: translate(-50%, -50%); }
.trace-loading p { margin-top: 0; }
.trace-empty { display: grid; place-items: center; min-height: 300px; }
@media (max-width: 760px) {
  .trace-summary-metrics { justify-content: flex-start; }
}
</style>
