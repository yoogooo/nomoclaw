<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useI18n } from "vue-i18n";
import { ChevronLeft } from "lucide-vue-next";
import { NSpin } from "naive-ui";
import { useRoute, useRouter } from "vue-router";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import ConversationSidebar from "@/components/chat/ConversationSidebar.vue";
import MessagesPanel from "@/components/chat/MessagesPanel.vue";
import RuntimeLogPanel from "@/components/chat/RuntimeLogPanel.vue";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { useConversationStore } from "@/stores/conversation";
import { useCronJobsStore } from "@/stores/cronJobs";
import { useJinnangStore } from "@/stores/jinnang";
import { useUiPreferencesStore } from "@/stores/uiPreferences";
import { themeTokens } from "@/themeTokens";

const route = useRoute();
const router = useRouter();
const conversationStore = useConversationStore();
const agentCatalogStore = useAgentCatalogStore();
const cronJobsStore = useCronJobsStore();
const jinnangStore = useJinnangStore();
const uiPreferencesStore = useUiPreferencesStore();
uiPreferencesStore.init();
const runtimeCollapsedStorageKey = "chat:runtime-collapsed";
const runtimeCollapsed = ref(false);
const { t } = useI18n();
const browserRuntimeOverlay = computed(() => conversationStore.browserRuntimeOverlay);
const showRuntimeLogPanel = computed(() => uiPreferencesStore.chatRuntimeLogVisible);
const runtimeAreaCollapsed = computed(() => !showRuntimeLogPanel.value || runtimeCollapsed.value);
const routeConversationUid = computed(() => String(route.query.conversationUid || "").trim());
const routeMessageUid = computed(() => String(route.query.messageUid || "").trim());
const routeAgentUid = computed(() => String(route.query.agentUid || "").trim());
const routeJobUid = computed(() => String(route.query.jobUid || "").trim());
const routeSource = computed(() => String(route.query.source || "").trim().toLowerCase());
const applyingRouteContext = ref(false);
const cronRouteBootstrapDone = ref(false);
let applyRouteToken = 0;

const shouldGateCronRouteView = computed(() =>
  routeSource.value === "cron" && !!routeConversationUid.value
);
const pageLoading = computed(() =>
  conversationStore.loading || applyingRouteContext.value
);
const cronRouteReady = computed(() =>
  !shouldGateCronRouteView.value
  || cronRouteBootstrapDone.value
  || (
    !applyingRouteContext.value
    && conversationStore.currentConversationUid === routeConversationUid.value
  )
);

if (typeof window !== "undefined") {
  runtimeCollapsed.value = window.localStorage.getItem(runtimeCollapsedStorageKey) === "1";
}

watch(runtimeCollapsed, (value) => {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(runtimeCollapsedStorageKey, value ? "1" : "0");
});

onMounted(() => {
  if (window.matchMedia(`(max-width: ${themeTokens.layout.breakpointLg})`).matches) {
    runtimeCollapsed.value = true;
  }
  if (!routeConversationUid.value) {
    if (!conversationStore.conversations.length) {
      void conversationStore.init();
    } else {
      void conversationStore.loadModelConfig();
    }
  }
  if (agentCatalogStore.selectedAgentUid) {
    void jinnangStore.restoreTips(agentCatalogStore.selectedAgentUid);
  }
});

async function applyConversationRouteContext() {
  const token = ++applyRouteToken;
  const targetConversationUid = routeConversationUid.value;
  if (!targetConversationUid) {
    applyingRouteContext.value = false;
    cronRouteBootstrapDone.value = true;
    return;
  }
  applyingRouteContext.value = true;
  try {
    if (!conversationStore.conversations.length) {
      await conversationStore.init();
    } else {
      await conversationStore.loadModelConfig();
    }
    if (token !== applyRouteToken) return;
    if (!agentCatalogStore.allAgents.length) {
      await agentCatalogStore.loadCatalog();
    }
    if (token !== applyRouteToken) return;

    const conversationSummary = conversationStore.conversations.find(
      (item) => item.conversationUid === targetConversationUid
    );
    const targetAgentUid = routeAgentUid.value || String(conversationSummary?.agentUid || "").trim();
    if (targetAgentUid) {
      const targetAgent = agentCatalogStore.allAgents.find((item) => item.agentUid === targetAgentUid);
      if (targetAgent && targetAgent.agentUid !== agentCatalogStore.selectedAgentUid) {
        agentCatalogStore.selectAgent(targetAgent.agentGroupUid, targetAgent.agentUid);
        await conversationStore.refreshConversations(targetConversationUid);
        if (token !== applyRouteToken) return;
      }
    }

    if (routeJobUid.value) {
      void cronJobsStore.selectJob(routeJobUid.value);
    }

    if (conversationStore.currentConversationUid !== targetConversationUid) {
      await conversationStore.selectConversation(targetConversationUid);
    }
    if (token !== applyRouteToken) return;
    await conversationStore.refreshConversations(targetConversationUid);
  } finally {
    if (token === applyRouteToken) {
      if (conversationStore.currentConversationUid === targetConversationUid) {
        cronRouteBootstrapDone.value = true;
      }
      applyingRouteContext.value = false;
    }
  }
}

watch(
  () => agentCatalogStore.selectedAgentUid,
  (agentUid) => {
    if (!agentUid) return;
    void jinnangStore.restoreTips(agentUid);
  },
  { immediate: true }
);

