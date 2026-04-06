import { computed, reactive, ref } from "vue";
import { conversationApi } from "@/api/conversationApi";
import { modelApi } from "@/api/modelApi";
import { dialog, message, warningDialogPreset } from "@/discrete";
import { tr } from "@/i18n";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import type {
  AgentDocConfig,
  AvatarIconOption,
  BasicFormModel,
  DocKey,
  ManagedAgent
} from "@/components/agents/agentManagementTypes";
import type { AgentDocFile, ImportedSkillResponse, ModelConfig } from "@/types/api";
import {
  defaultDocs,
  mapApiSkill,
  mapApiTip,
  mapApiTool,
  normalizeAvatarColor,
  normalizeAvatarIcon,
  resolveFallbackModelSelection,
  toManagedAgent
} from "@/components/agents/domain/agentManagementDomain";

interface UseAgentsManagementOptions {
  avatarIconOptions: AvatarIconOption[];
  avatarColorOptions: string[];
  defaultAvatarIcon: string;
  defaultAvatarColor: string;
}

export function useAgentsManagement(options: UseAgentsManagementOptions) {
  const agentCatalogStore = useAgentCatalogStore();

  const agents = ref<ManagedAgent[]>([]);
  const nomoclawRootDir = ref("");
  const agentsRootDir = ref("");
  const skillsRootDir = ref("");
  const modelConfig = ref<ModelConfig>({ providers: [] });

  const createForm = reactive({
    displayName: "",
    agentName: "",
    description: "",
    avatar: options.defaultAvatarIcon,
    avatarColor: options.defaultAvatarColor
  });

  const tipForm = reactive({
    title: "",
    content: ""
  });

  const docsForm = reactive<AgentDocConfig>({
    soul: "",
    agent: "",
    memory: "",
    tools: "",
    identity: "",
    user: ""
  });

  const basicForm = reactive<BasicFormModel>({
    displayName: "",
    description: "",
    avatar: options.defaultAvatarIcon,
    avatarColor: options.defaultAvatarColor
  });

  const domainOptions = computed(() => ({
    nomoclawRootDir: nomoclawRootDir.value,
    skillsRootDir: skillsRootDir.value,
    avatarIconKeys: options.avatarIconOptions.map((item) => item.key),
    avatarColorOptions: options.avatarColorOptions,
    defaultAvatarIcon: options.defaultAvatarIcon,
    defaultAvatarColor: options.defaultAvatarColor
  }));

  let workspaceLoadSeq = 0;

  function updateAgent(agentUid: string, updater: (agent: ManagedAgent) => ManagedAgent) {
    const index = agents.value.findIndex((item) => item.agentUid === agentUid);
    if (index < 0) return;
    agents.value[index] = updater(agents.value[index]);
  }

  function resetCreateForm() {
    createForm.displayName = "";
    createForm.agentName = "";
    createForm.description = "";
    createForm.avatar = options.defaultAvatarIcon;
    createForm.avatarColor = options.defaultAvatarColor;
  }

  function ensureSelectedAgent(selectedAgentUid: string) {
    if (!agents.value.length) {
      return "";
    }
    return agents.value.some((item) => item.agentUid === selectedAgentUid) ? selectedAgentUid : agents.value[0].agentUid;
  }

  function syncDocsFormFromSelection(selectedAgent: ManagedAgent | null) {
    if (!selectedAgent) {
      docsForm.soul = "";
      docsForm.agent = "";
      docsForm.memory = "";
      docsForm.tools = "";
      docsForm.identity = "";
      docsForm.user = "";
      return;
    }
    docsForm.soul = selectedAgent.docs.soul;
    docsForm.agent = selectedAgent.docs.agent;
    docsForm.memory = selectedAgent.docs.memory;
    docsForm.tools = selectedAgent.docs.tools;
    docsForm.identity = selectedAgent.docs.identity;
    docsForm.user = selectedAgent.docs.user;
  }

  function syncBasicFormFromSelection(selectedAgent: ManagedAgent | null) {
    if (!selectedAgent) {
      basicForm.displayName = "";
      basicForm.description = "";
      basicForm.avatar = options.defaultAvatarIcon;
      basicForm.avatarColor = options.defaultAvatarColor;
      return;
    }
    basicForm.displayName = selectedAgent.displayName || "";
    basicForm.description = selectedAgent.description || "";
    basicForm.avatar = normalizeAvatarIcon(selectedAgent.avatar, domainOptions.value);
    basicForm.avatarColor = normalizeAvatarColor(selectedAgent.avatarColor, domainOptions.value);
  }

  async function reloadAgentsFromCatalog() {
    await agentCatalogStore.loadCatalog();
    agents.value = agentCatalogStore.groups.flatMap((group) =>
      group.agents.map((agent) => toManagedAgent(agent, group.agentGroupUid, domainOptions.value))
    );
  }

  async function init() {
    try {
      const config = await conversationApi.getSystemConfig();
      const rootDir = (config.nomoclawRootDir || "").trim();
      const agentDir = (config.agentsRootDir || "").trim();
      const skillDir = (config.skillsRootDir || "").trim();
      if (!rootDir || !agentDir || !skillDir) {
        throw new Error("system config missing required directories");
      }
      nomoclawRootDir.value = rootDir;
      agentsRootDir.value = agentDir;
      skillsRootDir.value = skillDir;
    } catch {
      message.error(tr("errors.loadSystemPathFailed"));
      throw new Error("system config load failed");
    }
    try {
      modelConfig.value = await modelApi.getModelConfig();
    } catch {
      message.error(tr("errors.loadModelConfigFailed"));
      throw new Error("model config load failed");
    }
    await reloadAgentsFromCatalog();
  }

  async function loadAgentWorkspace(agentUid: string) {
    if (!agentUid) return;
    const isPersisted = agentCatalogStore.allAgents.some((item) => item.agentUid === agentUid);
    if (!isPersisted) return;
    const requestSeq = ++workspaceLoadSeq;
    const [skills, tools, tips, docs] = await Promise.all([
      conversationApi.listAgentSkills(agentUid),
      conversationApi.listAgentTools(agentUid),
      conversationApi.listAgentTips(agentUid),
      conversationApi.listAgentDocs(agentUid)
    ]);
    if (requestSeq !== workspaceLoadSeq) return;
    updateAgent(agentUid, (agent) => ({
      ...agent,
      managedSkills: skills.map((item) => mapApiSkill(item, domainOptions.value)),
      managedTools: tools.map(mapApiTool),
      tips: tips.map(mapApiTip),
      docs: {
        ...agent.docs,
        ...mapDocsPayload(docs)
      },
      docStates: mergeDocUpdatedTime(agent.docStates, docs)
    }));
  }

  async function saveAgent() {
    const displayName = createForm.displayName.trim();
    const agentName = createForm.agentName.trim();
    if (!displayName) {
      message.warning(tr("errors.fillAgentName"));
      return "";
    }
    if (!agentName) {
      message.warning(tr("errors.fillAgentIdentifier"));
      return "";
    }
    const fallbackModel = resolveFallbackModelSelection(modelConfig.value);
    if (!fallbackModel.modelProvider || !fallbackModel.modelName) {
      message.warning(tr("errors.noAvailableModel"));
      return "";
    }
    const payload = {
      displayName,
      agentName,
      description: createForm.description.trim(),
      avatar: normalizeAvatarIcon(createForm.avatar, domainOptions.value),
      avatarColor: normalizeAvatarColor(createForm.avatarColor, domainOptions.value),
      modelProvider: fallbackModel.modelProvider,
      modelName: fallbackModel.modelName,
      modelNames: [...fallbackModel.modelNames]
    };
    const created = await conversationApi.createAgent(payload);
    await reloadAgentsFromCatalog();
    const createdAgent = agents.value.find((item) => item.agentUid === created.agentUid);
    if (createdAgent) {
      updateAgent(createdAgent.agentUid, (agent) => ({
        ...agent,
        docs: defaultDocs(displayName)
      }));
    }
    message.success(tr("toast.agentCreated"));
    return created.agentUid;
  }

  function removeAgent(agent: ManagedAgent, onDeleted?: () => void) {
    if (agent.agentUid === "agent_general_assistant") {
      message.warning(tr("toast.defaultAgentDeleteDenied"));
      return;
    }
    dialog.warning({
      title: tr("dialogs.deleteAgentTitle"),
      content: tr("dialogs.deleteAgentContent", { name: agent.displayName || agent.agentName }),
      ...warningDialogPreset(),
      positiveText: tr("dialogs.confirmContinue"),
      negativeText: tr("common.cancel"),
      onPositiveClick: () => {
        dialog.error({
          title: tr("dialogs.deleteAgentRiskTitle"),
          content: tr("dialogs.deleteAgentRiskContent"),
          ...warningDialogPreset(),
          positiveText: tr("dialogs.confirmPermanentDelete"),
          negativeText: tr("common.cancel"),
          onPositiveClick: async () => {
            await conversationApi.deleteAgent(agent.agentUid);
            agents.value = agents.value.filter((item) => item.agentUid !== agent.agentUid);
            await agentCatalogStore.loadCatalog();
            onDeleted?.();
            message.success(tr("toast.agentDeleted"));
          }
        });
      }
    });
  }

  function setSkillEnabled(selectedAgent: ManagedAgent | null, skillId: string, enabled: boolean) {
    if (!selectedAgent) return;
    const skill = selectedAgent.managedSkills.find((item) => item.id === skillId);
    if (!skill) return;
    void conversationApi.updateAgentSkillStatus(selectedAgent.agentUid, skill.skillKey, enabled)
      .then((updated) => {
        updateAgent(selectedAgent.agentUid, (agent) => ({
          ...agent,
          managedSkills: agent.managedSkills.map((item) =>
            item.id === skillId
              ? {
                  ...item,
                  enabled: updated.enabled
                }
              : item
          )
        }));
      });
  }

  function setToolEnabled(selectedAgent: ManagedAgent | null, toolId: string, enabled: boolean) {
    if (!selectedAgent) return;
    const tool = selectedAgent.managedTools.find((item) => item.id === toolId);
    if (!tool) return;
    void conversationApi.updateAgentToolStatus(selectedAgent.agentUid, tool.toolKey, enabled)
      .then((updated) => {
        updateAgent(selectedAgent.agentUid, (agent) => ({
          ...agent,
          managedTools: agent.managedTools.map((item) =>
            item.id === toolId
              ? {
                  ...item,
                  enabled: updated.enabled
                }
              : item
          )
        }));
      });
  }

  async function addTip(selectedAgent: ManagedAgent | null) {
    if (!selectedAgent) return;
    const title = tipForm.title.trim();
    const content = tipForm.content.trim();
    if (!title || !content) {
      message.warning(tr("errors.fillTipTitleAndContent"));
      return;
    }
    const created = await conversationApi.createAgentTip(selectedAgent.agentUid, {
      title,
      sourceContent: content
    });
    const tip = mapApiTip(created);
    updateAgent(selectedAgent.agentUid, (agent) => ({
      ...agent,
      tips: [tip, ...agent.tips]
    }));
    tipForm.title = "";
    tipForm.content = "";
    message.success(tr("toast.tipSavedToDb"));
  }

  async function removeTip(selectedAgent: ManagedAgent | null, tipId: string) {
    if (!selectedAgent) return;
    await conversationApi.deleteAgentTip(selectedAgent.agentUid, tipId);
    updateAgent(selectedAgent.agentUid, (agent) => ({
      ...agent,
      tips: agent.tips.filter((item) => item.id !== tipId)
    }));
    message.success(tr("toast.tipDeleted"));
  }

  async function updateTip(selectedAgent: ManagedAgent | null, payload: { tipId: string; title: string; content: string }) {
    if (!selectedAgent) return;
    const title = payload.title.trim();
    const content = payload.content.trim();
    if (!title || !content) {
      message.warning(tr("errors.fillTipTitleAndContent"));
      return;
    }
    const updated = await conversationApi.updateAgentTip(selectedAgent.agentUid, payload.tipId, {
      title,
      sourceContent: content
    });
    const tip = mapApiTip(updated);
    updateAgent(selectedAgent.agentUid, (agent) => ({
      ...agent,
      tips: agent.tips.map((item) => (item.id === payload.tipId ? tip : item))
    }));
    message.success(tr("toast.tipUpdated"));
  }

  function docContentOf(selectedAgent: ManagedAgent | null, key: DocKey) {
    if (selectedAgent) {
      return docsForm[key];
    }
    return "";
  }

  function updateDocContent(key: DocKey, value: string) {
    docsForm[key] = value;
  }

  function docEnabledOf(selectedAgent: ManagedAgent | null, key: DocKey) {
    return selectedAgent?.docStates?.[key]?.enabled ?? true;
  }

  function docUpdatedAtOf(selectedAgent: ManagedAgent | null, key: DocKey) {
    return selectedAgent?.docStates?.[key]?.updatedAt || "";
  }

  function setDocEnabled(selectedAgent: ManagedAgent | null, key: DocKey, enabled: boolean) {
    if (!selectedAgent) return;
    updateAgent(selectedAgent.agentUid, (agent) => ({
      ...agent,
      docStates: {
        ...agent.docStates,
        [key]: {
          ...agent.docStates[key],
          enabled
        }
      }
    }));
  }

  async function saveDocs(selectedAgent: ManagedAgent | null, selectedDocKey: DocKey) {
    if (!selectedAgent) return;
    const updated = await conversationApi.updateAgentDoc(selectedAgent.agentUid, selectedDocKey, {
      content: docsForm[selectedDocKey] || ""
    });
    const now = updated.updatedTime || new Date().toISOString();
    updateAgent(selectedAgent.agentUid, (agent) => ({
      ...agent,
      docs: {
        ...agent.docs,
        [selectedDocKey]: updated.content || ""
      },
      docStates: {
        ...agent.docStates,
        [selectedDocKey]: {
          ...agent.docStates[selectedDocKey],
          updatedAt: now
        }
      }
    }));
    docsForm[selectedDocKey] = updated.content || "";
    message.success(tr("toast.docSaved"));
  }

  function mapDocsPayload(items: AgentDocFile[]) {
    const next: Partial<AgentDocConfig> = {};
    for (const item of items || []) {
      const key = (item.key || "").trim().toLowerCase() as DocKey;
      if (key === "soul" || key === "agent" || key === "memory" || key === "tools" || key === "identity" || key === "user") {
        next[key] = item.content || "";
      }
    }
    return next;
  }

  function mergeDocUpdatedTime(current: ManagedAgent["docStates"], items: AgentDocFile[]) {
    const next = { ...current };
    for (const item of items || []) {
      const key = (item.key || "").trim().toLowerCase() as DocKey;
      if (key === "soul" || key === "agent" || key === "memory" || key === "tools" || key === "identity" || key === "user") {
        next[key] = {
          enabled: current[key]?.enabled ?? true,
          updatedAt: item.updatedTime || current[key]?.updatedAt || new Date().toISOString()
        };
      }
    }
    return next;
  }

  async function saveBasicInfo(selectedAgent: ManagedAgent | null) {
    if (!selectedAgent) return;
    const displayName = basicForm.displayName.trim();
    if (!displayName) {
      message.warning(tr("errors.fillDisplayName"));
      return;
    }
    const fallbackModel = resolveFallbackModelSelection(modelConfig.value);
    const modelProvider = selectedAgent.modelProvider || fallbackModel.modelProvider;
    const modelName = selectedAgent.modelName || selectedAgent.modelNames?.[0] || fallbackModel.modelName;
    const modelNames = selectedAgent.modelNames?.length ? [...selectedAgent.modelNames] : (modelName ? [modelName] : fallbackModel.modelNames);
    if (!modelProvider || !modelName || !modelNames.length) {
      message.warning(tr("errors.noModelForAgent"));
      return;
    }
    const payload = {
      displayName,
      description: basicForm.description.trim(),
      avatar: normalizeAvatarIcon(basicForm.avatar, domainOptions.value),
      avatarColor: normalizeAvatarColor(basicForm.avatarColor, domainOptions.value),
      modelProvider,
      modelName,
      modelNames
    };
    const updated = await conversationApi.updateAgentBasicInfo(selectedAgent.agentUid, payload);
    updateAgent(selectedAgent.agentUid, (agent) => ({
      ...agent,
      displayName: updated.displayName || agent.displayName,
      description: updated.description || "",
      avatar: normalizeAvatarIcon(updated.avatar || payload.avatar, domainOptions.value),
      avatarColor: normalizeAvatarColor(updated.avatarColor || payload.avatarColor, domainOptions.value),
      modelProvider: updated.modelProvider || payload.modelProvider,
      modelName: updated.modelName || payload.modelName,
      modelNames: updated.modelNames && updated.modelNames.length ? updated.modelNames : payload.modelNames
    }));
    await agentCatalogStore.loadCatalog();
    message.success(tr("toast.basicSaved"));
  }

  async function handleSkillImported(selectedAgent: ManagedAgent | null, skill: ImportedSkillResponse) {
    if (!selectedAgent) return;
    await loadAgentWorkspace(selectedAgent.agentUid);
    message.success(tr("toast.skillImported", { name: skill.displayName || skill.skillKey }));
  }

  return {
    agents,
    modelConfig,
    nomoclawRootDir,
    agentsRootDir,
    skillsRootDir,
    createForm,
    tipForm,
    docsForm,
    basicForm,
    init,
    resetCreateForm,
    ensureSelectedAgent,
    syncDocsFormFromSelection,
    syncBasicFormFromSelection,
    reloadAgentsFromCatalog,
    loadAgentWorkspace,
    saveAgent,
    removeAgent,
    setSkillEnabled,
    setToolEnabled,
    addTip,
    updateTip,
    removeTip,
    docContentOf,
    updateDocContent,
    docEnabledOf,
    docUpdatedAtOf,
    setDocEnabled,
    saveDocs,
    saveBasicInfo,
    handleSkillImported
  };
}
