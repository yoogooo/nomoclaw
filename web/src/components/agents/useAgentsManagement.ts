import { computed, reactive, ref } from "vue";
import { conversationApi } from "@/api/conversationApi";
import { modelApi } from "@/api/modelApi";
import { dialog, message } from "@/discrete";
import { useAgentCatalogStore } from "@/stores/agentCatalog";
import type {
  AgentDocConfig,
  AvatarIconOption,
  BasicFormModel,
  DocKey,
  ManagedAgent
} from "@/components/agents/agentManagementTypes";
import type { ImportedSkillResponse, ModelConfig } from "@/types/api";
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
    identity: ""
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
      return;
    }
    docsForm.soul = selectedAgent.docs.soul;
    docsForm.agent = selectedAgent.docs.agent;
    docsForm.memory = selectedAgent.docs.memory;
    docsForm.tools = selectedAgent.docs.tools;
    docsForm.identity = selectedAgent.docs.identity;
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
      message.error("系统路径配置加载失败，请检查后端 /api/system/config。");
      throw new Error("system config load failed");
    }
    try {
      modelConfig.value = await modelApi.getModelConfig();
    } catch {
      message.error("模型配置加载失败，请检查后端 /api/system/models。");
      throw new Error("model config load failed");
    }
    await reloadAgentsFromCatalog();
  }

  async function loadAgentWorkspace(agentUid: string) {
    if (!agentUid) return;
    const isPersisted = agentCatalogStore.allAgents.some((item) => item.agentUid === agentUid);
    if (!isPersisted) return;
    const requestSeq = ++workspaceLoadSeq;
    const [skills, tools, tips] = await Promise.all([
      conversationApi.listAgentSkills(agentUid),
      conversationApi.listAgentTools(agentUid),
      conversationApi.listAgentTips(agentUid)
    ]);
    if (requestSeq !== workspaceLoadSeq) return;
    updateAgent(agentUid, (agent) => ({
      ...agent,
      managedSkills: skills.map((item) => mapApiSkill(item, domainOptions.value)),
      managedTools: tools.map(mapApiTool),
      tips: tips.map(mapApiTip)
    }));
  }

  async function saveAgent() {
    const displayName = createForm.displayName.trim();
    const agentName = createForm.agentName.trim();
    if (!displayName) {
      message.warning("请填写 Agent 名称");
      return "";
    }
    if (!agentName) {
      message.warning("请填写 Agent 标识");
      return "";
    }
    const fallbackModel = resolveFallbackModelSelection(modelConfig.value);
    if (!fallbackModel.modelProvider || !fallbackModel.modelName) {
      message.warning("系统中还没有可用模型，暂时无法创建 Agent");
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
    message.success("Agent 已创建");
    return created.agentUid;
  }

  function removeAgent(agent: ManagedAgent, onDeleted?: () => void) {
    dialog.warning({
      title: "删除 Agent",
      content: `确认删除 “${agent.displayName || agent.agentName}” 吗？`,
      positiveText: "删除",
      negativeText: "取消",
      onPositiveClick: () => {
        agents.value = agents.value.filter((item) => item.agentUid !== agent.agentUid);
        onDeleted?.();
        message.success("Agent 已删除");
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
      message.warning("请填写锦囊标题和内容");
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
    message.success("锦囊已保存到数据库");
  }

  async function removeTip(selectedAgent: ManagedAgent | null, tipId: string) {
    if (!selectedAgent) return;
    await conversationApi.deleteAgentTip(selectedAgent.agentUid, tipId);
    updateAgent(selectedAgent.agentUid, (agent) => ({
      ...agent,
      tips: agent.tips.filter((item) => item.id !== tipId)
    }));
    message.success("锦囊已删除");
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

  function saveDocs(selectedAgent: ManagedAgent | null, selectedDocKey: DocKey) {
    if (!selectedAgent) return;
    const now = new Date().toISOString();
    updateAgent(selectedAgent.agentUid, (agent) => ({
      ...agent,
      docs: {
        soul: docsForm.soul,
        agent: docsForm.agent,
        memory: docsForm.memory,
        tools: docsForm.tools,
        identity: docsForm.identity
      },
      docStates: {
        ...agent.docStates,
        [selectedDocKey]: {
          ...agent.docStates[selectedDocKey],
          updatedAt: now
        }
      }
    }));
    message.success("文档配置已保存");
  }

  async function saveBasicInfo(selectedAgent: ManagedAgent | null) {
    if (!selectedAgent) return;
    const displayName = basicForm.displayName.trim();
    if (!displayName) {
      message.warning("请填写显示名称");
      return;
    }
    const fallbackModel = resolveFallbackModelSelection(modelConfig.value);
    const modelProvider = selectedAgent.modelProvider || fallbackModel.modelProvider;
    const modelName = selectedAgent.modelName || selectedAgent.modelNames?.[0] || fallbackModel.modelName;
    const modelNames = selectedAgent.modelNames?.length ? [...selectedAgent.modelNames] : (modelName ? [modelName] : fallbackModel.modelNames);
    if (!modelProvider || !modelName || !modelNames.length) {
      message.warning("当前 Agent 缺少可用模型，请先在系统模型管理中配置模型");
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
    message.success("基本信息已保存到数据库");
  }

  async function handleSkillImported(selectedAgent: ManagedAgent | null, skill: ImportedSkillResponse) {
    if (!selectedAgent) return;
    await loadAgentWorkspace(selectedAgent.agentUid);
    message.success(`Skill 已导入：${skill.displayName || skill.skillKey}`);
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