watch(
  () => [routeConversationUid.value, routeAgentUid.value, routeJobUid.value],
  () => {
    if (!routeConversationUid.value) {
      applyingRouteContext.value = false;
      cronRouteBootstrapDone.value = true;
    } else {
      // Each new deep-link target should gate once during bootstrap.
      cronRouteBootstrapDone.value = false;
    }
    void applyConversationRouteContext();
  },
  { immediate: true }
);

watch(
  () => conversationStore.currentConversationUid,
  (currentConversationUid) => {
    const current = String(currentConversationUid || "").trim();
    if (!current) return;
    if (!routeConversationUid.value) return;
    if (current === routeConversationUid.value) return;
    if (routeSource.value === "cron" && !cronRouteBootstrapDone.value) return;

    const nextQuery = { ...route.query } as Record<string, any>;
    // User switched away from deep-linked target: keep URL in sync with current conversation
    // so refresh won't jump back to stale conversationUid.
    if (routeSource.value === "cron") {
      delete nextQuery.source;
      delete nextQuery.executionUid;
      delete nextQuery.jobUid;
      delete nextQuery.messageUid;
    }
    nextQuery.conversationUid = current;
    void router.replace({ path: route.path, query: nextQuery });
  }
);
</script>

<template>
  <div class="page-frame chat-page">
    <n-spin :show="pageLoading">
      <template #description>
        <span>{{ t("chat.messages.routeLoadingMessage") }}</span>
      </template>
      <div class="grid-chat" :class="{ 'runtime-collapsed': runtimeAreaCollapsed }">
        <DirectoryRail />
        <ConversationSidebar v-if="cronRouteReady" />
        <div v-else class="panel chat-route-loading">
          <div class="panel-body">{{ t("chat.messages.routeLoadingMessage") }}</div>
        </div>
        <MessagesPanel v-if="cronRouteReady" :focus-message-uid="routeMessageUid" />
        <div v-else class="panel chat-route-loading">
          <div class="panel-body">{{ t("chat.messages.routeLoadingMessage") }}</div>
        </div>
        <RuntimeLogPanel v-if="showRuntimeLogPanel && !runtimeCollapsed" :collapsed="runtimeCollapsed" @toggle="runtimeCollapsed = !runtimeCollapsed" />
      </div>
    </n-spin>
    <button v-if="showRuntimeLogPanel && runtimeCollapsed" class="runtime-expand-toggle" :title="t('chat.runtime.expand')" @click="runtimeCollapsed = false">
      <ChevronLeft :size="16" />
    </button>
    <div v-if="browserRuntimeOverlay.visible" class="browser-runtime-overlay" role="status" aria-live="polite">
      <div class="browser-runtime-overlay__card">
        <div class="browser-runtime-overlay__head">
          <div class="browser-runtime-overlay__title-wrap">
            <div class="browser-runtime-overlay__spinner" />
            <h3 class="browser-runtime-overlay__title">{{ browserRuntimeOverlay.title || "浏览器依赖准备中" }}</h3>
          </div>
          <span class="browser-runtime-overlay__percent">
            {{ browserRuntimeOverlay.indeterminate ? "..." : `${Math.max(0, Math.min(100, browserRuntimeOverlay.progressPercent || 0))}%` }}
          </span>
        </div>
        <p class="browser-runtime-overlay__desc">{{ browserRuntimeOverlay.details || "首次运行可能下载较大资源，请稍候。" }}</p>
        <p class="browser-runtime-overlay__hint">{{ browserRuntimeOverlay.hint }}</p>
        <div class="browser-runtime-overlay__track">
          <div
            class="browser-runtime-overlay__bar"
            :class="{ 'browser-runtime-overlay__bar--indeterminate': browserRuntimeOverlay.indeterminate }"
            :style="{ width: browserRuntimeOverlay.indeterminate ? '45%' : `${Math.max(2, Math.min(100, browserRuntimeOverlay.progressPercent || 0))}%` }"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  position: relative;
}

.chat-route-loading {
  display: flex;
  align-items: center;
  justify-content: center;
}

.browser-runtime-overlay {
  position: absolute;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(8, 12, 17, 0.58);
  backdrop-filter: blur(3px);
}

.browser-runtime-overlay__card {
  width: min(620px, calc(100vw - 36px));
  padding: 18px 20px 16px;
  border-radius: 14px;
  border: 1px solid rgba(255, 255, 255, 0.14);
  background: rgba(10, 13, 20, 0.9);
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.38);
}

.browser-runtime-overlay__spinner {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.26);
  border-top-color: #67e8f9;
  animation: browser-runtime-spin 0.9s linear infinite;
}

.browser-runtime-overlay__title {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  color: #f8fbff;
}

.browser-runtime-overlay__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.browser-runtime-overlay__title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.browser-runtime-overlay__percent {
  font-size: 28px;
  line-height: 1;
  font-weight: 700;
  color: #67e8f9;
  letter-spacing: -0.02em;
}

.browser-runtime-overlay__desc {
  margin: 10px 0 6px;
  font-size: 14px;
  line-height: 1.4;
  color: rgba(232, 240, 255, 0.86);
}

.browser-runtime-overlay__hint {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.45;
  color: rgba(177, 196, 220, 0.9);
}

.browser-runtime-overlay__track {
  width: 100%;
  height: 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  overflow: hidden;
}

.browser-runtime-overlay__bar {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #22d3ee, #60a5fa);
  transition: width 0.45s ease;
}

.browser-runtime-overlay__bar--indeterminate {
  animation: browser-runtime-indeterminate 1.1s ease-in-out infinite;
}

@keyframes browser-runtime-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes browser-runtime-indeterminate {
  0% {
    transform: translateX(-48%);
  }
  100% {
    transform: translateX(148%);
  }
}
</style>
