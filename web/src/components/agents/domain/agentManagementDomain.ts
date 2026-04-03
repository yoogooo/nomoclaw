import type {
  AgentCatalogAgent,
  AgentSkill,
  AgentTip as ApiAgentTip,
  AgentTool,
  ModelConfig
} from "@/types/api";
import type {
  AgentDocConfig,
  AgentTip,
  DocKey,
  DocState,
  ManagedAgent,
  ManagedSkill,
  ManagedTool
} from "@/components/agents/agentManagementTypes";

export interface AgentDomainOptions {
  nomoclawRootDir: string;
  skillsRootDir: string;
  avatarIconKeys: string[];
  avatarColorOptions: string[];
  defaultAvatarIcon: string;
  defaultAvatarColor: string;
}

export function joinPath(base: string, ...parts: string[]) {
  const normalizedBase = (base || "").replace(/[\\/]+$/, "");
  const normalizedParts = parts
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => item.replace(/^[\\/]+/, "").replace(/[\\/]+/g, "/"));
  if (!normalizedBase) return normalizedParts.join("/");
  if (!normalizedParts.length) return normalizedBase;
  return `${normalizedBase}/${normalizedParts.join("/")}`;
}

export function normalizePath(path: string, nomoclawRootDir: string) {
  const trimmed = path.trim();
  if (!trimmed) return "";
  if (trimmed.startsWith("/") || /^[a-zA-Z]:[\\/]/.test(trimmed)) return trimmed;
  return joinPath(nomoclawRootDir, trimmed);
}

export function defaultDocs(displayName: string): AgentDocConfig {
  return {
    soul: `# SOUL\n\n你是 ${displayName || "该 Agent"} 的内核人格，保持清晰、稳健、可执行。`,
    agent: "# AGENT\n\n## 目标\n- 在当前职责范围内完成任务\n\n## 输出约束\n- 先结论，后细节",
    memory: "# MEMORY\n\n- 记录长期偏好\n- 记录高价值上下文",
    tools: "# TOOLS\n\n- 列出允许调用的工具\n- 列出工具风险边界",
    identity: `# IDENTITY\n\nname: ${displayName || "agent"}\nrole: 成员`,
    user: "# USER\n\n- 记录该 Agent 服务对象的偏好、约束与上下文。"
  };
}

export function defaultDocStates() {
  const now = new Date().toISOString();
  return {
    soul: { enabled: true, updatedAt: now },
    agent: { enabled: true, updatedAt: now },
    memory: { enabled: true, updatedAt: now },
    tools: { enabled: true, updatedAt: now },
    identity: { enabled: true, updatedAt: now },
    user: { enabled: true, updatedAt: now }
  } satisfies Record<DocKey, DocState>;
}

export function approxBytes(content: string) {
  return new TextEncoder().encode(content || "").length;
}

export function formatRelativeTime(raw: string) {
  if (!raw) return "刚刚";
  const ts = new Date(raw).getTime();
  if (!Number.isFinite(ts)) return "刚刚";
  const diff = Math.max(0, Date.now() - ts);
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;
  const month = 30 * day;
  const year = 365 * day;
  if (diff < minute) return "刚刚";
  if (diff < hour) return `${Math.floor(diff / minute)}分钟前`;
  if (diff < day) return `${Math.floor(diff / hour)}小时前`;
  if (diff < month) return `${Math.floor(diff / day)}天前`;
  if (diff < year) return `${Math.floor(diff / month)}个月前`;
  return `${Math.floor(diff / year)}年前`;
}

export function normalizeAvatarIcon(raw: unknown, options: AgentDomainOptions) {
  const value = typeof raw === "string" ? raw.trim().toLowerCase() : "";
  if (options.avatarIconKeys.includes(value)) {
    return value;
  }
  return options.defaultAvatarIcon;
}

export function normalizeAvatarColor(raw: unknown, options: AgentDomainOptions) {
  const value = typeof raw === "string" ? raw.trim() : "";
  if (options.avatarColorOptions.includes(value)) {
    return value;
  }
  return options.defaultAvatarColor;
}

export function toManagedAgent(agent: AgentCatalogAgent, agentGroupUid: string, options: AgentDomainOptions): ManagedAgent {
  const docs = defaultDocs(agent.displayName || agent.agentName);
  return {
    ...agent,
    agentGroupUid,
    avatar: normalizeAvatarIcon(agent.avatar, options),
    avatarColor: normalizeAvatarColor(agent.avatarColor, options),
    managedSkills: (agent.capabilityTags || []).map((skillKey) => ({
      id: `skill_${skillKey}`,
      skillKey,
      name: skillKey,
      description: "暂无描述",
      path: joinPath(options.skillsRootDir, skillKey),
      enabled: true
    })),
    managedTools: [],
    tips: [],
    docs,
    docStates: defaultDocStates()
  };
}

export function mapApiSkill(skill: AgentSkill, options: AgentDomainOptions): ManagedSkill {
  const key = skill.skillKey || skill.displayName;
  return {
    id: `skill_${key}`,
    skillKey: key,
    name: skill.displayName || key,
    description: skill.description || "暂无描述",
    path: skill.skillPath ? normalizePath(skill.skillPath, options.nomoclawRootDir) : joinPath(options.skillsRootDir, key),
    enabled: skill.enabled
  };
}

export function mapApiTool(tool: AgentTool): ManagedTool {
  const key = tool.toolKey || tool.displayName;
  return {
    id: `tool_${key}`,
    toolKey: key,
    name: tool.displayName || key,
    description: tool.description || "暂无描述",
    enabled: tool.enabled
  };
}

export function mapApiTip(tip: ApiAgentTip): AgentTip {
  return {
    id: tip.tipUid,
    title: tip.title || "未命名锦囊",
    content: tip.sourceContent || tip.summary || ""
  };
}

export function resolveFallbackModelSelection(modelConfig: ModelConfig) {
  const firstProvider = modelConfig.providers.find((provider) => provider.models.length);
  const firstModel = firstProvider?.defaultModel || firstProvider?.models[0]?.id || "";
  return {
    modelProvider: firstProvider?.id || "",
    modelName: firstModel,
    modelNames: firstModel ? [firstModel] : []
  };
}
