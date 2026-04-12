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
import { tr } from "@/i18n";

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
  const resolvedName = displayName || tr("agents.docsTemplate.defaultAgentName");
  const resolvedRole = tr("agents.docsTemplate.defaultRole");
  return {
    soul: `# SOUL\n\n${tr("agents.docsTemplate.soul", { name: resolvedName })}`,
    agent: `# AGENT\n\n## ${tr("agents.docsTemplate.goalTitle")}\n- ${tr("agents.docsTemplate.goalItem")}\n\n## ${tr("agents.docsTemplate.outputRulesTitle")}\n- ${tr("agents.docsTemplate.outputRulesItem")}`,
    memory: `# MEMORY\n\n- ${tr("agents.docsTemplate.memoryItem1")}\n- ${tr("agents.docsTemplate.memoryItem2")}`,
    tools: `# TOOLS\n\n- ${tr("agents.docsTemplate.toolsItem1")}\n- ${tr("agents.docsTemplate.toolsItem2")}`,
    identity: `# IDENTITY\n\nname: ${resolvedName}\nrole: ${resolvedRole}`,
    user: `# USER\n\n- ${tr("agents.docsTemplate.userItem1")}`
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
  if (!raw) return tr("agents.time.justNow");
  const ts = new Date(raw).getTime();
  if (!Number.isFinite(ts)) return tr("agents.time.justNow");
  const diff = Math.max(0, Date.now() - ts);
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;
  const month = 30 * day;
  const year = 365 * day;
  if (diff < minute) return tr("agents.time.justNow");
  if (diff < hour) return tr("agents.time.minutesAgo", { count: Math.floor(diff / minute) });
  if (diff < day) return tr("agents.time.hoursAgo", { count: Math.floor(diff / hour) });
  if (diff < month) return tr("agents.time.daysAgo", { count: Math.floor(diff / day) });
  if (diff < year) return tr("agents.time.monthsAgo", { count: Math.floor(diff / month) });
  return tr("agents.time.yearsAgo", { count: Math.floor(diff / year) });
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
      description: tr("agents.common.noDescription"),
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
    description: skill.description || tr("agents.common.noDescription"),
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
    description: tool.description || tr("agents.common.noDescription"),
    enabled: tool.enabled
  };
}

export function mapApiTip(tip: ApiAgentTip): AgentTip {
  return {
    id: tip.tipUid,
    title: tip.title || tr("agents.tips.untitled"),
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
