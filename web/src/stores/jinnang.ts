import { ref } from "vue";
import { defineStore } from "pinia";
import { conversationApi } from "@/api/conversationApi";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import type { AgentTip } from "@/types/api";

export interface JinnangTip {
  id: string;
  title: string;
  summary: string;
  sourceContent: string;
  conversationUid: string;
  sourceMessageUid: string;
  sourceTime: string;
  createdTime: string;
}

function toJinnangTip(item: AgentTip): JinnangTip {
  return {
    id: item.tipUid,
    title: item.title,
    summary: item.summary || "",
    sourceContent: item.sourceContent || "",
    conversationUid: item.sourceConversationUid || "",
    sourceMessageUid: item.sourceMessageUid || "",
    sourceTime: item.sourceTime || "",
    createdTime: item.createdTime || item.updatedTime
  };
}

export const useJinnangStore = defineStore("jinnang", () => {
  const tips = ref<JinnangTip[]>([]);
  const loaded = ref(false);
  const loadedAgentUid = ref("");

  async function restoreTips(agentUid?: string) {
    const catalogStore = useAgentCatalogStore();
    const targetAgentUid = (agentUid || catalogStore.selectedAgentUid || "").trim();
    if (!targetAgentUid) {
      tips.value = [];
      loaded.value = true;
      loadedAgentUid.value = "";
      return;
    }
    const list = await conversationApi.listAgentTips(targetAgentUid);
    tips.value = list.map(toJinnangTip);
    loaded.value = true;
    loadedAgentUid.value = targetAgentUid;
  }

  async function addTip(params: {
    agentUid: string;
    messageUid?: string;
    title?: string;
    content: string;
    summary?: string;
    sourceTime: string;
    conversationUid: string;
    generateBestPractice?: boolean;
    suppressErrorToast?: boolean;
  }) {
    const targetAgentUid = (params.agentUid || "").trim();
    if (!targetAgentUid) {
      throw new Error("agentUid is required");
    }
    const created = await conversationApi.createAgentTip(targetAgentUid, {
      title: params.title,
      summary: params.summary,
      sourceContent: params.content,
      sourceConversationUid: params.conversationUid,
      sourceMessageUid: params.messageUid,
      sourceTime: params.sourceTime,
      generateBestPractice: params.generateBestPractice
    }, {
      suppressErrorToast: params.suppressErrorToast
    });
    if (loadedAgentUid.value !== targetAgentUid || !loaded.value) {
      await restoreTips(targetAgentUid);
      return;
    }
    const mapped = toJinnangTip(created);
    tips.value = [mapped, ...tips.value.filter((item) => item.id !== mapped.id)];
  }

  async function removeTip(id: string, agentUid?: string) {
    const targetAgentUid = (agentUid || loadedAgentUid.value || "").trim();
    if (!targetAgentUid) {
      throw new Error("agentUid is required");
    }
    await conversationApi.deleteAgentTip(targetAgentUid, id);
    tips.value = tips.value.filter((tip) => tip.id !== id);
  }

  return {
    tips,
    loaded,
    loadedAgentUid,
    restoreTips,
    addTip,
    removeTip
  };
});
