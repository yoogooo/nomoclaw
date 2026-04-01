<script setup lang="ts">
import { onMounted, ref, watch } from "vue";
import { ChevronLeft } from "lucide-vue-next";
import DirectoryRail from "@/components/chat/DirectoryRail.vue";
import AgentSidebar from "@/components/chat/AgentSidebar.vue";
import ConversationSidebar from "@/components/chat/ConversationSidebar.vue";
import MessagesPanel from "@/components/chat/MessagesPanel.vue";
import RuntimeLogPanel from "@/components/chat/RuntimeLogPanel.vue";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import { useConversationStore } from "@/stores/conversation";
import { useJinnangStore } from "@/stores/jinnang";
import { themeTokens } from "@/themeTokens";

const conversationStore = useConversationStore();
const agentCatalogStore = useAgentCatalogStore();
const jinnangStore = useJinnangStore();
const runtimeCollapsedStorageKey = "chat:runtime-collapsed";
const runtimeCollapsed = ref(false);

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
  if (!conversationStore.conversations.length) {
    void conversationStore.init();
  }
  if (agentCatalogStore.selectedAgentUid) {
    void jinnangStore.restoreTips(agentCatalogStore.selectedAgentUid);
  }
});

watch(
  () => agentCatalogStore.selectedAgentUid,
  (agentUid) => {
    if (!agentUid) return;
    void jinnangStore.restoreTips(agentUid);
  },
  { immediate: true }
);
</script>

<template>
  <div class="page-frame chat-page">
    <div class="grid-chat" :class="{ 'runtime-collapsed': runtimeCollapsed }">
      <DirectoryRail />
      <AgentSidebar />
      <ConversationSidebar />
      <MessagesPanel />
      <RuntimeLogPanel v-if="!runtimeCollapsed" :collapsed="runtimeCollapsed" @toggle="runtimeCollapsed = !runtimeCollapsed" />
    </div>
    <button v-if="runtimeCollapsed" class="runtime-expand-toggle" title="展开日志" @click="runtimeCollapsed = false">
      <ChevronLeft :size="16" />
    </button>
  </div>
</template>
