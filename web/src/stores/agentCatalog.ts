import { computed, ref } from "vue";
import { defineStore } from "pinia";
import { conversationApi } from "@/api/conversationApi";
import type { AgentCatalogGroup, ConversationSummary, EntryType } from "@/types/api";

const STORAGE_KEY = "nomoclaw:last-selection";

export const useAgentCatalogStore = defineStore("agentCatalog", () => {
  const groups = ref<AgentCatalogGroup[]>([]);
  const selectedEntryType = ref<EntryType>("agent");
  const selectedAgentGroupUid = ref("group_short_drama");
  const selectedAgentUid = ref("agent_general_assistant");

  function persistSelection() {
    window.localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        entryType: selectedEntryType.value,
        agentGroupUid: selectedAgentGroupUid.value,
        agentUid: selectedAgentUid.value
      })
    );
  }

  function restoreSelection() {
    try {
      const raw = window.localStorage.getItem(STORAGE_KEY);
      if (!raw) return;
      const saved = JSON.parse(raw) as Partial<{
        entryType: EntryType;
        agentGroupUid: string;
        agentUid: string;
      }>;
      selectedEntryType.value = saved.entryType === "group" ? "group" : "agent";
      selectedAgentGroupUid.value = saved.agentGroupUid || selectedAgentGroupUid.value;
      selectedAgentUid.value = saved.agentUid || selectedAgentUid.value;
    } catch {
      // ignore malformed local storage
    }
  }

  function ensureSelection() {
    if (!groups.value.length) {
      selectedEntryType.value = "agent";
      selectedAgentGroupUid.value = "";
      selectedAgentUid.value = "";
      return;
    }

    const currentGroup = groups.value.find((item) => item.agentGroupUid === selectedAgentGroupUid.value);
    const currentAgent = groups.value.flatMap((item) => item.agents).find((item) => item.agentUid === selectedAgentUid.value);
    const stillExists = selectedEntryType.value === "group" ? Boolean(currentGroup) : Boolean(currentAgent);

    if (stillExists) {
      return;
    }

    const fallbackGroup = groups.value[0];
    const fallbackAgent = fallbackGroup?.agents[0];
    selectedEntryType.value = "agent";
    selectedAgentGroupUid.value = fallbackGroup?.agentGroupUid || "";
    selectedAgentUid.value = fallbackAgent?.agentUid || "";
    persistSelection();
  }

  function matchesConversation(conversation: ConversationSummary) {
    if (selectedEntryType.value === "group") {
      return conversation.agentGroupUid === selectedAgentGroupUid.value;
    }
    return conversation.agentUid === selectedAgentUid.value;
  }

  async function loadCatalog() {
    groups.value = await conversationApi.listAgentGroups();
    ensureSelection();
  }

  function selectAgent(agentGroupUid: string, agentUid: string) {
    selectedEntryType.value = "agent";
    selectedAgentGroupUid.value = agentGroupUid;
    selectedAgentUid.value = agentUid;
    persistSelection();
  }

  function selectGroup(agentGroupUid: string) {
    selectedEntryType.value = "group";
    selectedAgentGroupUid.value = agentGroupUid;
    selectedAgentUid.value = "";
    persistSelection();
  }

  const allAgents = computed(() =>
    groups.value.flatMap((group) =>
      group.agents.map((agent) => ({
        ...agent,
        agentGroupUid: group.agentGroupUid,
        entryType: "agent" as const
      }))
    )
  );

  return {
    groups,
    selectedEntryType,
    selectedAgentGroupUid,
    selectedAgentUid,
    allAgents,
    restoreSelection,
    loadCatalog,
    ensureSelection,
    matchesConversation,
    selectAgent,
    selectGroup
  };
});
